/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrMultiSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrRecursiveSignatureVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureParameterImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
@SuppressWarnings("unchecked")
public class GrClosureSignatureUtil {
  private GrClosureSignatureUtil() {
  }

  public static GrMultiSignature createMultiSignature(GrClosureSignature[] signatures) {
    return new GrMultiSignatureImpl(signatures);
  }

  @Nullable
  public static GrClosureSignature createSignature(GrCall call) {
    if (call instanceof GrMethodCall) {
      final GrExpression invokedExpression = ((GrMethodCall)call).getInvokedExpression();
      final PsiType type = invokedExpression.getType();
      if (type instanceof GrClosureType) {
        final GrSignature signature = ((GrClosureType)type).getSignature();
        final Trinity<GrClosureSignature, ArgInfo<PsiType>[], ApplicabilityResult> trinity =
          getApplicableSignature(signature, PsiUtil.getArgumentTypes(invokedExpression, true), call);
        if (trinity != null) {
          return trinity.first;
        }
        return null;
      }
    }

    final GroovyResolveResult resolveResult = call.advancedResolve();
    final PsiElement element = resolveResult.getElement();
    if (element instanceof PsiMethod) {
      return createSignature((PsiMethod)element, resolveResult.getSubstitutor());
    }

    return null;
  }

  public static GrClosureSignature createSignature(MethodSignature signature) {
    final PsiType[] types = signature.getParameterTypes();
    GrClosureParameter[] parameters = new GrClosureParameter[types.length];
    ContainerUtil.map(types, new Function<PsiType, GrClosureParameter>() {
      @Override
      public GrClosureParameter fun(PsiType type) {
        return new GrClosureParameterImpl(type, false, null);
      }
    }, parameters);
    return new GrClosureSignatureImpl(parameters, null, false, false);
  }


  public static GrClosureSignature createSignature(final GrClosableBlock block) {
    return new GrClosureSignatureImpl(block.getAllParameters(), null) {
      @Override
      public PsiType getReturnType() {
        return block.getReturnType();
      }

      @Override
      public boolean isValid() {
        return block.isValid();
      }
    };
  }

  public static GrClosureSignature createSignature(final PsiMethod method, PsiSubstitutor substitutor) {
    return new GrClosureSignatureImpl(method.getParameterList().getParameters(), null, substitutor) {
      @Override
      public PsiType getReturnType() {
        return getSubstitutor().substitute(PsiUtil.getSmartReturnType(method));
      }

      @Override
      public boolean isValid() {
        return method.isValid();
      }
    };
  }

  public static GrClosureSignature removeParam(final GrClosureSignature signature, int i) {
    final GrClosureParameter[] newParams = ArrayUtil.remove(signature.getParameters(), i);
    return new GrClosureSignatureImpl(newParams, null, newParams.length > 0 && signature.isVarargs(), false) {
      @Override
      public PsiType getReturnType() {
        return signature.getReturnType();
      }

      @Override
      public boolean isValid() {
        return signature.isValid();
      }
    };
  }

  public static GrClosureSignature createSignatureWithErasedParameterTypes(final PsiMethod method) {
    final PsiParameter[] params = method.getParameterList().getParameters();
    final GrClosureParameter[] closureParams = new GrClosureParameter[params.length];
    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      PsiType type = TypeConversionUtil.erasure(param.getType());
      closureParams[i] = new GrClosureParameterImpl(type, GrClosureParameterImpl.isParameterOptional(param),
                                                    GrClosureParameterImpl.getDefaultInitializer(param));
    }
    return new GrClosureSignatureImpl(closureParams, null, GrClosureParameterImpl.isVararg(closureParams), false) {
      @Override
      public PsiType getReturnType() {
        return PsiUtil.getSmartReturnType(method);
      }

      @Override
      public boolean isValid() {
        return method.isValid();
      }
    };
  }

  public static GrClosureSignature createSignatureWithErasedParameterTypes(final GrClosableBlock closure) {
    final PsiParameter[] params = closure.getParameterList().getParameters();
    final GrClosureParameter[] closureParams = new GrClosureParameter[params.length];
    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      PsiType type = TypeConversionUtil.erasure(param.getType());
      closureParams[i] = new GrClosureParameterImpl(type, GrClosureParameterImpl.isParameterOptional(param),
                                                    GrClosureParameterImpl.getDefaultInitializer(param));
    }
    return new GrClosureSignatureImpl(closureParams, null, GrClosureParameterImpl.isVararg(closureParams), false) {
      @Override
      public PsiType getReturnType() {
        return closure.getReturnType();
      }

      @Override
      public boolean isValid() {
        return closure.isValid();
      }
    };
  }


  public static GrClosureSignature createSignature(PsiParameter[] parameters, @Nullable PsiType returnType) {
    return new GrClosureSignatureImpl(parameters, returnType);
  }


  @Nullable
  public static PsiType getReturnType(@NotNull final GrSignature signature, @NotNull GrMethodCall expr) {
    return getReturnType(signature, PsiUtil.getArgumentTypes(expr.getInvokedExpression(), true), expr);
  }

  @Nullable
  public static PsiType getReturnType(@NotNull final GrSignature signature, @Nullable PsiType[] args, @NotNull GroovyPsiElement context) {
    if (signature instanceof GrClosureSignature) return ((GrClosureSignature)signature).getReturnType();

    if (args == null) {
      return TypesUtil.getLeastUpperBoundNullable(new Iterable<PsiType>() {

        @Override
        public Iterator<PsiType> iterator() {
          return new Iterator<PsiType>() {
            private final Iterator<GrClosureSignature> it = Arrays.asList(((GrMultiSignature)signature).getAllSignatures()).iterator();

            @Override
            public boolean hasNext() {
              return it.hasNext();
            }

            @Override
            public PsiType next() {
              return it.next().getReturnType();
            }

            @Override
            public void remove() {
              throw new UnsupportedOperationException();
            }
          };
        }
      }, context.getManager());
    }

    final List<Trinity<GrClosureSignature, ArgInfo<PsiType>[], ApplicabilityResult>> results =
      getSignatureApplicabilities(signature, args, context);

    if (results.size() == 1) return results.get(0).first.getReturnType();

    return TypesUtil.getLeastUpperBoundNullable(new Iterable<PsiType>() {
      @Override
      public Iterator<PsiType> iterator() {
        return new Iterator<PsiType>() {
          private final Iterator<Trinity<GrClosureSignature, ArgInfo<PsiType>[], ApplicabilityResult>> myIterator = results.iterator();

          @Override
          public boolean hasNext() {
            return myIterator.hasNext();
          }

          @Override
          public PsiType next() {
            return myIterator.next().first.getReturnType();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    }, context.getManager());
  }

  public static boolean isSignatureApplicable(@NotNull GrSignature signature, @NotNull PsiType[] args, @NotNull GroovyPsiElement context) {
    return isSignatureApplicableConcrete(signature, args, context) != ApplicabilityResult.inapplicable;
  }

  public static ApplicabilityResult isSignatureApplicableConcrete(@NotNull GrSignature signature, @NotNull final PsiType[] args, @NotNull final GroovyPsiElement context) {
    final List<Trinity<GrClosureSignature, ArgInfo<PsiType>[], ApplicabilityResult>> results =
      getSignatureApplicabilities(signature, args, context);

    if (results.isEmpty()) return ApplicabilityResult.inapplicable;
    else if (results.size()==1) return results.get(0).third;
    else return ApplicabilityResult.ambiguous;
  }

  @Nullable
  public static Trinity<GrClosureSignature, ArgInfo<PsiType>[], ApplicabilityResult> getApplicableSignature(@NotNull GrSignature signature,
                                                                                                            @Nullable final PsiType[] args,
                                                                                                            @NotNull final GroovyPsiElement context) {
    if (args == null) return null;
    final List<Trinity<GrClosureSignature, ArgInfo<PsiType>[], ApplicabilityResult>> results = getSignatureApplicabilities(signature, args, context);

    if (results.size() == 1) return results.get(0);
    else return null;
  }

  private static List<Trinity<GrClosureSignature, ArgInfo<PsiType>[], ApplicabilityResult>> getSignatureApplicabilities(@NotNull GrSignature signature,
                                                                                                                        @NotNull final PsiType[] args,
                                                                                                                        @NotNull final GroovyPsiElement context) {
    final List<Trinity<GrClosureSignature, ArgInfo<PsiType>[], ApplicabilityResult>> results =
      new ArrayList<Trinity<GrClosureSignature, GrClosureSignatureUtil.ArgInfo<PsiType>[], ApplicabilityResult>>();
    signature.accept(new GrRecursiveSignatureVisitor() {
      @Override
      public void visitClosureSignature(GrClosureSignature signature) {
        ArgInfo<PsiType>[] map = mapArgTypesToParameters(signature, args, context, false);
        if (map != null) {
          results.add(new Trinity<GrClosureSignature, ArgInfo<PsiType>[], ApplicabilityResult>(signature, map, isSignatureApplicableInner(map, signature)));
          return;
        }

        // check for the case foo([1, 2, 3]) if foo(int, int, int)
        if (args.length == 1 && PsiUtil.isInMethodCallContext(context)) {
          final GrClosureParameter[] parameters = signature.getParameters();
          if (parameters.length == 1 && parameters[0].getType() instanceof PsiArrayType) {
            return;
          }
          PsiType arg = args[0];
          if (arg instanceof GrTupleType) {
            PsiType[] _args = ((GrTupleType)arg).getComponentTypes();
            map = mapArgTypesToParameters(signature, _args, context, false);
            if (map != null) {
              results.add(new Trinity<GrClosureSignature, ArgInfo<PsiType>[], ApplicabilityResult>(signature, map,
                                                                                                   isSignatureApplicableInner(map,
                                                                                                                              signature)));
            }
          }
        }
      }
    });
    return results;
  }

  private static ApplicabilityResult isSignatureApplicableInner(@NotNull ArgInfo<PsiType>[] infos, @NotNull GrClosureSignature signature) {
    GrClosureParameter[] parameters = signature.getParameters();
    for (int i = 0; i < infos.length; i++) {
      ArgInfo<PsiType> info = infos[i];
      if (info.args.size() != 1 || info.isMultiArg) continue;
      PsiType type = info.args.get(0);
      if (type != null) continue;

      PsiType pType = parameters[i].getType();
      if (pType != null && !pType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        return ApplicabilityResult.canBeApplicable;
      }
    }
    return ApplicabilityResult.applicable;
  }

  public enum ApplicabilityResult {
    applicable, inapplicable, canBeApplicable, ambiguous;

    public static boolean isApplicable(ApplicabilityResult r) {
      return r != inapplicable && r!= ambiguous;
    }
  }

  @Nullable
  public static ArgInfo<PsiType>[] mapArgTypesToParameters(@NotNull GrClosureSignature signature,
                                                           @NotNull PsiType[] args,
                                                           @NotNull GroovyPsiElement context,
                                                           boolean partial) {
    return mapParametersToArguments(signature, args, FunctionUtil.<PsiType>id(), context, partial);
  }

  private static class ArgWrapper<Arg> {
    PsiType type;
    @Nullable Arg arg;

    private ArgWrapper(PsiType type, Arg arg) {
      this.type = type;
      this.arg = arg;
    }
  }

  private static <Arg> Function<ArgWrapper<Arg>, PsiType> ARG_WRAPPER_COMPUTER() {
    return new Function<ArgWrapper<Arg>, PsiType>() {
      @Override
      public PsiType fun(ArgWrapper<Arg> argWrapper) {
        return argWrapper.type;
      }
    };
  }

  @Nullable
  private static <Arg> ArgInfo<Arg>[] mapParametersToArguments(@NotNull GrClosureSignature signature,
                                                               @NotNull Arg[] args,
                                                               @NotNull Function<Arg, PsiType> typeComputer,
                                                               @NotNull GroovyPsiElement context,
                                                               boolean partial) {
    if (checkForOnlyMapParam(signature, args.length)) return ArgInfo.empty_array();
    GrClosureParameter[] params = signature.getParameters();
    if (args.length > params.length && !signature.isVarargs() && !partial) return null;
    int optional = getOptionalParamCount(signature, false);
    int notOptional = params.length - optional;
    if (signature.isVarargs()) notOptional--;
    if (notOptional > args.length && !partial) return null;

    final ArgInfo<Arg>[] map = mapSimple(params, args, typeComputer, context, false);
    if (map != null) return map;

    if (signature.isVarargs()) {
      return new ParameterMapperForVararg<Arg>(context, params, args, typeComputer).isApplicable();
    }

    if (!partial) return null;

    return mapSimple(params, args, typeComputer, context, true);
  }

  private static boolean checkForOnlyMapParam(@NotNull GrClosureSignature signature, final int argCount) {
    if (argCount > 0 || signature.isCurried()) return false;
    final GrClosureParameter[] parameters = signature.getParameters();
    if (parameters.length != 1) return false;
    final PsiType type = parameters[0].getType();
    return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP);
  }

  @Nullable
  private static <Arg> ArgInfo<Arg>[] mapSimple(@NotNull GrClosureParameter[] params,
                                                @NotNull Arg[] args,
                                                @NotNull Function<Arg, PsiType> typeComputer,
                                                @NotNull GroovyPsiElement context,
                                                boolean partial) {
    if (args.length > params.length && !partial) return null;

    ArgInfo<Arg>[] map = new ArgInfo[params.length];
    int optional = getOptionalParamCount(params, false);
    int notOptional = params.length - optional;
    int optionalArgs = args.length - notOptional;

    if (notOptional > args.length && !partial) return null;

    int cur = 0;
    for (int i = 0; i < args.length; i++, cur++) {
      while (optionalArgs == 0 && cur < params.length && params[cur].isOptional()) {
        cur++;
      }
      if (cur == params.length) return partial ? map : null;
      if (params[cur].isOptional()) optionalArgs--;
      if (!isAssignableByConversion(params[cur].getType(), typeComputer.fun(args[i]), context)) return partial ? map : null;
      map[cur] = new ArgInfo<Arg>(args[i]);
    }
    for (int i = 0; i < map.length; i++) {
      if (map[i] == null) map[i] = new ArgInfo<Arg>(Collections.<Arg>emptyList(), false);
    }
    return map;
  }

  private static boolean isAssignableByConversion(@Nullable PsiType paramType, @Nullable PsiType argType, @NotNull GroovyPsiElement context) {
    if (argType == null) {
      return true;
    }
    return TypesUtil.isAssignableByMethodCallConversion(paramType, argType, context);
  }

  public static void checkAndAddSignature(List<GrClosureSignature> list,
                                          PsiType[] args,
                                          int position,
                                          List<GrClosureParameter> params,
                                          PsiType returnType,
                                          @NotNull GroovyPsiElement context) {
    final int last = position + args.length;
    if (last > params.size()) return;

    for (int i = position; i < last; i++) {
      final GrClosureParameter p = params.get(i);
      final PsiType type = p.getType();
      if (!isAssignableByConversion(type, args[i - position], context)) return;
    }
    GrClosureParameter[] _p = new GrClosureParameter[params.size() - args.length];
    int j = 0;
    for (int i = 0; i < position; i++) {
      _p[j++] = params.get(i);
    }
    for (int i = position + args.length; i < params.size(); i++) {
      _p[j++] = params.get(i);
    }

    list.add(new GrClosureSignatureImpl(_p, returnType, _p.length > 0 && _p[_p.length - 1].getType() instanceof PsiArrayType, true));
  }


  @Nullable
  public static GrClosureSignature createSignature(GroovyResolveResult resolveResult) {
    final PsiElement resolved = resolveResult.getElement();
    if (!(resolved instanceof PsiMethod)) return null;
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    return createSignature((PsiMethod)resolved, substitutor);
  }

  private static class ParameterMapperForVararg<Arg> {
    private final GroovyPsiElement context;
    private final GrClosureParameter[] params;
    private final Arg[] args;
    private final PsiType[] types;
    private final PsiType vararg;
    private final int paramLength;
    private final ArgInfo<Arg>[] map;

    private ParameterMapperForVararg(GroovyPsiElement context,
                                     GrClosureParameter[] params,
                                     Arg[] args,
                                     Function<Arg, PsiType> typeComputer) {
      this.context = context;
      this.params = params;
      this.args = args;
      this.types = new PsiType[args.length];
      for (int i = 0; i < args.length; i++) {
        types[i] = typeComputer.fun(args[i]);
      }
      paramLength = params.length - 1;
      vararg = ((PsiArrayType)params[paramLength].getType()).getComponentType();
      map = new ArgInfo[params.length];
    }

    @Nullable
    private ArgInfo<Arg>[] isApplicable() {
      int notOptionals = 0;
      for (int i = 0; i < paramLength; i++) {
        if (!params[i].isOptional()) notOptionals++;
      }
      if (isApplicableInternal(0, 0, false, notOptionals)) {
        for (int i = 0; i < map.length; i++) {
          if (map[i] == null) map[i] = new ArgInfo<Arg>(false);
        }
        return map;
      }
      else {
        return null;
      }
    }

    private boolean isApplicableInternal(int curParam, int curArg, boolean skipOptionals, int notOptional) {
      int startParam = curParam;
      if (notOptional > args.length - curArg) return false;
      if (notOptional == args.length - curArg) skipOptionals = true;

      while (curArg < args.length) {
        if (skipOptionals) {
          while (curParam < paramLength && params[curParam].isOptional()) curParam++;
        }

        if (curParam == paramLength) break;

        if (params[curParam].isOptional()) {
          if (isAssignableByConversion(params[curParam].getType(), types[curArg], context) &&
              isApplicableInternal(curParam + 1, curArg + 1, false, notOptional)) {
            map[curParam] = new ArgInfo<Arg>(args[curArg]);
            return true;
          }
          skipOptionals = true;
        }
        else {
          if (!isAssignableByConversion(params[curParam].getType(), types[curArg], context)) {
            for (int i = startParam; i < curParam; i++) map[i] = null;
            return false;
          }
          map[curParam] = new ArgInfo<Arg>(args[curArg]);
          notOptional--;
          curArg++;
          curParam++;
        }
      }

      List<Arg> varargs = new ArrayList<Arg>();
      for (; curArg < args.length; curArg++) {
        if (!isAssignableByConversion(vararg, types[curArg], context)) {
          for (int i = startParam; i < curParam; i++) map[i] = null;
          return false;
        }
        varargs.add(args[curArg]);
      }
      map[paramLength] = new ArgInfo<Arg>(varargs, true);
      return true;
    }
  }

  public static int getOptionalParamCount(GrClosureSignature signature, boolean hasNamedArgs) {
    return getOptionalParamCount(signature.getParameters(), hasNamedArgs);
  }

  public static int getOptionalParamCount(GrClosureParameter[] parameters, boolean hasNamedArgs) {
    int count = 0;
    int i = 0;
    if (hasNamedArgs) i++;
    for (; i < parameters.length; i++) {
      GrClosureParameter parameter = parameters[i];
      if (parameter.isOptional()) count++;
    }
    return count;
  }

  public static class ArgInfo<ArgType> {
    public static final ArgInfo[] EMPTY_ARRAY = new ArgInfo[0];

    public List<ArgType> args;
    public final boolean isMultiArg;

    public ArgInfo(List<ArgType> args, boolean multiArg) {
      this.args = args;
      isMultiArg = multiArg;
    }

    public ArgInfo(ArgType arg) {
      this.args = Collections.singletonList(arg);
      this.isMultiArg = false;
    }

    public ArgInfo(boolean isMultiArg) {
      this.args = Collections.emptyList();
      this.isMultiArg = isMultiArg;
    }

    public static <ArgType> ArgInfo<ArgType>[] empty_array() {
      return EMPTY_ARRAY;
    }
  }

  private static class InnerArg {
    List<PsiElement> list;
    PsiType type;

    InnerArg(PsiType type, PsiElement... elements) {
      this.list = new ArrayList<PsiElement>(Arrays.asList(elements));
      this.type = type;
    }
  }

  @Nullable
  public static Map<GrExpression, Pair<PsiParameter, PsiType>> mapArgumentsToParameters(@NotNull GroovyResolveResult resolveResult,
                                                                                        @NotNull GroovyPsiElement context,
                                                                                        final boolean partial,
                                                                                        final boolean eraseArgs,
                                                                                        @NotNull final GrNamedArgument[] namedArgs,
                                                                                        @NotNull final GrExpression[] expressionArgs,
                                                                                        @NotNull GrClosableBlock[] closureArguments) {
    final GrClosureSignature signature;
    final PsiParameter[] parameters;
    final PsiElement element = resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (element instanceof PsiMethod) {
      signature =
        eraseArgs ? createSignatureWithErasedParameterTypes((PsiMethod)element) : createSignature((PsiMethod)element, substitutor);
      parameters = ((PsiMethod)element).getParameterList().getParameters();
    }
    else if (element instanceof GrClosableBlock) {
      signature =
        eraseArgs ? createSignatureWithErasedParameterTypes((GrClosableBlock)element) : createSignature(((GrClosableBlock)element));
      parameters = ((GrClosableBlock)element).getAllParameters();
    }
    else {
      return null;
    }

    final ArgInfo<PsiElement>[] argInfos = mapParametersToArguments(signature, namedArgs, expressionArgs, closureArguments, context, partial, eraseArgs);
    if (argInfos == null) {
      return null;
    }

    final HashMap<GrExpression, Pair<PsiParameter, PsiType>> result = new HashMap<GrExpression, Pair<PsiParameter, PsiType>>();
    for (int i = 0; i < argInfos.length; i++) {
      ArgInfo<PsiElement> info = argInfos[i];
      if (info == null) continue;
      for (PsiElement arg : info.args) {
        if (arg instanceof GrNamedArgument) {
          arg = ((GrNamedArgument)arg).getExpression();
        }
        final GrExpression expression = (GrExpression)arg;
        PsiType type = parameters[i].getType();
        if (info.isMultiArg && type instanceof PsiArrayType) {
          type = ((PsiArrayType)type).getComponentType();
        }
        result.put(expression, Pair.create(parameters[i], substitutor.substitute(type)));
      }
    }

    return result;
  }


  @Nullable
  public static ArgInfo<PsiElement>[] mapParametersToArguments(@NotNull GrClosureSignature signature, @NotNull GrCall call) {
    return mapParametersToArguments(signature, call.getNamedArguments(), call.getExpressionArguments(), call.getClosureArguments(), call,
                                    false, false);
  }

  @Nullable
  public static ArgInfo<PsiElement>[] mapParametersToArguments(@NotNull GrClosureSignature signature,
                                                               @NotNull GrNamedArgument[] namedArgs,
                                                               @NotNull GrExpression[] expressionArgs,
                                                               @NotNull GrClosableBlock[] closureArguments,
                                                               @NotNull GroovyPsiElement context,
                                                               boolean partial, boolean eraseArgs) {
    List<InnerArg> innerArgs = new ArrayList<InnerArg>();

    boolean hasNamedArgs = namedArgs.length > 0;
    GrClosureParameter[] params = signature.getParameters();

    if (hasNamedArgs) {
      if (params.length == 0) return null;
      PsiType type = params[0].getType();
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP) ||
          type == null ||
          type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        innerArgs.add(new InnerArg(new GrMapType(context.getResolveScope()), namedArgs));
      }
      else {
        return null;
      }
    }

    for (GrExpression expression : expressionArgs) {
      PsiType type = expression.getType();
      if (partial && expression instanceof GrNewExpression && com.intellij.psi.util.PsiUtil.resolveClassInType(type) == null) {
        type = null;
      }
      if (eraseArgs) {
        type = TypeConversionUtil.erasure(type);
      }
      innerArgs.add(new InnerArg(type, expression));
    }

    for (GrClosableBlock closureArgument : closureArguments) {
      innerArgs.add(new InnerArg(TypeConversionUtil.erasure(closureArgument.getType()), closureArgument));
    }

    final ArgInfo<InnerArg>[] innerMap = mapParametersToArguments(signature, innerArgs.toArray(new InnerArg[innerArgs.size()]), new Function<InnerArg, PsiType>() {
        @Override
        public PsiType fun(InnerArg o) {
          return o.type;
        }
      }, context, partial);
    if (innerMap == null) return null;

    ArgInfo<PsiElement>[] map = new ArgInfo[innerMap.length];
    int i = 0;
    if (hasNamedArgs) {
      map[i] = new ArgInfo<PsiElement>(innerMap[i].args.iterator().next().list, true);
      i++;
    }

    for (; i < innerMap.length; i++) {
      final ArgInfo<InnerArg> innerArg = innerMap[i];
      if (innerArg == null) {
        map[i] = null;
      }
      else {
        List<PsiElement> argList = new ArrayList<PsiElement>();
        for (InnerArg arg : innerArg.args) {
          argList.addAll(arg.list);
        }
        boolean multiArg = innerArg.isMultiArg || argList.size() > 1;
        map[i] = new ArgInfo<PsiElement>(argList, multiArg);
      }
    }

    return map;
  }

  public static List<MethodSignature> generateAllSignaturesForMethod(GrMethod method, PsiSubstitutor substitutor) {
    GrClosureSignature signature = createSignature(method, substitutor);
    String name = method.getName();
    PsiTypeParameter[] typeParameters = method.getTypeParameters();

    final ArrayList<MethodSignature> result = new ArrayList<MethodSignature>();
    generateAllMethodSignaturesByClosureSignature(name, signature, typeParameters, substitutor, result);
    return result;
  }

  public static MultiMap<MethodSignature, PsiMethod> findMethodSignatures(PsiMethod[] methods) {
    MultiMap<MethodSignature, PsiMethod> map = new MultiMap<MethodSignature, PsiMethod>();
    for (PsiMethod method : methods) {
      final PsiMethod actual = method instanceof GrReflectedMethod ? ((GrReflectedMethod)method).getBaseMethod() : method;
      map.putValue(method.getSignature(PsiSubstitutor.EMPTY), actual);
    }

    return map;
  }

  private static MethodSignature generateSignature(String name,
                                                   List<PsiType> paramTypes,
                                                   PsiTypeParameter[] typeParameters,
                                                   PsiSubstitutor substitutor) {
    return MethodSignatureUtil.createMethodSignature(name, paramTypes.toArray(new PsiType[paramTypes.size()]), typeParameters, substitutor);
  }

  public static void generateAllMethodSignaturesByClosureSignature(@NotNull String name,
                                                                                    @NotNull GrClosureSignature signature,
                                                                                    @NotNull PsiTypeParameter[] typeParameters,
                                                                                    @NotNull PsiSubstitutor substitutor,
                                                                                    List<MethodSignature> result) {
    GrClosureParameter[] params = signature.getParameters();

    ArrayList<PsiType> newParams = new ArrayList<PsiType>(params.length);
    ArrayList<GrClosureParameter> opts = new ArrayList<GrClosureParameter>(params.length);
    ArrayList<Integer> optInds = new ArrayList<Integer>(params.length);

    for (int i = 0; i < params.length; i++) {
      if (params[i].isOptional()) {
        opts.add(params[i]);
        optInds.add(i);
      }
      else {
        newParams.add(params[i].getType());
      }
    }

    result.add(generateSignature(name, newParams, typeParameters, substitutor));
    for (int i = 0; i < opts.size(); i++) {
      newParams.add(optInds.get(i), opts.get(i).getType());
      result.add(generateSignature(name, newParams, typeParameters, substitutor));
    }
  }

  public static List<MethodSignature> generateAllMethodSignaturesBySignature(@NotNull final String name,
                                                                             @NotNull final GrSignature signature) {

    final ArrayList<MethodSignature> result = new ArrayList<MethodSignature>();
    signature.accept(new GrRecursiveSignatureVisitor() {
      @Override
      public void visitClosureSignature(GrClosureSignature signature) {
        generateAllMethodSignaturesByClosureSignature(name, signature, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY, result);
      }
    });
    return result;
  }

  @Nullable
  public static PsiType getTypeByArg(ArgInfo<PsiElement> arg, PsiManager manager, GlobalSearchScope resolveScope) {
    if (arg.isMultiArg) {
      if (arg.args.size() == 0) return PsiType.getJavaLangObject(manager, resolveScope).createArrayType();
      PsiType leastUpperBound = null;
      PsiElement first = arg.args.get(0);
      if (first instanceof GrNamedArgument) {
        GrNamedArgument[] args=new GrNamedArgument[arg.args.size()];
        for (int i = 0, size = arg.args.size(); i < size; i++) {
          args[i] = (GrNamedArgument)arg.args.get(i);
        }
        return new GrMapType(first, args);
      }
      else {
        for (PsiElement elem : arg.args) {
          if (elem instanceof GrExpression) {
            leastUpperBound = TypesUtil.getLeastUpperBoundNullable(leastUpperBound, ((GrExpression)elem).getType(), manager);
          }
        }
        if (leastUpperBound == null) return null;
        return leastUpperBound.createArrayType();
      }
    }
    else {
      if (arg.args.size() == 0) return null;
      PsiElement elem = arg.args.get(0);
      if (elem instanceof GrExpression) {
        return ((GrExpression)elem).getType();
      }
      return null;
    }
  }


  /**
   *
   * @param signature
   * @return return type or null if there is some different return types
   */
  @Nullable
  public static PsiType getReturnType(GrSignature signature) {
    if (signature instanceof GrClosureSignature) {
      return ((GrClosureSignature)signature).getReturnType();
    }
    else if (signature instanceof GrMultiSignature) {
      final GrClosureSignature[] signatures = ((GrMultiSignature)signature).getAllSignatures();
      if (signatures.length == 0) return null;
      final PsiType type = signatures[0].getReturnType();
      if (type == null) return null;
      String firstType = type.getCanonicalText();
      for (int i = 1; i < signatures.length; i++) {
        final PsiType _type = signatures[i].getReturnType();
        if (_type == null) return null;
        if (!firstType.equals(_type.getCanonicalText())) return null;
      }
      return type;
    }

    return null;
  }


  public static class MapResultWithError<Arg> {
    private final ArgInfo<Arg>[] mapping;
    private final List<Pair<Integer, PsiType>> errorsAndExpectedType;

    public MapResultWithError(ArgInfo<Arg>[] mapping, List<Pair<Integer, PsiType>> errorsAndExpectedType) {
      this.mapping = mapping;
      this.errorsAndExpectedType = errorsAndExpectedType;
    }

    public ArgInfo<Arg>[] getMapping() {
      return mapping;
    }

    public List<Pair<Integer, PsiType>> getErrors() {
      return errorsAndExpectedType;
    }
  }

  @Nullable
  public static <Arg> MapResultWithError<Arg> mapSimpleSignatureWithErrors(@NotNull GrClosureSignature signature,
                                                                            @NotNull Arg[] args,
                                                                            @NotNull Function<Arg, PsiType> typeComputer,
                                                                            @NotNull GroovyPsiElement context,
                                                                            int maxErrorCount) {
    final GrClosureParameter[] params = signature.getParameters();
    if (args.length < params.length) return null;

    if (args.length > params.length && !signature.isVarargs()) return null;

    int optional = getOptionalParamCount(params, false);
    assert optional == 0;

    int errorCount = 0;
    ArgInfo<Arg>[] map = new ArgInfo[params.length];

    List<Pair<Integer, PsiType>> errors = new ArrayList<Pair<Integer, PsiType>>(maxErrorCount);

    for (int i = 0; i < params.length; i++) {
      if (isAssignableByConversion(params[i].getType(), typeComputer.fun(args[i]), context)) {
        map[i] = new ArgInfo<Arg>(args[i]);
      }
      else if (params[i].getType() instanceof PsiArrayType && i == params.length - 1) {
        if (i + 1 == args.length) {
          errors.add(new Pair<Integer, PsiType>(i, params[i].getType()));
        }
        final PsiType ellipsis = ((PsiArrayType)params[i].getType()).getComponentType();
        for (int j = i; j < args.length; j++) {
          if (!isAssignableByConversion(ellipsis, typeComputer.fun(args[j]), context)) {
            errorCount++;
            if (errorCount > maxErrorCount) return null;
            errors.add(new Pair<Integer, PsiType>(i, ellipsis));
          }
          map[i] = new ArgInfo<Arg>(args[i]);
        }
      }
      else {
        errorCount++;
        if (errorCount > maxErrorCount) return null;
        errors.add(new Pair<Integer, PsiType>(i, params[i].getType()));
      }
    }
    return new MapResultWithError<Arg>(map, errors);
  }

  public static List<GrClosureSignature> generateSimpleSignature(GrSignature signature) {
    final List<GrClosureSignature> result = new ArrayList<GrClosureSignature>();
    signature.accept(new GrRecursiveSignatureVisitor() {
      @Override
      public void visitClosureSignature(GrClosureSignature signature) {
        final GrClosureParameter[] original = signature.getParameters();
        final ArrayList<GrClosureParameter> parameters = new ArrayList<GrClosureParameter>(original.length);

        for (GrClosureParameter parameter : original) {
          parameters.add(new GrClosureParameterImpl(parameter.getType(), false, null));
        }

        final int pcount = signature.isVarargs() ? signature.getParameterCount() - 2 : signature.getParameterCount() - 1;
        for (int i = pcount; i >= 0; i--) {
          if (original[i].isOptional()) {
            result.add(new GrClosureSignatureImpl(parameters.toArray(new GrClosureParameter[parameters.size()]), signature.getReturnType(), signature.isVarargs(), false));
            parameters.remove(i);
          }
        }
        result.add(new GrClosureSignatureImpl(parameters.toArray(new GrClosureParameter[parameters.size()]), signature.getReturnType(), signature.isVarargs(), false));
      }
    });
    return result;
  }
}
