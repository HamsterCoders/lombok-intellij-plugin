package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.ExplicitTypeCanBeDiamondInspection;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiNewExpression;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Overwrites intellij standard diamond inspection, to filter out lombok "val" declarations
 */
public class LombokExplicitTypeCanBeDiamondInspection extends ExplicitTypeCanBeDiamondInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    final JavaElementVisitor originalVisitor = (JavaElementVisitor) super.buildVisitor(holder, isOnTheFly);

    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        if (!possibleValDeclaration(expression)) {
          originalVisitor.visitNewExpression(expression);
        }
      }

      private boolean possibleValDeclaration(PsiNewExpression expression) {
        final PsiElement expressionParent = expression.getParent();
        if (expressionParent instanceof PsiLocalVariable) {
          PsiLocalVariable localVar = (PsiLocalVariable) expressionParent;
          return ValProcessor.isVal(localVar.getTypeElement());
        }
        return false;
      }
    };
  }
}
