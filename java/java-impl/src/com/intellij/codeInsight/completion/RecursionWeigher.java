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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.openapi.util.Comparing;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ExcludeSillyAssignment;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author peter
*/
class RecursionWeigher extends LookupElementWeigher {
  private final ElementFilter myFilter;
  private final PsiElement myPosition;
  private final PsiReferenceExpression myReference;
  @Nullable private final PsiMethodCallExpression myExpression;
  private final PsiMethod myPositionMethod;
  private final ExpectedTypeInfo[] myExpectedInfos;
  private final PsiExpression myCallQualifier;
  private final PsiExpression myPositionQualifier;
  private final boolean myDelegate;

  public RecursionWeigher(PsiElement position,
                          @NotNull PsiReferenceExpression reference,
                          PsiMethodCallExpression expression,
                          ExpectedTypeInfo[] expectedInfos) {
    super("recursion");
    myFilter = recursionFilter(position);
    myPosition = position;
    myReference = reference;
    myExpression = expression;
    myPositionMethod = PsiTreeUtil.getParentOfType(position, PsiMethod.class, false);
    myExpectedInfos = expectedInfos;
    myCallQualifier = normalizeQualifier(myReference.getQualifierExpression());
    myPositionQualifier = normalizeQualifier(position.getParent() instanceof PsiJavaCodeReferenceElement
                                             ? ((PsiJavaCodeReferenceElement)position.getParent()).getQualifier()
                                             : null);
    myDelegate = isDelegatingCall();
  }

  @Nullable
  private static PsiExpression normalizeQualifier(PsiElement qualifier) {
    return qualifier instanceof PsiThisExpression || !(qualifier instanceof PsiExpression) ? null : (PsiExpression)qualifier;
  }

  private boolean isDelegatingCall() {
    if (myCallQualifier != null &&
        myPositionQualifier != null &&
        myCallQualifier != myPositionQualifier &&
        CodeInsightUtil.areExpressionsEquivalent(myCallQualifier, myPositionQualifier)) {
      return false;
    }

    if (myCallQualifier == null && myPositionQualifier == null) {
      return false;
    }

    return true;
  }

  @Nullable
  static ElementFilter recursionFilter(PsiElement element) {
    if (PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.RETURN).inside(PsiReturnStatement.class).accepts(element)) {
      return new ExcludeDeclaredFilter(ElementClassFilter.METHOD);
    }

    if (PsiJavaPatterns.psiElement().inside(
        PsiJavaPatterns.or(
            PsiJavaPatterns.psiElement(PsiAssignmentExpression.class),
            PsiJavaPatterns.psiElement(PsiVariable.class))).
        andNot(PsiJavaPatterns.psiElement().afterLeaf(".")).accepts(element)) {
      return new AndFilter(new ExcludeSillyAssignment(),
                                                   new ExcludeDeclaredFilter(new ClassFilter(PsiVariable.class)));
    }
    return null;
  }

  private enum Result {
    delegation,
    normal,
    passingObjectToItself,
    recursive,
  }

  @NotNull
  @Override
  public Result weigh(@NotNull LookupElement element) {
    final Object object = element.getObject();
    if (!(object instanceof PsiMethod || object instanceof PsiVariable || object instanceof PsiExpression)) return Result.normal;

    if (myFilter != null && !myFilter.isAcceptable(object, myPosition)) {
      return Result.recursive;
    }

    if (isPassingObjectToItself(object)) {
      return Result.passingObjectToItself;
    }

    if (myExpression != null) {
      if (myExpectedInfos != null) {
        final PsiType itemType = JavaCompletionUtil.getLookupElementType(element);
        for (final ExpectedTypeInfo expectedInfo : myExpectedInfos) {
          PsiMethod calledMethod = expectedInfo.getCalledMethod();
          if (calledMethod != null && itemType != null) {
            if (calledMethod.equals(myPositionMethod) && expectedInfo.getType().isAssignableFrom(itemType)) {
              return myDelegate ? Result.delegation : Result.recursive;
            }
            if (isGetterSetterAssignment(object, calledMethod)) {
              return myDelegate ? Result.delegation : Result.recursive;
            }
          }
        }
      }
      return Result.normal;
    }

    if (object instanceof PsiMethod && myPositionMethod != null) {
      final PsiMethod method = (PsiMethod)object;
      if (PsiTreeUtil.isAncestor(myReference, myPosition, false) &&
          Comparing.equal(method.getName(), myPositionMethod.getName())) {
        if (!myDelegate && findDeepestSuper(method).equals(findDeepestSuper(myPositionMethod))) {
          return Result.recursive;
        }
        return Result.delegation;
      }
    }

    return Result.normal;
  }

  private static boolean isGetterSetterAssignment(Object lookupObject, PsiMethod calledMethod) {
    if (!PropertyUtil.isSimplePropertySetter(calledMethod)) {
      return false;
    }

    String prop = PropertyUtil.getPropertyName(calledMethod);
    assert prop != null;
    if (lookupObject instanceof PsiField &&
        prop.equals(JavaCodeStyleManager.getInstance(calledMethod.getProject())
                      .variableNameToPropertyName(((PsiField)lookupObject).getName(), VariableKind.FIELD))) {
      return true;
    }
    if (lookupObject instanceof PsiMethod &&
        PropertyUtil.isSimplePropertyGetter((PsiMethod)lookupObject) &&
        prop.equals(PropertyUtil.getPropertyName((PsiMethod)lookupObject))) {
      return true;
    }
    return false;
  }

  private boolean isPassingObjectToItself(Object object) {
    if (object instanceof PsiThisExpression) {
      return !myDelegate || myCallQualifier instanceof PsiSuperExpression;
    }
    return myCallQualifier instanceof PsiReferenceExpression &&
           object.equals(((PsiReferenceExpression)myCallQualifier).advancedResolve(true).getElement());
  }

  @NotNull
  private static PsiMethod findDeepestSuper(@NotNull final PsiMethod method) {
    final PsiMethod first = DeepestSuperMethodsSearch.search(method).findFirst();
    return first == null ? method : first;
  }
}
