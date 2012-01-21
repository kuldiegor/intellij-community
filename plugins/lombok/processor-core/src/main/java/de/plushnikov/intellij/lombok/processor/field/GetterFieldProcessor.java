package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.Modifier;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.lombok.util.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import de.plushnikov.intellij.lombok.util.PsiPrimitiveTypeFactory;
import lombok.Getter;
import lombok.core.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Getter lombok annotation on a field
 * Creates getter method for this field
 *
 * @author Plushnikov Michail
 */
public class GetterFieldProcessor extends AbstractLombokFieldProcessor {

  public GetterFieldProcessor() {
    super(Getter.class, PsiMethod.class);
  }

  protected GetterFieldProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<?> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      target.add((Psi) createGetterMethod(psiField, methodVisibility));
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result;

    final String methodVisibity = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    result = null != methodVisibity;

    final boolean lazy = isLazyGetter(psiAnnotation);
    if (null == methodVisibity && lazy) {
      builder.addWarning("'lazy' does not work with AccessLevel.NONE.");
    }

    if (result && lazy) {
      if (!psiField.hasModifierProperty(PsiModifier.FINAL) || !psiField.hasModifierProperty(PsiModifier.PRIVATE)) {
        builder.addError("'lazy' requires the field to be private and final",
            PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.PRIVATE, true, false),
            PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, true, false));
      }
      if (null == psiField.getInitializer()) {
        builder.addError("'lazy' requires field initialization.");
      }
    }

    if (result) {
      result = validateExistingMethods(psiField, builder);
    }

    return result;
  }

  protected boolean isLazyGetter(@NotNull PsiAnnotation psiAnnotation) {
    final Boolean lazyObj = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "lazy", Boolean.class);
    return null != lazyObj && lazyObj;
  }

  protected boolean validateExistingMethods(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      final PsiType booleanType = PsiPrimitiveTypeFactory.getInstance().getBooleanType();
      final boolean isBoolean = booleanType.equals(psiField.getType());
      final Collection<String> methodNames = TransformationsUtil.toAllGetterNames(psiField.getName(), isBoolean);
      final PsiMethod[] classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);

      for (String methodName : methodNames) {
        if (PsiMethodUtil.hasMethodByName(classMethods, methodName)) {
          final String setterMethodName = TransformationsUtil.toGetterName(psiField.getName(), isBoolean);

          builder.addWarning(String.format("Not generated '%s'(): A method with similar name '%s' already exists", setterMethodName, methodName));
          result = false;
        }
      }
    }
    return result;
  }

  @NotNull
  public PsiMethod createGetterMethod(@NotNull PsiField psiField, @Modifier @NotNull String methodModifier) {
    final String fieldName = psiField.getName();
    final PsiType psiReturnType = psiField.getType();
    final PsiType booleanType = PsiPrimitiveTypeFactory.getInstance().getBooleanType();
    String methodName = TransformationsUtil.toGetterName(fieldName, booleanType.equals(psiReturnType));

//    final Collection<String> annotationsToCopy = PsiAnnotationUtil.collectAnnotationsToCopy(psiField, LombokConstants.NON_NULL_PATTERN);
//    final String annotationsString = PsiAnnotationUtil.buildAnnotationsString(annotationsToCopy);
    //TODO adapt annotations

    PsiClass psiClass = psiField.getContainingClass();
    assert psiClass != null;

    UserMapKeys.addReadUsageFor(psiField);

    LombokLightMethodBuilder method = LombokPsiElementFactory.getInstance().createLightMethod(psiField.getManager(), methodName)
        .withMethodReturnType(psiReturnType)
        .withContainingClass(psiClass)
        .withNavigationElement(psiField);
    if (StringUtil.isNotEmpty(methodModifier)) {
      method.withModifier(methodModifier);
    }
    if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
      method.withModifier(PsiModifier.STATIC);
    }
    return method;
  }

}
