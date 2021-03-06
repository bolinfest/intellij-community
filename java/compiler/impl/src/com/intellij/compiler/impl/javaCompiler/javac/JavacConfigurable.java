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
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.compiler.options.ComparingUtils;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public class JavacConfigurable implements Configurable{
  private JPanel myPanel;
  private JCheckBox myCbDebuggingInfo;
  private JCheckBox myCbDeprecation;
  private JCheckBox myCbGenerateNoWarnings;
  private RawCommandLineEditor myAdditionalOptionsField;
  private JTextField myJavacMaximumHeapField;
  private final JavacSettings myJavacSettings;

  public JavacConfigurable(final JavacSettings javacSettings) {
    myJavacSettings = javacSettings;
    myAdditionalOptionsField.setDialogCaption(CompilerBundle.message("java.compiler.option.additional.command.line.parameters"));
  }

  public String getDisplayName() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    boolean isModified = false;
    isModified |= ComparingUtils.isModified(myJavacMaximumHeapField, myJavacSettings.MAXIMUM_HEAP_SIZE);

    isModified |= ComparingUtils.isModified(myCbDeprecation, myJavacSettings.DEPRECATION);
    isModified |= ComparingUtils.isModified(myCbDebuggingInfo, myJavacSettings.DEBUGGING_INFO);
    isModified |= ComparingUtils.isModified(myCbGenerateNoWarnings, myJavacSettings.GENERATE_NO_WARNINGS);
    isModified |= ComparingUtils.isModified(myAdditionalOptionsField, myJavacSettings.ADDITIONAL_OPTIONS_STRING);
    return isModified;
  }

  public void apply() throws ConfigurationException {

    try {
      myJavacSettings.MAXIMUM_HEAP_SIZE = Integer.parseInt(myJavacMaximumHeapField.getText());
      if(myJavacSettings.MAXIMUM_HEAP_SIZE < 1) {
        myJavacSettings.MAXIMUM_HEAP_SIZE = 128;
      }
    }
    catch(NumberFormatException exception) {
      myJavacSettings.MAXIMUM_HEAP_SIZE = 128;
    }

    myJavacSettings.DEPRECATION =  myCbDeprecation.isSelected();
    myJavacSettings.DEBUGGING_INFO = myCbDebuggingInfo.isSelected();
    myJavacSettings.GENERATE_NO_WARNINGS = myCbGenerateNoWarnings.isSelected();
    myJavacSettings.ADDITIONAL_OPTIONS_STRING = myAdditionalOptionsField.getText();
  }

  public void reset() {
    myJavacMaximumHeapField.setText(Integer.toString(myJavacSettings.MAXIMUM_HEAP_SIZE));
    myCbDeprecation.setSelected(myJavacSettings.DEPRECATION);
    myCbDebuggingInfo.setSelected(myJavacSettings.DEBUGGING_INFO);
    myCbGenerateNoWarnings.setSelected(myJavacSettings.GENERATE_NO_WARNINGS);
    myAdditionalOptionsField.setText(myJavacSettings.ADDITIONAL_OPTIONS_STRING);
  }

  public void disposeUIResources() {
  }

}
