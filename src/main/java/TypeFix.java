import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Objects;

public class TypeFix implements LocalQuickFix {
    PyElementGenerator pyElementGenerator = null;
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

    public void applyFixElement(PsiElement psiElement) throws Exception {
        if (psiElement instanceof PyTargetExpression) {
            if (psiElement.getParent() instanceof PyTupleExpression) {
                PyAssignmentStatement assignmentStatement = (PyAssignmentStatement) psiElement.getParent().getParent();
                PsiElement[] assignValues = assignmentStatement.getAssignedValue().getChildren();
                PyExpression[] targets = assignmentStatement.getTargets();
                for (int i = 0; i < targets.length; i++) {
                    StringBuilder builder = new StringBuilder();
                    typeInfer.inferAnnotation(assignValues[i], builder);
                    PyTypeDeclarationStatement templateDeclaration = pyElementGenerator.createFromText(
                            LanguageLevel.forElement(psiElement), PyTypeDeclarationStatement.class,
                            targets[i].getName() + ":" + builder);
                    assignmentStatement.getParent().addBefore(templateDeclaration, assignmentStatement);
                    typeInfer.addImport(targets[i]);
                }
                return;
            }
            StringBuilder annotationBuilder = new StringBuilder();
            typeInfer.inferAnnotation(((PyAssignmentStatement) psiElement.getParent()).getAssignedValue(), annotationBuilder);
            PyTypeDeclarationStatement templateDeclaration = pyElementGenerator.createFromText(
                    LanguageLevel.forElement(psiElement), PyTypeDeclarationStatement.class,
                    "a:" + annotationBuilder);
            psiElement.add(templateDeclaration.getAnnotation());
            refreshAssignment(psiElement);
            typeInfer.addImport(psiElement);
        } else if (psiElement.getParent() instanceof PyFunction || psiElement instanceof PyNamedParameter) {
            PyFunction function;
            if (psiElement.getParent() instanceof PyFunction) {
                function = (PyFunction) psiElement.getParent();
            } else {
                function = (PyFunction) psiElement.getParent().getParent();
            }

            PyParameter[] parameters = function.getParameterList().getParameters();
            PsiReference psiReference = ReferencesSearch.search(function).findFirst();
            if (psiReference != null) {
                PyCallExpression pyCallExpression = (PyCallExpression) (psiReference.getElement().getParent());
                PyExpression[] referenceArguments = pyCallExpression.getArgumentList().getArguments();
                PyExpression[] orderedArguments = getOrderedReferenceArguments(parameters, referenceArguments);
                for (int i = 0; i < orderedArguments.length; i++) {
                    StringBuilder annotationBuilder = new StringBuilder();
                    typeInfer.inferAnnotation(orderedArguments[i], annotationBuilder);
                    if (((PyNamedParameter) parameters[i]).getAnnotation() == null) {
                        PyTypeDeclarationStatement templateDeclaration = pyElementGenerator.createFromText(
                                LanguageLevel.forElement(psiElement), PyTypeDeclarationStatement.class,
                                "a:" + annotationBuilder);
                        parameters[i].add(templateDeclaration.getAnnotation());
                        typeInfer.addImport(parameters[i]);
                    }
                }
            }
            if (function.getAnnotation() == null) {
                PyType returnStatementType = typeInfer.getFunctionReturnType(function);
                String returnTypeName = returnStatementType == null ? "None" : returnStatementType.getName();
                PyFunction templateFunction = pyElementGenerator.createFromText(
                        LanguageLevel.forElement(psiElement), PyFunction.class,
                        "def a()->" + returnTypeName + ":\n	pass");
                function.addAfter(templateFunction.getAnnotation(), function.getParameterList());
            }

        }
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

    private void refreshAssignment(PsiElement psiElement) {
        PsiElement temp = psiElement;
        while (!(temp instanceof PyAssignmentStatement)) {
            temp = temp.getParent();
        }
        PyAssignmentStatement newPyAssignmentStatement = pyElementGenerator.createFromText(
                LanguageLevel.forElement(temp), PyAssignmentStatement.class,
                temp.getText());
        temp.replace(newPyAssignmentStatement);
    }
}


