/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.Function;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class DumpGroovyControlFlowAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
    if (editor == null) return;

    final PsiFile psiFile = HandlerUtils.getPsiFile(editor, e.getDataContext());
    if (!(psiFile instanceof GroovyFile)) return;

    int offset = editor.getCaretModel().getOffset();

    final List<GrControlFlowOwner> controlFlowOwners = collectControlFlowOwners(psiFile, editor, offset);
    if (controlFlowOwners.size() == 0) return;
    if (controlFlowOwners.size() == 1) {
      passInner(controlFlowOwners.get(0));
    }
    else {
      IntroduceTargetChooser.showChooser(editor, controlFlowOwners, new Pass<GrControlFlowOwner>() {
                                           @Override
                                           public void pass(GrControlFlowOwner grExpression) {
                                             passInner(grExpression);
                                           }
                                         }, new Function<GrControlFlowOwner, String>() {
        @Override
        public String fun(GrControlFlowOwner flowOwner) {
          return flowOwner.getText();
        }
      }
      );
    }
  }

  private static List<GrControlFlowOwner> collectControlFlowOwners(final PsiFile file, final Editor editor, final int offset) {
    final PsiElement elementAtCaret = file.findElementAt(GrIntroduceHandlerBase.correctOffset(editor, offset));
    final List<GrControlFlowOwner> result = new ArrayList<GrControlFlowOwner>();

    for (GrControlFlowOwner owner = ControlFlowUtils.findControlFlowOwner(elementAtCaret);
         owner != null;
         owner = ControlFlowUtils.findControlFlowOwner(owner)) {
      result.add(owner);
    }
    return result;
  }

  private static void passInner(GrControlFlowOwner owner) {
    System.out.println(owner.getText());
    System.out.println(ControlFlowUtils.dumpControlFlow(owner.getControlFlow()));
  }
}
