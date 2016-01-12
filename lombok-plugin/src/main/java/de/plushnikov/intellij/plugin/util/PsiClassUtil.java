package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Plushnikov Michail
 */
public class PsiClassUtil {

  /**
   * Workaround to get all of original Methods of the psiClass, without calling PsiAugmentProvider infinitely
   *
   * @param psiClass psiClass to collect all of methods from
   * @return all intern methods of the class
   */
  @NotNull
  public static Collection<PsiMethod> collectClassMethodsIntern(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiExtensibleClass) {
      return ((PsiExtensibleClass) psiClass).getOwnMethods();
    } else {
      return filterPsiElements(psiClass, PsiMethod.class);
    }
  }

  /**
   * Workaround to get all of original Fields of the psiClass, without calling PsiAugmentProvider infinitely
   *
   * @param psiClass psiClass to collect all of fields from
   * @return all intern fields of the class
   */
  @NotNull
  public static Collection<PsiField> collectClassFieldsIntern(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiExtensibleClass) {
      return ((PsiExtensibleClass) psiClass).getOwnFields();
    } else {
      return filterPsiElements(psiClass, PsiField.class);
    }
  }

  /**
   * Workaround to get all of original inner classes of the psiClass, without calling PsiAugmentProvider infinitely
   *
   * @param psiClass psiClass to collect all inner classes from
   * @return all inner classes of the class
   */
  @NotNull
  public static Collection<PsiClass> collectInnerClassesIntern(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiExtensibleClass) {
      return ((PsiExtensibleClass) psiClass).getOwnInnerClasses();
    } else {
      return filterPsiElements(psiClass, PsiClass.class);
    }
  }

  protected static <T extends PsiElement> Collection<T> filterPsiElements(@NotNull PsiClass psiClass, @NotNull Class<T> disiredClass) {
    Collection<T> result = new ArrayList<T>();
    for (PsiElement psiElement : psiClass.getChildren()) {
      if (disiredClass.isAssignableFrom(psiElement.getClass())) {
        result.add((T) psiElement);
      }
    }
    return result;
  }

  @NotNull
  public static Collection<PsiMethod> collectClassConstructorIntern(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> psiMethods = collectClassMethodsIntern(psiClass);

    Collection<PsiMethod> classConstructors = new ArrayList<PsiMethod>(3);
    for (PsiMethod psiMethod : psiMethods) {
      if (psiMethod.isConstructor()) {
        classConstructors.add(psiMethod);
      }
    }
    return classConstructors;
  }

  @NotNull
  public static Collection<PsiMethod> collectClassStaticMethodsIntern(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> psiMethods = collectClassMethodsIntern(psiClass);

    Collection<PsiMethod> staticMethods = new ArrayList<PsiMethod>(5);
    for (PsiMethod psiMethod : psiMethods) {
      if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        staticMethods.add(psiMethod);
      }
    }
    return staticMethods;
  }

  public static boolean hasSuperClass(@NotNull final PsiClass psiClass) {
    final PsiClass superClass = psiClass.getSuperClass();
    return (null != superClass && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())
        || !superTypesIsEmptyOrObjectOnly(psiClass));
  }

  private static boolean superTypesIsEmptyOrObjectOnly(@NotNull final PsiClass psiClass) {
    // It returns abstract classes, but also Object.
    final PsiClassType[] superTypes = psiClass.getSuperTypes();
    return superTypes.length == 0 || superTypes.length > 1 || CommonClassNames.JAVA_LANG_OBJECT.equals(superTypes[0].getCanonicalText());
  }

  /**
   * Creates a PsiType for a PsiClass enriched with generic substitution information if available
   */
  @NotNull
  public static PsiType getTypeWithGenerics(@NotNull PsiClass psiClass) {
    PsiType result;
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    final PsiTypeParameter[] classTypeParameters = psiClass.getTypeParameters();
    if (classTypeParameters.length > 0) {
      Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<PsiTypeParameter, PsiType>();
      for (PsiTypeParameter typeParameter : classTypeParameters) {
        substitutionMap.put(typeParameter, factory.createType(typeParameter));
      }
      result = factory.createType(psiClass, factory.createSubstitutor(substitutionMap));
    } else {
      result = factory.createType(psiClass);
    }
    return result;
  }

  /**
   * Workaround to get inner class of the psiClass, without calling PsiAugmentProvider infinitely
   *
   * @param psiClass psiClass to search for inner class
   * @return inner class if found
   */
  @Nullable
  public static PsiClass getInnerClassInternByName(@NotNull PsiClass psiClass, @NotNull String className) {
    Collection<PsiClass> innerClasses = collectInnerClassesIntern(psiClass);
    for (PsiClass innerClass : innerClasses) {
      if (className.equals(innerClass.getName())) {
        return innerClass;
      }
    }
    return null;
  }

  public static Collection<String> getNames(Collection<? extends PsiMember> psiMembers) {
    Collection<String> result = new HashSet<String>();
    for (PsiMember psiMember : psiMembers) {
      result.add(psiMember.getName());
    }
    return result;
  }

  @Nullable
  public static PsiClass getInnerClassByName(@NotNull PsiClass psiClass, @NotNull String className) {
    PsiClass[] innerClasses = psiClass.getInnerClasses();
    for (PsiClass innerClass : innerClasses) {
      if (className.equals(innerClass.getName())) {
        return innerClass;
      }
    }
    return null;
  }

  public static boolean hasParent(@NotNull PsiClass clazz, @NotNull PsiClass classParent) {
    for (PsiClass parent : getAllParents(clazz)) {
      if (parent.getQualifiedName().equals(classParent.getQualifiedName())) return true;
    }
    return false;
  }

  @NotNull
  public static Set<PsiClass> getAllParents(@NotNull PsiClass clazz) {
    return getAllParents(clazz, new HashSet<PsiClass>());
  }

  @NotNull
  public static Set<PsiClass> getAllParents(@NotNull PsiClass clazz, @NotNull Set<PsiClass> allParents) {
    for (PsiClass aClass : clazz.getSupers()) {
      if (!allParents.contains(aClass)) {
        allParents.add(aClass);
        getAllParents(aClass, allParents);
      }
    }
    return allParents;
  }
}
