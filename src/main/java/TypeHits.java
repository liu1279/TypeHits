import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Query;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.PyElementTypes.*;

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
                if (isIdentifier) {
                    if (element instanceof PyTargetExpression) {
                        if (((PyTargetExpression) element).getAnnotationValue() == null) {
                            holder.registerProblem(element, "No type declare of variable" + node.getText(), myQuickFix);
                        }
                    } else if (element instanceof PyNamedParameter) {
                        if (((PyNamedParameter) element).getAnnotationValue() == null) {
                            holder.registerProblem(element, "No type declare", myQuickFix);
                        }

                    } else if (element instanceof PyFunction) {
                        Query<PsiReference> search = ReferencesSearch.search(element);
                        for (PsiReference psiReference : search) {
                            System.out.println(psiReference);
                        }
                        System.out.println(search);
                        if (((PyFunction) element).getAnnotationValue() == null) {
                            holder.registerProblem(element, "No type declare", myQuickFix);
                        }
                    }
                }
                System.out.println(node + targetFlag);
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
            applyFixElement(descriptor.getPsiElement(), pyElementGenerator);
        }

        private void applyFixElement(PsiElement psiElement, PyElementGenerator pyElementGenerator) {
            if (psiElement instanceof PyTargetExpression) {
                String oldParentText = psiElement.getParent().getText();
                String targetText = psiElement.getText();
                StringBuilder annotationBuilder = new StringBuilder(":");
                if (!inferAnnotation(psiElement.getParent().getChildren()[1], annotationBuilder)){
                    return;
                }
                String newParentText = oldParentText.substring(0, targetText.length())
                        + annotationBuilder + oldParentText.substring(targetText.length());
                PyAssignmentStatement newPyAssignmentStatement = pyElementGenerator.createFromText(LanguageLevel.forElement(psiElement),
                        PyAssignmentStatement.class, newParentText);
                psiElement.getParent().replace(newPyAssignmentStatement);
            } else if (psiElement instanceof PyNamedParameter) {

            } else if (psiElement instanceof PyFunction) {

            }
        }


        private boolean inferAnnotation(PsiElement element, StringBuilder stringBuilder) {
            if (element == null) {
                return false;
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
                if (!inferAnnotation(firstKeyValue.getKey(), stringBuilder)) {
                    return false;
                }
                stringBuilder.append(":");
                if (!inferAnnotation(firstKeyValue.getValue(), stringBuilder)) {
                    return false;
                }
                stringBuilder.append("]");
            } else if (element instanceof PyListLiteralExpression) {
                stringBuilder.append("list[");
                if (!inferAnnotation(((PyListLiteralExpression) element).getElements()[0], stringBuilder)) {
                    return false;
                }
                stringBuilder.append("]");
            } else if (element instanceof PySetLiteralExpression) {
                stringBuilder.append("set[");
                if (!inferAnnotation(((PySetLiteralExpression) element).getElements()[0], stringBuilder)) {
                    return false;
                }
                stringBuilder.append("]");
            } else if (elementType.equals(REFERENCE_EXPRESSION)) {
                return inferReferenceAnnotation(element, stringBuilder);
            } else if (element instanceof PyCallExpression){
                PsiElement resolve = ((PyCallExpression) element).getCallee().getReference().resolve();
                if (resolve instanceof PyClass){
                    stringBuilder.append(((PyClass)resolve).getName());
                } else if (resolve instanceof PyFunction) {
                    stringBuilder.append(((PyFunction)resolve).getReturnStatementType(typeEvalContext).getName());
                }
            } else {
                return false;
            }

            return true;
        }

        private boolean inferReferenceAnnotation(PsiElement element, StringBuilder stringBuilder) {
            PyTargetExpression resolve = (PyTargetExpression) element.getReference().resolve();
            if (resolve.getAnnotationValue() == null) {
                applyFixElement(resolve, pyElementGenerator);
            }
            resolve = (PyTargetExpression) element.getReference().resolve();
            if (resolve.getAnnotationValue() == null) {
                return false;
            }
            stringBuilder.append(resolve.getAnnotationValue());
            return true;
        }
    }
}
