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
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class JavaCallHierarchyProvider implements HierarchyProvider {
  public PsiElement getTarget(final DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;

    final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
  }

  public HierarchyBrowser createHierarchyBrowser(final PsiElement target) {
    return new CallHierarchyBrowser(target.getProject(), (PsiMethod) target);
  }

  public void browserActivated(final HierarchyBrowser hierarchyBrowser) {
    ((CallHierarchyBrowser) hierarchyBrowser).changeView(CallHierarchyBrowserBase.CALLER_TYPE);
  }
}
