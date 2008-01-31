/**
 * @author Alexey
 */
package com.intellij.ide.actions;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.*;
import com.intellij.util.LogicalRoot;
import com.intellij.util.LogicalRootsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class CopyReferenceAction extends AnAction {
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    boolean enabled = isEnabled(dataContext);
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean isEnabled(final DataContext dataContext) {
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    PsiElement element = getElementToCopy(editor, dataContext);
    PsiElement member = getMember(element);
    return member != null;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    PsiElement element = getElementToCopy(editor, dataContext);

    PsiElement member = getMember(element);
    if (member == null) return;

    doCopy(member, project);
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    if (editor != null) {
      PsiElement toHighlight = HighlightUsagesHandler.getNameIdentifier(element);
      if (toHighlight == null) toHighlight = element;
      highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{toHighlight}, attributes, true, null);
    }
  }

  @Nullable
  private static PsiElement getElementToCopy(final Editor editor, final DataContext dataContext) {
    PsiElement element = null;
    if (editor != null) {
      PsiReference reference = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());
      if (reference != null) {
        element = reference.getElement();
      }
    }

    if (element == null) {
      element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    if (element != null && !(element instanceof PsiMember) && element.getParent() instanceof PsiMember) {
      element = element.getParent();
    }
    return element;
  }

  @Nullable
  private static PsiElement getMember(final PsiElement element) {
    if (element instanceof PsiMember || element instanceof PsiFile) return element;
    if (element instanceof PsiReference) {
      PsiElement resolved = ((PsiReference)element).resolve();
      if (resolved instanceof PsiMember) return resolved;
    }
    if (!(element instanceof PsiIdentifier)) return null;
    final PsiElement parent = element.getParent();
    PsiMember member = null;
    if (parent instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement)parent).resolve();
      if (resolved instanceof PsiMember) {
        member = (PsiMember)resolved;
      }
    }
    else if (parent instanceof PsiMember) {
      member = (PsiMember)parent;
    }
    else {
      //todo show error
      //return;
    }
    return member;
  }

  public static void doCopy(final PsiElement element, final Project project) {
    String fqn = elementToFqn(element);

    CopyPasteManager.getInstance().setContents(new MyTransferable(fqn));

    final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
    statusBar.setInfo(IdeBundle.message("message.reference.to.fqn.has.been.copied", fqn));
  }

  static final DataFlavor OUR_DATA_FLAVOR;
  static {
    try {
      //noinspection HardCodedStringLiteral
      OUR_DATA_FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + MyTransferable.class.getName());
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private static class MyTransferable implements Transferable {
    private final String fqn;

    public MyTransferable(String fqn) {
      this.fqn = fqn;
    }

    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[]{OUR_DATA_FLAVOR, DataFlavor.stringFlavor};
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return OUR_DATA_FLAVOR.equals(flavor) || DataFlavor.stringFlavor.equals(flavor);
    }

    @Nullable
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      if (!isDataFlavorSupported(flavor)) return null;
      return fqn;
    }
  }

  @Nullable
  private static String elementToFqn(final PsiElement element) {
    final String fqn;
    if (element instanceof PsiClass) {
      fqn = ((PsiClass)element).getQualifiedName();
    }
    else if (element instanceof PsiMember) {
      final PsiMember member = (PsiMember)element;
      fqn = member.getContainingClass().getQualifiedName() + "#" + member.getName();
    }
    else if (element instanceof PsiFile) {
      final PsiFile file = (PsiFile)element;
      fqn = FileUtil.toSystemIndependentName(getFileFqn(file));
    }
    else {
      fqn = element.getClass().getName();
    }
    return fqn;
  }

  @NotNull
  private static String getFileFqn(final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return file.getName();
    }
    final Project project = file.getProject();
    final LogicalRoot logicalRoot = LogicalRootsManager.getLogicalRootsManager(project).findLogicalRoot(virtualFile);
    if (logicalRoot != null) {
      return "/"+FileUtil.getRelativePath(VfsUtil.virtualToIoFile(logicalRoot.getVirtualFile()), VfsUtil.virtualToIoFile(virtualFile));
    }

    final VirtualFile contentRoot = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(virtualFile);
    if (contentRoot != null) {
      return "/"+FileUtil.getRelativePath(VfsUtil.virtualToIoFile(contentRoot), VfsUtil.virtualToIoFile(virtualFile));
    }
    return virtualFile.getPath();
  }
}
