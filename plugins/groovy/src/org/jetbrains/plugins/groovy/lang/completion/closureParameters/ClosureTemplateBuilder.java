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
package org.jetbrains.plugins.groovy.lang.completion.closureParameters;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.template.expressions.ParameterNameExpression;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class ClosureTemplateBuilder {
  public static void runTemplate(List<ClosureParameterInfo> parameters, GrClosableBlock block, final Project project, final Editor editor) {
    assert block.getArrow() == null;
    if (parameters.isEmpty()) return;

    StringBuilder buffer = new StringBuilder();
    buffer.append("{");
    for (ClosureParameterInfo parameter : parameters) {
      final String type = parameter.getType();
      final String name = parameter.getName();
      if (type != null) {
        buffer.append(type).append(" ");
      }
      else {
        buffer.append("def ");
      }
      buffer.append(name);
      buffer.append(", ");
    }
    buffer.replace(buffer.length() - 2, buffer.length(), " ->}");


    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    assert file != null;

    final GrClosableBlock closure = GroovyPsiElementFactory.getInstance(project).createClosureFromText(buffer.toString());
    final GrClosableBlock templateClosure = (GrClosableBlock)block.replaceWithExpression(closure, false);

    final TemplateBuilderImpl builder = new TemplateBuilderImpl(templateClosure);

    for (GrParameter p : templateClosure.getParameters()) {
      final GrTypeElement typeElement = p.getTypeElementGroovy();
      final PsiElement nameIdentifier = p.getNameIdentifierGroovy();

      if (typeElement != null) {
        final TypeConstraint[] typeConstraints = {SupertypeConstraint.create(typeElement.getType())};
        final ChooseTypeExpression expression = new ChooseTypeExpression(typeConstraints, PsiManager.getInstance(project));
        builder.replaceElement(typeElement, expression);
      }
      else {
        final ChooseTypeExpression expression = new ChooseTypeExpression(TypeConstraint.EMPTY_ARRAY, PsiManager.getInstance(project));
        builder.replaceElement(p.getModifierList(), expression);
      }

      builder.replaceElement(nameIdentifier, new ParameterNameExpression(nameIdentifier.getText()));
    }

    final GrClosableBlock afterPostprocess = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(templateClosure);
    final Template template = builder.buildTemplate();
    TextRange range = afterPostprocess.getTextRange();
    document.deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateEditingListener templateListener = new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            PsiDocumentManager.getInstance(project).commitDocument(document);
            final CaretModel caretModel = editor.getCaretModel();
            final int offset = caretModel.getOffset();
            GrClosableBlock block = PsiTreeUtil.findElementOfClassAtOffset(file, offset - 1, GrClosableBlock.class, false);
            if (block != null) {
              final PsiElement arrow = block.getArrow();
              if (arrow != null) {
                caretModel.moveToOffset(arrow.getTextRange().getEndOffset());
              }

              // fix space before closure lbrace
              final TextRange range = block.getTextRange();
              CodeStyleManager.getInstance(project).reformatRange(block.getParent(), range.getStartOffset() - 1, range.getEndOffset(), true);
            }
          }
        });
      }
    };

    TemplateManager manager = TemplateManager.getInstance(project);
    manager.startTemplate(editor, template, templateListener);
  }
}
