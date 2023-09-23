import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.ASTNode;
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
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.PyElementTypes.*;
import static com.jetbrains.python.inspections.PyTypeCheckerInspection.Visitor.tryPromotingType;

public class TypeHits extends PyInspection {
    private final TypeFix myQuickFix = new TypeFix();

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PyElementVisitor() {
            @Override
            public void visitPyElement(@NotNull PyElement element) {
                ASTNode node = element.getNode().getFirstChildNode();
                boolean isIdentifier = node != null && node.getElementType().toString().equals("Py:IDENTIFIER");
                String targetFlag = isIdentifier ? "  【目标】->" + node.getText() : "";
                if (element instanceof PyTargetExpression) {
                    if (((PyTargetExpression) element).getAnnotationValue() == null) {
                        holder.registerProblem(element, "No type declare of variable " + element.getText(), myQuickFix);
                    }
                } else if (element instanceof PyFunction pyFunction) {
                    boolean isNeedFix = pyFunction.getAnnotation() == null;
                    for (PyParameter parameter : pyFunction.getParameterList().getParameters()) {
                        if (((PyNamedParameter) parameter).getAnnotation() == null) {
                            isNeedFix = true;
                            break;
                        }
                    }
                    if (isNeedFix) {
                        holder.registerProblem(pyFunction.getNameNode().getPsi(),
                                "lose some type declare of function " + pyFunction.getName(), myQuickFix);
                    }
                }
                System.out.println(element + targetFlag);
            }
        };

    }

    private static class TypeFix implements LocalQuickFix {
        PyElementGenerator pyElementGenerator = null;
        TypeEvalContext typeEvalContext = null;

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
            try {
                applyFixElement(descriptor.getPsiElement(), pyElementGenerator);
            } catch (Exception e) {
                Messages.showErrorDialog(e.getMessage(), "infer type error");
                System.out.println(e.getMessage());
            }
        }

        private void applyFixElement(PsiElement psiElement, PyElementGenerator pyElementGenerator) throws Exception {
            if (psiElement instanceof PyTargetExpression) {
                String oldParentText = psiElement.getParent().getText();
                String targetText = psiElement.getText();
                StringBuilder annotationBuilder = new StringBuilder(":");

                inferAnnotation(psiElement.getParent().getChildren()[1], annotationBuilder);

                String newParentText = oldParentText.substring(0, targetText.length())
                        + annotationBuilder + oldParentText.substring(targetText.length());
                PyAssignmentStatement newPyAssignmentStatement = pyElementGenerator.createFromText(LanguageLevel.forElement(psiElement),
                        PyAssignmentStatement.class, newParentText);
                psiElement.getParent().replace(newPyAssignmentStatement);
            } else if (psiElement.getParent() instanceof PyFunction
                    || psiElement instanceof PyNamedParameter) {

                PsiElement function = psiElement.getParent();
                if (psiElement instanceof PyNamedParameter) {
                    function = psiElement.getParent().getParent();
                }

                String functionText = function.getText();
                int[] bufferIndex = {0};
                bufferIndex[0] = functionText.indexOf("(");

                PyParameter[] parameters = ((PyFunction) function).getParameterList().getParameters();
                PsiReference psiReference = ReferencesSearch.search(function).findFirst();
                if (psiReference != null) {
                    PyCallExpression pyCallExpression = (PyCallExpression) (psiReference.getElement().getParent());
                    PyExpression[] referenceArguments = pyCallExpression.getArgumentList().getArguments();
                    for (int i = 0; i < referenceArguments.length; i++) {
                        StringBuilder annotationBuilder = new StringBuilder(":");
                        inferAnnotation(referenceArguments[i], annotationBuilder);
                        if (((PyNamedParameter) parameters[i]).getAnnotation() != null) {
                            bufferIndex[0] += parameters[i].getText().length();
                            continue;
                        }
                        functionText = Until.getInsertedString(functionText, parameters[i].getText(),
                                annotationBuilder.toString(), bufferIndex);

                    }
                }
                PyType returnStatementType = ((PyFunction) function).getReturnStatementType(typeEvalContext);
                if (((PyFunction) function).getAnnotation() == null) {
                    functionText = Until.getInsertedString(functionText, ")", "->" + returnStatementType.getName(), bufferIndex);
                }

                PyFunctionImpl newFunction = pyElementGenerator.createFromText(LanguageLevel.forElement(psiElement),
                        PyFunctionImpl.class, functionText);
                PyParameter[] newParameters = newFunction.getParameterList().getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    if (((PyNamedParameter) parameters[i]).getAnnotation() == null) {
                        parameters[i].replace(newParameters[i]);
                    }
                }
                if (((PyFunction) function).getAnnotation() == null) {
                    ASTNode anchorNode = function.getNode().findChildByType(PyTokenTypes.COLON);
                    function.getNode().addChild(newFunction.getAnnotation().getNode(), anchorNode);
                }
            }
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
                PyKeyValueExpression firstKeyValue = ((PyDictLiteralExpression) element).getElements()[0];
                stringBuilder.append("dict[");
                inferAnnotation(firstKeyValue.getKey(), stringBuilder);
                stringBuilder.append(":");
                inferAnnotation(firstKeyValue.getValue(), stringBuilder);
                stringBuilder.append("]");
            } else if (element instanceof PyListLiteralExpression) {
                stringBuilder.append("list[");
                inferAnnotation(((PyListLiteralExpression) element).getElements()[0], stringBuilder);
                stringBuilder.append("]");
            } else if (element instanceof PySetLiteralExpression) {
                stringBuilder.append("set[");
                inferAnnotation(((PySetLiteralExpression) element).getElements()[0], stringBuilder);
                stringBuilder.append("]");
            } else if (elementType.equals(REFERENCE_EXPRESSION)) {
                inferReferenceAnnotation(element, stringBuilder);
            } else if (element instanceof PyCallExpression) {
                PsiElement resolve = ((PyCallExpression) element).getCallee().getReference().resolve();
                if (resolve instanceof PyClass) {
                    stringBuilder.append(((PyClass) resolve).getName());
                } else if (resolve instanceof PyFunction) {
                    PyTargetExpression node = (PyTargetExpression) element.getParent().getChildren()[0];
                    PyExpression assignedValue = node.findAssignedValue();
                    PyType type = typeEvalContext.getType(node);
                    PyType inferType = tryPromotingType(assignedValue, type, typeEvalContext);
                    if (inferType == null) {
                        Until.throwErrorWithPosition(resolve, "infer returnType error");
                    }
                    stringBuilder.append(inferType.getName());
                }
            } else if (element instanceof PyBinaryExpression pyBinaryExpression) {
                inferAnnotation(pyBinaryExpression.getChildren()[0], stringBuilder);
            } else if (element instanceof PySubscriptionExpression pySubscriptionExpression) {
                StringBuilder temp = new StringBuilder();
                inferReferenceAnnotation(pySubscriptionExpression.getOperand(), temp);
                stringBuilder.append(temp.substring(temp.indexOf("[") + 1, temp.lastIndexOf("]")));
            } else {
                Until.throwErrorWithPosition(element, "unexcepted psiElementType");
            }
            return true;
        }

        private boolean inferReferenceAnnotation(PsiElement element, StringBuilder stringBuilder) throws Exception {
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

