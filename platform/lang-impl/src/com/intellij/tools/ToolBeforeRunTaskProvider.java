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
package com.intellij.tools;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;

public class ToolBeforeRunTaskProvider extends BeforeRunTaskProvider<ToolBeforeRunTask> {
  private static final Icon ICON = AllIcons.General.ExternalToolsSmall;
  static final Key<ToolBeforeRunTask> ID = Key.create("ToolBeforeRunTask");
  private static final Logger LOG = Logger.getInstance("#" + ToolBeforeRunTaskProvider.class.getName());


  @Override
  public Key<ToolBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return ToolsBundle.message("tools.before.run.provider.name");
  }

  @Override
  public String getDescription(ToolBeforeRunTask task) {
    final String actionId = task.getToolActionId();
    if (actionId == null) {
      LOG.error("Null id");
      return ToolsBundle.message("tools.unknown.external.tool");
    }
    Tool tool = task.findCorrespondingTool();
    if (tool == null) {
      return ToolsBundle.message("tools.unknown.external.tool");
    }
    String groupName = tool.getGroup();
    return ToolsBundle
      .message("tools.before.run.description", StringUtil.isEmpty(groupName) ? tool.getName() : groupName + "/" + tool.getName());
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  public ToolBeforeRunTask createTask(RunConfiguration runConfiguration) {
    return new ToolBeforeRunTask();
  }

  @Override
  public boolean configureTask(RunConfiguration runConfiguration, ToolBeforeRunTask task) {
    final ToolSelectDialog dialog = new ToolSelectDialog(runConfiguration.getProject(), task.getToolActionId());
    dialog.show();
    if (!dialog.isOK()) {
      return false;
    }
    boolean isModified = dialog.isModified();
    Tool selectedTool = dialog.getSelectedTool();
    LOG.assertTrue(selectedTool != null);
    String selectedToolId = selectedTool.getActionId();
    String oldToolId = task.getToolActionId();
    if (oldToolId != null && oldToolId.equals(selectedToolId)) {
      return isModified;
    }
    task.setToolActionId(selectedToolId);
    return true;
  }

  @Override
  public boolean canExecuteTask(RunConfiguration configuration, ToolBeforeRunTask task) {
    return task.isExecutable();
  }

  @Override
  public boolean executeTask(DataContext context, RunConfiguration configuration, ExecutionEnvironment env, ToolBeforeRunTask task) {
    if (!task.isExecutable()) {
      return false;
    }
    task.execute(context);
    return true;
  }
}
