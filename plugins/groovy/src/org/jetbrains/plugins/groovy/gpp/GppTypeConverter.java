package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.toplevel.AnnotatedContextFilter;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.psi.GrTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author peter
 */
public class GppTypeConverter extends GrTypeConverter {

  public static final String GROOVY_LANG_TYPED = "groovy.lang.Typed";

  public static boolean hasTypedContext(PsiElement context) {
    if (context == null) {
      return false;
    }

    if (AnnotatedContextFilter.findContextAnnotation(context, GROOVY_LANG_TYPED) != null) {
      return true;
    }

    if (isGppExtension(StringUtil.getShortName(context.getContainingFile().getName()))) {
      return true;
    }

    return false;
  }

  public static boolean isGppExtension(String extension) {
    return "gpp".equals(extension) || "grunit".equals(extension);
  }

  @Override
  public Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull GroovyPsiElement context) {
    if (context instanceof GrListOrMap && context.getReference() instanceof LiteralConstructorReference) return null;

    if (rType instanceof GrTupleType) {
      final GrTupleType tupleType = (GrTupleType)rType;

      final PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(lType, false);
      if (expectedComponent != null && isMethodCallConversion(context)) {
        PsiType tupleComponent = tupleType.getParameters()[0];
        if (tupleComponent != null && TypesUtil.isAssignable(expectedComponent, tupleComponent, context) && hasDefaultConstructor(lType)) {
          return true;
        }
      }

      if (lType instanceof PsiClassType && hasTypedContext(context)) {
        return true;
      }
    }
    else if (rType instanceof GrMapType) {
      final PsiType lKeyType = PsiUtil.substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_MAP, 0, false);
      final PsiType lValueType = PsiUtil.substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_MAP, 1, false);
      final PsiType[] parameters = ((GrMapType)rType).getParameters();
      if (lKeyType != null && lValueType != null &&
          parameters[0] != null && parameters[1] != null &&
          (!TypesUtil.isAssignable(lKeyType, parameters[0], context) || !TypesUtil.isAssignable(lValueType, parameters[1], context))) {
        return null;
      }

      if (hasTypedContext(context)) {
        return true;
      }
    }
    else if (rType instanceof GrClosureType) {
      final PsiType[] methodParameters = GppClosureParameterTypeProvider.findSingleAbstractMethodSignature(lType);
      if (isClosureOverride(methodParameters, (GrClosureType)rType, context)) return true;
    }

    return null;
  }

  public static boolean isClosureOverride(PsiType[] methodParameters, GrClosureType closureType, GroovyPsiElement context) {
    final GrSignature signature = closureType.getSignature();
    if (methodParameters != null && GrClosureSignatureUtil.isSignatureApplicable(signature, methodParameters, context)) {
      return true;
    }
    return false;
  }

  public static boolean hasDefaultConstructor(PsiType type) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass != null && PsiUtil.hasDefaultConstructor(psiClass, true, false);

  }

}
