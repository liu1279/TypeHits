import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class TypeFix implements LocalQuickFix {
    @SafeFieldForPreview
    PyElementGenerator pyElementGenerator = null;
    @SafeFieldForPreview
    TypeInfer typeInfer = null;

    @NotNull
    @Override
    public String getName() {
        return InspectionBundle.message("inspection.checking.type.declare.use.quickfix");
    }


    @Override
    public @IntentionFamilyName
    @NotNull String getFamilyName() {
        return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        pyElementGenerator = PyElementGenerator.getInstance(project);
        TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(project, descriptor.getPsiElement().getContainingFile());
        typeInfer = new TypeInfer(typeEvalContext, this);
        try {
            applyFixElement(descriptor.getPsiElement());
        } catch (Exception e) {
            Messages.showErrorDialog(e.getMessage(), "Infer Type Error");

            System.out.println(e.getMessage());
        }
    }

    public String applyFixElement(PsiElement psiElement) throws Exception {
        if (psiElement instanceof PyTargetExpression) {
            String annotation = typeInfer.getInferedAnnotation(((PyAssignmentStatement) psiElement.getParent()).getAssignedValue());
            PyTypeDeclarationStatement templateDeclaration = pyElementGenerator.createFromText(
                    LanguageLevel.forElement(psiElement), PyTypeDeclarationStatement.class,
                    "a:" + annotation);
            if (templateDeclaration.getAnnotation() == null) {
                Until.throwErrorWithPosition(psiElement, "generate statement fail from psiElement: " + psiElement.getText());
                return null;
            }
            psiElement.add(templateDeclaration.getAnnotation());
            typeInfer.addImport(psiElement);
            return annotation;

        } else if (psiElement.getParent() instanceof PyFunction || psiElement instanceof PyNamedParameter) {
            boolean isFunction = psiElement.getParent() instanceof PyFunction;
            PyFunction function = isFunction ? (PyFunction) psiElement.getParent() : (PyFunction) psiElement.getParent().getParent();
            PyParameter[] parameters = function.getParameterList().getParameters();
            PsiReference psiReference = ReferencesSearch.search(function).findFirst();
            String resultAnnotation = null;
            if (psiReference == null) {
                Until.throwErrorWithPosition(function, "there is no reference of fucntion: " + function.getName());
                return null;
            }

            PyCallExpression pyCallExpression = (PyCallExpression) (psiReference.getElement().getParent());
            PyArgumentList argumentList = pyCallExpression.getArgumentList();
            if (argumentList == null) {
                Until.throwErrorWithPosition(function, "there is no PyArgumentList of pyCallExpression: " + pyCallExpression.getName());
                return null;
            }
            PyExpression[] orderedArguments = getOrderedReferenceArguments(parameters, argumentList.getArguments());
            for (int i = 0; i < orderedArguments.length; i++) {
                String annotation = typeInfer.getInferedAnnotation(orderedArguments[i]);
                if (!isFunction && Objects.equals(((PyNamedParameter) psiElement).getName(), orderedArguments[i].getName())) {
                    resultAnnotation = annotation;
                }
                if (((PyNamedParameter) parameters[i]).getAnnotation() == null) {
                    PyTypeDeclarationStatement templateDeclaration = pyElementGenerator.createFromText(
                            LanguageLevel.forElement(psiElement), PyTypeDeclarationStatement.class,
                            "a:" + annotation);
                    if (templateDeclaration.getAnnotation() == null) {
                        Until.throwErrorWithPosition(psiElement, "generate statement fail from psiElement: " + psiElement.getText());
                        return null;
                    }
                    parameters[i].add(templateDeclaration.getAnnotation());
                    typeInfer.addImport(parameters[i]);
                }
            }

            if (function.getAnnotation() == null) {
                PyType returnStatementType = typeInfer.getFunctionReturnType(function);
                String returnTypeName = returnStatementType == null ? "None" : returnStatementType.getName();
                if (isFunction) {
                    resultAnnotation = returnTypeName;
                }
                PyFunction templateFunction = pyElementGenerator.createFromText(
                        LanguageLevel.forElement(psiElement), PyFunction.class,
                        "def a()->" + returnTypeName + ":\n	pass");
                if (templateFunction.getAnnotation() == null) {
                    Until.throwErrorWithPosition(psiElement, "generate function fail from psiElement: " + psiElement.getText());
                    return null;
                }
                function.addAfter(templateFunction.getAnnotation(), function.getParameterList());
            }
            return resultAnnotation;
        } else {
            Until.throwErrorWithPosition(psiElement, "unexcept element to fix");
        }
        return null;
    }


    private PyExpression[] getOrderedReferenceArguments(PyParameter[] parameters, PyExpression[] referenceArguments) {
        if (referenceArguments.length == 0 || !(referenceArguments[0] instanceof PyKeywordArgument)) {
            return referenceArguments;
        }
        PyExpression[] result = new PyExpression[referenceArguments.length];
        for (int i = 0; i < result.length; i++) {
            String targetName = parameters[i].getName();
            int j = 0;
            PyKeywordArgument target = (PyKeywordArgument) referenceArguments[j];
            while (!Objects.equals(target.getKeyword(), targetName)) {
                j++;
                target = (PyKeywordArgument) referenceArguments[j];
            }
            result[i] = target.getValueExpression();
        }
        return result;
    }

}


