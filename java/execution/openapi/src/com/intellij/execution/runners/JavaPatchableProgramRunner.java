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
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.util.JDOMExternalizable;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public abstract class JavaPatchableProgramRunner<Settings extends JDOMExternalizable> extends GenericProgramRunner<Settings> {

  public abstract void patch(JavaParameters javaParameters, RunnerSettings settings, final boolean beforeExecution) throws ExecutionException;


  protected static void runCustomPatchers(JavaParameters javaParameters, @Nullable RunnerSettings settings, Executor executor) {
    if (settings != null) {
      final RunProfile profile = settings.getRunProfile();
      for (JavaProgramPatcher patcher : JavaProgramPatcher.EP_NAME.getExtensions()) {
        patcher.patchJavaParameters(executor, profile, javaParameters);
      }
    }
  }
}
