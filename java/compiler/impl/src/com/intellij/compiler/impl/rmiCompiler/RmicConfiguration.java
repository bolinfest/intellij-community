/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.compiler.impl.rmiCompiler;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name = "RmicSettings",
  storages = {
    @Storage( file = StoragePathMacros.PROJECT_FILE)
   ,@Storage( file = StoragePathMacros.PROJECT_CONFIG_DIR + "/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class RmicConfiguration implements PersistentStateComponent<RmicSettings> {
  private final RmicSettings mySettings = new RmicSettings();

  public RmicSettings getState() {
    return mySettings;
  }

  public void loadState(RmicSettings state) {
    XmlSerializerUtil.copyBean(state, mySettings);
  }

  public RmicSettings getSettings() {
    return mySettings;
  }

  public static RmicSettings getSettings(Project project) {
    return ServiceManager.getService(project, RmicConfiguration.class).getSettings();
  }
}