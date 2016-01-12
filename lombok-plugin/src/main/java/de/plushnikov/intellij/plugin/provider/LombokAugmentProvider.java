package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


/**
 * Provides support for lombok generated elements
 *
 * @author Plushnikov Michail
 */
public class LombokAugmentProvider extends PsiAugmentProvider {
  private static final Logger log = Logger.getInstance(LombokAugmentProvider.class.getName());

  private ValProcessor valProcessor;

  public LombokAugmentProvider() {
    log.debug("LombokAugmentProvider created");
    valProcessor = new ValProcessor();
  }

  @Nullable
  protected PsiType inferType(PsiTypeElement typeElement) {
    if (null == typeElement || DumbService.isDumb(typeElement.getProject())) {
      return null;
    }
    return valProcessor.inferType(typeElement);
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull final Class<Psi> type) {
    final List<Psi> emptyResult = Collections.emptyList();
    // skip processing during index rebuild
    final Project project = element.getProject();
    if (DumbService.isDumb(project)) {
      return emptyResult;
    }
    // Expecting that we are only augmenting an PsiClass
    // Don't filter !isPhysical elements or code auto completion will not work
    if (!(element instanceof PsiExtensibleClass) || !element.isValid()) {
      return emptyResult;
    }

    // skip processing if plugin is disabled
    if (!ProjectSettings.loadAndGetEnabledInProject(project)) {
      return emptyResult;
    }

    final PsiClass psiClass = (PsiClass) element;

    if (type == PsiField.class) {
      return CachedValuesManager.getCachedValue(element, new FieldLombokCachedValueProvider<Psi>(type, psiClass));
    } else if (type == PsiMethod.class) {
      return CachedValuesManager.getCachedValue(element, new MethodLombokCachedValueProvider<Psi>(type, psiClass));
    } else if (type == PsiClass.class) {
      return CachedValuesManager.getCachedValue(element, new ClassLombokCachedValueProvider<Psi>(type, psiClass));
    } else {
      return emptyResult;
    }
  }

  private static class FieldLombokCachedValueProvider<Psi extends PsiElement> extends LombokCachedValueProvider<Psi> {
    public FieldLombokCachedValueProvider(Class<Psi> type, PsiClass psiClass) {
      super(type, psiClass);
    }
  }

  private static class MethodLombokCachedValueProvider<Psi extends PsiElement> extends LombokCachedValueProvider<Psi> {
    public MethodLombokCachedValueProvider(Class<Psi> type, PsiClass psiClass) {
      super(type, psiClass);
    }
  }

  private static class ClassLombokCachedValueProvider<Psi extends PsiElement> extends LombokCachedValueProvider<Psi> {
    public ClassLombokCachedValueProvider(Class<Psi> type, PsiClass psiClass) {
      super(type, psiClass);
    }
  }

  private static class LombokCachedValueProvider<Psi extends PsiElement> implements CachedValueProvider<List<Psi>> {
    private final Class<Psi> type;
    private final PsiClass psiClass;

    public LombokCachedValueProvider(Class<Psi> type, PsiClass psiClass) {
      this.type = type;
      this.psiClass = psiClass;
    }

    @Nullable
    @Override
    public Result<List<Psi>> compute() {
      if (log.isDebugEnabled()) {
        log.debug(String.format("Process call for type: %s class: %s", type, psiClass.getQualifiedName()));
      }

      final List<Psi> result = new ArrayList<Psi>();
      final Collection<Processor> lombokProcessors = LombokProcessorProvider.getInstance().getLombokProcessors(type);
      for (Processor processor : lombokProcessors) {
        result.addAll((Collection<Psi>) processor.process(psiClass));
      }
      return new Result<List<Psi>>(result, psiClass);
    }
  }
}
