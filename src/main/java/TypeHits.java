import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.jetbrains.python.PyElementTypes.*;
import static com.jetbrains.python.inspections.PyTypeCheckerInspection.Visitor.tryPromotingType;

public class TypeHits extends PyInspection {
    private final TypeFix myQuickFix = new TypeFix();

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PyElementVisitor() {
            @Override
            public void visitPyElement(@NotNull PyElement element) {
                if (element instanceof PyTargetExpression) {
                    if (((PyTargetExpression) element).getAnnotationValue() == null
                            && !(element.getParent() instanceof PyForPart)
                            && ((PyTargetExpressionImpl) element).getReference().resolve() == element) {
                        holder.registerProblem(element, "No type declare of variable " + element.getText(), myQuickFix);
                    }
                } else if (element instanceof PyFunction pyFunction) {
                    boolean isNeedFix = pyFunction.getAnnotation() == null;
                    for (PyParameter parameter : pyFunction.getParameterList().getParameters()) {
                        if (!parameter.getText().equals("self") && ((PyNamedParameter) parameter).getAnnotation() == null) {
                            isNeedFix = true;
                            break;
                        }
                    }
                    if (isNeedFix) {
                        holder.registerProblem(pyFunction.getNameNode().getPsi(),
                                "lose some type declare of function " + pyFunction.getName(), myQuickFix);
                    }
                }
            }
        };

    }

    private static class TypeFix implements LocalQuickFix {
        PyElementGenerator pyElementGenerator = null;
        TypeEvalContext typeEvalContext = null;
        Project project = null;

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
            typeEvalContext = TypeEvalContext.userInitiated(project, descriptor.getPsiElement().getContainingFile());
            this.project = project;
            try {
                applyFixElement(descriptor.getPsiElement(), pyElementGenerator);
            } catch (Exception e) {
                Messages.showErrorDialog(e.getMessage(), "infer type error");

                System.out.println(e.getMessage());
            }
        }

        private void applyFixElement(PsiElement psiElement, PyElementGenerator pyElementGenerator) throws Exception {
            if (psiElement instanceof PyTargetExpression) {
                if (psiElement.getParent() instanceof PyTupleExpression) {
                    PyAssignmentStatement assignmentStatement = (PyAssignmentStatement) psiElement.getParent().getParent();
                    PsiElement[] assignValues = assignmentStatement.getAssignedValue().getChildren();
                    PyExpression[] targets = assignmentStatement.getTargets();
                    for (int i = 0; i < targets.length; i++) {
                        StringBuilder builder = new StringBuilder();
                        inferAnnotation(assignValues[i], builder);
                        PyTypeDeclarationStatement templateDeclaration = pyElementGenerator.createFromText(
                                LanguageLevel.forElement(psiElement), PyTypeDeclarationStatement.class,
                                targets[i].getName() + ":" + builder);
                        assignmentStatement.getParent().addBefore(templateDeclaration, assignmentStatement);
                    }
                    return;
                }
                StringBuilder annotationBuilder = new StringBuilder();
                inferAnnotation(((PyAssignmentStatement) psiElement.getParent()).getAssignedValue(), annotationBuilder);
                PyTypeDeclarationStatement templateDeclaration = pyElementGenerator.createFromText(
                        LanguageLevel.forElement(psiElement), PyTypeDeclarationStatement.class,
                        "a:" + annotationBuilder);
                psiElement.add(templateDeclaration.getAnnotation());
                refreshAssignment(psiElement);
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
                        inferAnnotation(orderedArguments[i], annotationBuilder);
                        if (((PyNamedParameter) parameters[i]).getAnnotation() == null) {
                            PyTypeDeclarationStatement templateDeclaration = pyElementGenerator.createFromText(
                                    LanguageLevel.forElement(psiElement), PyTypeDeclarationStatement.class,
                                    "a:" + annotationBuilder);
                            parameters[i].add(templateDeclaration.getAnnotation());
                        }
                    }
                }
                if (function.getAnnotation() == null) {
                    PyType returnStatementType = function.getReturnStatementType(typeEvalContext);
                    PyFunction templateFunction = pyElementGenerator.createFromText(
                            LanguageLevel.forElement(psiElement), PyFunction.class,
                            "def a()->" + returnStatementType.getName() + ":\n	pass");
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
                PyKeywordArgument target = (PyKeywordArgument)referenceArguments[j];
                while (!Objects.equals(target.getKeyword(), targetName)) {
                    j++;
                    target=(PyKeywordArgument)referenceArguments[j];
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


        private boolean inferAnnotation(PsiElement element, StringBuilder stringBuilder) throws Exception {
            if (element == null) {
                throw new Exception("PsiElement is null");
            }
            IElementType elementType = element.getNode().getElementType();
            if (elementType.equals(INTEGER_LITERAL_EXPRESSION)) {
                stringBuilder.append("int");
            } else if (elementType.equals(FLOAT_LITERAL_EXPRESSION)) {
                stringBuilder.append("float");
            } else if (elementType.equals(IMAGINARY_LITERAL_EXPRESSION)) {
                stringBuilder.append("imaginary");
            } else if (elementType.equals(STRING_LITERAL_EXPRESSION)) {
                stringBuilder.append("str");
            } else if (elementType.equals(NONE_LITERAL_EXPRESSION)) {
                stringBuilder.append("None");
            } else if (elementType.equals(BOOL_LITERAL_EXPRESSION)) {
                stringBuilder.append("bool");
            } else if (element instanceof PyDictLiteralExpression) {
                PyKeyValueExpression[] elements = ((PyDictLiteralExpression) element).getElements();
                if (elements.length == 0) {
                    stringBuilder.append("dict[None]");
                    return true;
                }
                stringBuilder.append("dict[");
                inferAnnotation(elements[0].getKey(), stringBuilder);
                stringBuilder.append(":");
                inferAnnotation(elements[0].getValue(), stringBuilder);
                stringBuilder.append("]");
            } else if (element instanceof PyListLiteralExpression) {
                PyExpression[] elements = ((PyListLiteralExpression) element).getElements();
                if (elements.length == 0) {
                    stringBuilder.append("list[None]");
                    return true;
                }
                stringBuilder.append("list[");
                inferAnnotation(elements[0], stringBuilder);
                stringBuilder.append("]");
            } else if (element instanceof PySetLiteralExpression) {
                PyExpression[] elements = ((PySetLiteralExpression) element).getElements();
                if (elements.length == 0) {
                    stringBuilder.append("set[None]");
                    return true;
                }
                stringBuilder.append("set[");
                inferAnnotation(elements[0], stringBuilder);
                stringBuilder.append("]");
            } else if (elementType.equals(REFERENCE_EXPRESSION)) {
                inferReferenceAnnotation(element, stringBuilder);
            } else if (element instanceof PyCallExpression) {
                PsiElement resolve = ((PyCallExpression) element).getCallee().getReference().resolve();
                if (resolve instanceof PyClass) {
                    stringBuilder.append(((PyClass) resolve).getName());
                } else if (resolve instanceof PyFunction) {
                    String anntationStr = ((PyFunction) resolve).getReturnStatementType(typeEvalContext).getName();
                    if (resolve.getContainingFile().getName().contains("builtin")) {
                        anntationStr = typeEvalContext.getType((PyTypedElement) element).getName();
                    }
                    stringBuilder.append(anntationStr);
                }
            } else if (element instanceof PyBinaryExpression pyBinaryExpression) {
                inferAnnotation(pyBinaryExpression.getChildren()[0], stringBuilder);
            } else if (element instanceof PySubscriptionExpression pySubscriptionExpression) {
                StringBuilder temp = new StringBuilder();
                inferReferenceAnnotation(pySubscriptionExpression.getOperand(), temp);
                if (!temp.toString().contains("[")) {
                    Until.throwErrorWithPosition(pySubscriptionExpression.getOperand().getReference().resolve(),
                            "no sub type");
                }
                stringBuilder.append(temp.substring(temp.indexOf("[") + 1, temp.lastIndexOf("]")));
            } else {
                Until.throwErrorWithPosition(element, "unexcepted psiElementType");
            }
            return true;
        }

        private boolean inferReferenceAnnotation(PsiElement element, StringBuilder stringBuilder) throws Exception {
            if (element.getReference().resolve().getParent() instanceof PyForPart) {
                stringBuilder.append(typeEvalContext.getType((PyTypedElement) element).getName());
                return true;
            }
            if (Until.getAnnotationValue(element) == null) {
                applyFixElement(element.getReference().resolve(), pyElementGenerator);
            }
            if (Until.getAnnotationValue(element) == null) {
                Until.throwErrorWithPosition(element.getReference().resolve(), "infer reference type fail");
            }
            stringBuilder.append(Until.getAnnotationValue(element));
            return true;
        }
    }
}


