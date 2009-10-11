/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.configurations;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import org.jetbrains.annotations.NotNull;

public interface JavaClasspathPolicyExtender {
  ExtensionPointName<JavaClasspathPolicyExtender> EP_NAME = ExtensionPointName.create("com.intellij.javaClasspathPolicyExtender");

  @NotNull
  ProjectRootsTraversing.RootTraversePolicy extend(Project project, @NotNull ProjectRootsTraversing.RootTraversePolicy policy);

  @NotNull
  ProjectRootsTraversing.RootTraversePolicy extend(Module module, @NotNull ProjectRootsTraversing.RootTraversePolicy policy);
}
