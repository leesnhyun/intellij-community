/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.ant;

import com.intellij.openapi.module.Module;

/**
 * Ant file generation options. This object is availalbe during construction.
 * @author anna
 */
public abstract class GenerationOptions {
  /** This option specifies whether mulitfile or single file ant script is created. */
  public final boolean generateSingleFile;
  /** This option specifies whehter standard javac or javac2 task is used */
  public final boolean enableFormCompiler;
  /** This option speciries whether files are backed up before generation */
  public final boolean backupPreviouslyGeneratedFiles;
  /** This option specifies whether target JDK is forced during compilation or default ant JDK is used. */
  public final boolean forceTargetJdk;

  /**
   * A constructor
   *
   * @param forceTargetJdk a value of corresponding option
   * @param generateSingleFile  a value of corresponding option
   * @param enableFormCompiler  a value of corresponding option
   * @param backupPreviouslyGeneratedFiles  a value of corresponding option
   */
  public GenerationOptions(boolean forceTargetJdk, boolean generateSingleFile, boolean enableFormCompiler, boolean backupPreviouslyGeneratedFiles) {
    this.forceTargetJdk = forceTargetJdk;
    this.generateSingleFile = generateSingleFile;
    this.enableFormCompiler = enableFormCompiler;
    this.backupPreviouslyGeneratedFiles = backupPreviouslyGeneratedFiles;
  }

  /**
   * Substitute path prefix with macro reference if it matches some macro.
   * @param path a path to update
   * @return an updated path or argument
   */
  public abstract String subsitutePathWithMacros(String path);

  /**
   * Get property reference for the specified url of module output directory
   * @param url an URL to map
   * @return the property reference in the form ${..}
   */
  public abstract String getPropertyRefForUrl(String url);

  /**
   * @return an array of module chunks. an array must not be modified by the clients.
   */
  public abstract ModuleChunk[] getModuleChunks();

  /**
   * Get the chunk that contains the specified module.
   * @param module the module to find
   * @return the chunk that contains specifid module
   */
  public abstract ModuleChunk getChunkByModule(Module module);
}