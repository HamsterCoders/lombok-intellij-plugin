/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import java.util.Collection;
import java.util.Set;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTION;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_SET;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_STREAM_STREAM;

/**
 * @author peter
 */
class CollectConversionEx {

  static void addCollectConversion(PsiReferenceExpression ref, Collection<ExpectedTypeInfo> expectedTypes, Consumer<LookupElement> consumer) {
    final PsiExpression qualifier = ref.getQualifierExpression();
    PsiType component = qualifier == null ? null : PsiUtil.substituteTypeParameter(qualifier.getType(), JAVA_UTIL_STREAM_STREAM, 0, true);
    if (component == null) return;

    JavaPsiFacade facade = JavaPsiFacade.getInstance(ref.getProject());
    PsiElementFactory factory = facade.getElementFactory();
    GlobalSearchScope scope = ref.getResolveScope();
    PsiClass list = facade.findClass(JAVA_UTIL_LIST, scope);
    PsiClass set = facade.findClass(JAVA_UTIL_SET, scope);
    PsiClass collection = facade.findClass(JAVA_UTIL_COLLECTION, scope);
    if (facade.findClass(JAVA_UTIL_STREAM_COLLECTORS, scope) == null || list == null || set == null || collection == null) return;

    PsiType listType = null;
    PsiType setType = null;
    boolean hasIterable = false;
    for (ExpectedTypeInfo info : expectedTypes) {
      PsiType type = info.getDefaultType();
      PsiClass expectedClass = PsiUtil.resolveClassInClassTypeOnly(type);
      PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(type, true);
      if (expectedClass == null || expectedComponent == null || !TypeConversionUtil.isAssignable(expectedComponent, component)) continue;
      hasIterable = true;

      if (InheritanceUtil.isInheritorOrSelf(list, expectedClass, true)) {
        listType = type;
      }
      if (InheritanceUtil.isInheritorOrSelf(set, expectedClass, true)) {
        setType = type;
      }
    }

    if (expectedTypes.isEmpty()) {
      listType = factory.createType(list, component);
      setType = factory.createType(set, component);
    }

    if (listType != null) {
      consumer.consume(new MyLookupElement("toList", listType, ref));
    }
    if (setType != null) {
      consumer.consume(new MyLookupElement("toSet", setType, ref));
    }

    if (expectedTypes.isEmpty() || hasIterable) {
      consumer.consume(new MyLookupElement("toCollection", factory.createType(collection, component), ref));
    }
  }

  private static class MyLookupElement extends LookupElement implements TypedLookupItem {
    private final String myLookupString;
    private final String myTypeText;
    private final String myMethodName;
    @NotNull private final PsiType myExpectedType;
    private final boolean myHasImport;

    MyLookupElement(String methodName, @NotNull PsiType expectedType, @NotNull PsiElement context) {
      myMethodName = methodName;
      myExpectedType = expectedType;
      myTypeText = myExpectedType.getPresentableText();

      PsiMethodCallExpression call = (PsiMethodCallExpression)
        JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(methodName + "()", context);
      myHasImport = ContainerUtil.or(call.getMethodExpression().multiResolve(true), result -> {
        PsiElement element = result.getElement();
        return element instanceof PsiMember &&
               (JAVA_UTIL_STREAM_COLLECTORS + "." + myMethodName).equals(PsiUtil.getMemberQualifiedName((PsiMember)element));
      });

      myLookupString = "collect(" + (myHasImport ? "" : "Collectors.") + myMethodName + "())";
    }

    @NotNull
    @Override
    public String getLookupString() {
      return myLookupString;
    }

    @Override
    public Set<String> getAllLookupStrings() {
      return ContainerUtil.newHashSet(myLookupString, myMethodName);
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      super.renderElement(presentation);
      presentation.setTypeText(myTypeText);
      presentation.setIcon(PlatformIcons.METHOD_ICON);
    }

    @Override
    public void handleInsert(InsertionContext context) {
      context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), getInsertString());
      context.commitDocument();

      PsiMethodCallExpression call =
        PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiMethodCallExpression.class, false);
      if (call == null) return;

      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1 || !(args[0] instanceof PsiMethodCallExpression)) return;

      PsiMethodCallExpression innerCall = (PsiMethodCallExpression)args[0];
      PsiMethod collectorMethod = innerCall.resolveMethod();
      if (collectorMethod != null && collectorMethod.getParameterList().getParametersCount() > 0) {
        context.getEditor().getCaretModel().moveToOffset(innerCall.getArgumentList().getFirstChild().getTextRange().getEndOffset());
      }

      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(innerCall);
    }

    @NotNull
    private String getInsertString() {
      return "collect(" + (myHasImport ? "" : JAVA_UTIL_STREAM_COLLECTORS + ".") + myMethodName + "())";
    }

    @Override
    public PsiType getType() {
      return myExpectedType;
    }
  }
}
