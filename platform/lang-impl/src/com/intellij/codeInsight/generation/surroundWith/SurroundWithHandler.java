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

package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.SurroundWithTemplateHandler;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.WrapWithCustomTemplateAction;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageSurrounders;
import com.intellij.lang.folding.CustomFoldingSurroundDescriptor;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SurroundWithHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler");
  private static final String CHOOSER_TITLE = CodeInsightBundle.message("surround.with.chooser.title");

  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    invoke(project, editor, file, null);
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static void invoke(final Project project, final Editor editor, PsiFile file, Surrounder surrounder) {
    List<AnAction> applicable = buildSurroundActions(project, editor, file, surrounder);
    if (applicable != null) {
      showPopup(editor, applicable);
    }
    else if (!ApplicationManager.getApplication().isUnitTestMode()) {
      HintManager.getInstance().showErrorHint(editor, "Couldn't find Surround With variants applicable to the current context");
    }
  }

  @Nullable
  public static List<AnAction> buildSurroundActions(final Project project, final Editor editor, PsiFile file, @Nullable Surrounder surrounder){
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);

    if (element1 == null || element2 == null) return null;

    TextRange textRange = new TextRange(startOffset, endOffset);
    for(SurroundWithRangeAdjuster adjuster: Extensions.getExtensions(SurroundWithRangeAdjuster.EP_NAME)) {
      textRange = adjuster.adjustSurroundWithRange(file, textRange);
      if (textRange == null) return null;
    }
    startOffset = textRange.getStartOffset();
    endOffset = textRange.getEndOffset();
    element1 = file.findElementAt(startOffset);

    final Language baseLanguage = file.getViewProvider().getBaseLanguage();
    final Language l = element1.getParent().getLanguage();
    List<SurroundDescriptor> surroundDescriptors = new ArrayList<SurroundDescriptor>();

    surroundDescriptors.addAll(LanguageSurrounders.INSTANCE.allForLanguage(l));
    if (l != baseLanguage) surroundDescriptors.addAll(LanguageSurrounders.INSTANCE.allForLanguage(baseLanguage));
    surroundDescriptors.add(CustomFoldingSurroundDescriptor.INSTANCE);

    int exclusiveCount = 0;
    List<SurroundDescriptor> exclusiveSurroundDescriptors = new ArrayList<SurroundDescriptor>();
    for (SurroundDescriptor sd : surroundDescriptors) {
      if (sd.isExclusive()) {
        exclusiveCount++;
        exclusiveSurroundDescriptors.add(sd);
      }
    }

    if (exclusiveCount > 0) {
      surroundDescriptors = exclusiveSurroundDescriptors;
    }

    if (surrounder != null) {
      invokeSurrounderInTests(project, editor, file, surrounder, startOffset, endOffset, surroundDescriptors);
      return null;
    }

    Map<Surrounder, PsiElement[]> surrounders = ContainerUtil.newLinkedHashMap();
    for (SurroundDescriptor descriptor : surroundDescriptors) {
      final PsiElement[] elements = descriptor.getElementsToSurround(file, startOffset, endOffset);
      if (elements.length > 0) {
        for (PsiElement element : elements) {
          assert element != null : "descriptor " + descriptor + " returned null element";
          assert element.isValid() : descriptor;
        }
        for (Surrounder s: descriptor.getSurrounders()) {
          surrounders.put(s, elements);
        }
      }
    }
    return doBuildSurroundActions(project, editor, file, surrounders);
  }

  private static void invokeSurrounderInTests(Project project,
                                              Editor editor,
                                              PsiFile file,
                                              Surrounder surrounder,
                                              int startOffset,
                                              int endOffset, List<SurroundDescriptor> surroundDescriptors) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    for (SurroundDescriptor descriptor : surroundDescriptors) {
      final PsiElement[] elements = descriptor.getElementsToSurround(file, startOffset, endOffset);
      if (elements.length > 0) {
        for (Surrounder descriptorSurrounder : descriptor.getSurrounders()) {
          if (surrounder.getClass().equals(descriptorSurrounder.getClass())) {
            doSurround(project, editor, surrounder, elements);
            return;
          }
        }
      }
    }
  }

  private static void showPopup(Editor editor, List<AnAction> applicable) {
    DataContext context = DataManager.getInstance().getDataContext(editor.getContentComponent());
    JBPopupFactory.ActionSelectionAid mnemonics = JBPopupFactory.ActionSelectionAid.MNEMONICS;
    DefaultActionGroup group = new DefaultActionGroup(applicable.toArray(new AnAction[applicable.size()]));
    JBPopupFactory.getInstance().createActionGroupPopup(CHOOSER_TITLE, group, context, mnemonics, true).showInBestPositionFor(editor);
  }

  static void doSurround(final Project project, final Editor editor, final Surrounder surrounder, final PsiElement[] elements) {
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
      return;
    }

    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      int col = editor.getCaretModel().getLogicalPosition().column;
      int line = editor.getCaretModel().getLogicalPosition().line;
      LogicalPosition pos = new LogicalPosition(0, 0);
      editor.getCaretModel().moveToLogicalPosition(pos);
      TextRange range = surrounder.surroundElements(project, editor, elements);
      if (TemplateManager.getInstance(project).getActiveTemplate(editor) == null) {
        LogicalPosition pos1 = new LogicalPosition(line, col);
        editor.getCaretModel().moveToLogicalPosition(pos1);
      }
      if (range != null) {
        int offset = range.getStartOffset();
        editor.getCaretModel().moveToOffset(offset);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static List<AnAction> doBuildSurroundActions(Project project,
                                                     Editor editor,
                                                     PsiFile file,
                                                     Map<Surrounder, PsiElement[]> surrounders) {
    final List<AnAction> applicable = new ArrayList<AnAction>();
    boolean hasEnabledSurrounders = false;

    Set<Character> usedMnemonicsSet = new HashSet<Character>();

    int index = 0;
    for (Map.Entry<Surrounder, PsiElement[]> entry : surrounders.entrySet()) {
      Surrounder surrounder = entry.getKey();
      PsiElement[] elements = entry.getValue();
      if (surrounder.isApplicable(elements)) {
        char mnemonic;
        if (index < 9) {
          mnemonic = (char)('0' + index + 1);
        }
        else if (index == 9) {
          mnemonic = '0';
        }
        else {
          mnemonic = (char)('A' + index - 10);
        }
        index++;
        usedMnemonicsSet.add(Character.toUpperCase(mnemonic));
        applicable.add(new InvokeSurrounderAction(surrounder, project, editor, elements, mnemonic));
        hasEnabledSurrounders = true;
      }
    }

    List<CustomLiveTemplate> customTemplates = SurroundWithTemplateHandler.getApplicableCustomTemplates(editor, file);
    List<TemplateImpl> templates = SurroundWithTemplateHandler.getApplicableTemplates(editor, file, true);

    if (!templates.isEmpty() || !customTemplates.isEmpty()) {
      applicable.add(new Separator("Live templates"));
    }

    for (TemplateImpl template : templates) {
      applicable.add(new InvokeTemplateAction(template, editor, project, usedMnemonicsSet));
      hasEnabledSurrounders = true;
    }

    for (CustomLiveTemplate customTemplate : customTemplates) {
      applicable.add(new WrapWithCustomTemplateAction(customTemplate, editor, file, usedMnemonicsSet));
      hasEnabledSurrounders = true;
    }

    if (!templates.isEmpty() || !customTemplates.isEmpty()) {
      applicable.add(Separator.getInstance());
      applicable.add(new ConfigureTemplatesAction());
    }
    return hasEnabledSurrounders ? applicable : null;
  }

  private static class InvokeSurrounderAction extends AnAction {
    private final Surrounder mySurrounder;
    private final Project myProject;
    private final Editor myEditor;
    private final PsiElement[] myElements;

    public InvokeSurrounderAction(Surrounder surrounder, Project project, Editor editor, PsiElement[] elements, char mnemonic) {
      super(UIUtil.MNEMONIC + String.valueOf(mnemonic) + ". " + surrounder.getTemplateDescription());
      mySurrounder = surrounder;
      myProject = project;
      myEditor = editor;
      myElements = elements;
    }

    public void actionPerformed(AnActionEvent e) {
      new WriteCommandAction(myProject) {
        @Override
        protected void run(Result result) throws Exception {
          doSurround(myProject, myEditor, mySurrounder, myElements);
        }
      }.execute();
    }
  }

  private static class ConfigureTemplatesAction extends AnAction {
    private ConfigureTemplatesAction() {
      super("Configure Live Templates...");
    }

    public void actionPerformed(AnActionEvent e) {
      ShowSettingsUtil.getInstance().showSettingsDialog(e.getData(PlatformDataKeys.PROJECT), "Live Templates");
    }
  }
}
