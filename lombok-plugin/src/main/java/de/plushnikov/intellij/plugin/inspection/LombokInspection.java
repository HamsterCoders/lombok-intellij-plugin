package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiTypeElement;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import de.plushnikov.intellij.plugin.provider.LombokProcessorProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Plushnikov Michail
 */
public class LombokInspection extends BaseJavaLocalInspectionTool {

  private final LombokProcessorProvider processorProvider;
  private final ValProcessor valProcessor;

  public LombokInspection() {
    processorProvider = LombokProcessorProvider.getInstance();
    valProcessor = new ValProcessor();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Lombok annotations inspection";
  }

  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Lombok";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new LombokElementVisitor(holder);
  }

  private class LombokElementVisitor extends JavaElementVisitor {

    private final ProblemsHolder holder;

    public LombokElementVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitTypeElement(PsiTypeElement type) {
      super.visitTypeElement(type);

      valProcessor.verifyTypeElement(type, holder);
    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);

      final Collection<LombokProblem> problems = new HashSet<LombokProblem>();

      for (Processor inspector : processorProvider.getProcessors(annotation)) {
        problems.addAll(inspector.verifyAnnotation(annotation));
      }

      for (LombokProblem problem : problems) {
        holder.registerProblem(annotation, problem.getMessage(), problem.getHighlightType(), problem.getQuickFixes());
      }
    }
  }
}
