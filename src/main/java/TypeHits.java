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

                boolean isIdentifier = isTypeAs(node, TreeType.IDENTIFIER);
                String targetFlag = isIdentifier ? "  【目标】->" + node.getText() : "";
                if (isIdentifier) {
                    ASTNode treeParent = node.getTreeParent();
                    if (isTypeAs(treeParent, TreeType.TARGET_EXPRESSION)) {
                        ASTNode treeNext = treeParent.getTreeNext();
                        while (isTypeAs(treeNext, TreeType.PSI_WHITE_SPACE)) {
                            treeNext = treeNext.getTreeNext();
                        }
                        if (!isTypeAs(treeNext, TreeType.ANNOTATION)) {
                            holder.registerProblem(node.getPsi(), "No type declare", myQuickFix);
                        }
                    } else if (isTypeAs(treeParent, TreeType.NAMED_PARAMETER)) {
                        ASTNode treeNext = node.getTreeNext();
                        while (isTypeAs(treeNext, TreeType.PSI_WHITE_SPACE)) {
                            treeNext = treeNext.getTreeNext();
                        }
                        if (!isTypeAs(treeNext, TreeType.ANNOTATION)) {
                            holder.registerProblem(node.getPsi(), "No type declare", myQuickFix);
                        }

                    } else if (isTypeAs(treeParent, TreeType.FUNCTION_DECLARATION)) {
                        ASTNode treeNext = node.getTreeNext();
//                        Query<PsiReference> search1 = ReferencesSearch.search(node.getPsi());
                        Query<PsiReference> search = ReferencesSearch.search(treeParent.getPsi());
                        for (PsiReference psiReference : search) {
                            System.out.println(psiReference);
                        }
                        System.out.println(search);
                        while (isTypeAs(treeNext, TreeType.PSI_WHITE_SPACE) || isTypeAs(treeNext, TreeType.PARAMETER_LIST)) {
                            treeNext = treeNext.getTreeNext();
                        }
                        if (!isTypeAs(treeNext, TreeType.ANNOTATION)) {
                            holder.registerProblem(node.getPsi(), "No type declare", myQuickFix);
                        }
                    }
                }
                System.out.println(node + targetFlag);
            }
        };

    }

    private boolean isTypeAs(ASTNode treeNext, String targetTreeType) {
        return treeNext != null && treeNext.getElementType().toString().equals(targetTreeType);
    }

    private static class TypeFix implements LocalQuickFix {

        @NotNull
        @Override
        public String getName() {
            return InspectionBundle.message("inspection.comparing.string.references.use.quickfix");
        }


        @Override
        public @IntentionFamilyName
        @NotNull String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement psiElement = descriptor.getPsiElement().getParent();
            if (psiElement instanceof PyTargetExpression) {
                PyElementGenerator pyElementGenerator = PyElementGenerator.getInstance(project);
                String oldParentText = psiElement.getParent().getText();
                String targetText = psiElement.getText();
                StringBuilder annotationBuilder = new StringBuilder(":");
                if (!inferAnnotation(psiElement.getParent().getChildren()[1], annotationBuilder)){
                    return;
                }
                String newParentText = oldParentText.substring(0, targetText.length()) + annotationBuilder.toString() + oldParentText.substring(targetText.length());
                PyAssignmentStatement newPyAssignmentStatement = pyElementGenerator.createFromText(LanguageLevel.forElement(psiElement), PyAssignmentStatement.class, newParentText);
                psiElement.getParent().replace(newPyAssignmentStatement);
            } else if (psiElement instanceof PyNamedParameter) {

            } else if (psiElement instanceof PyFunction) {

            }
        }

        /*
        PyElementType INTEGER_LITERAL_EXPRESSION = new PyElementType("INTEGER_LITERAL_EXPRESSION", node -> new PyNumericLiteralExpressionImpl(node));
          PyElementType FLOAT_LITERAL_EXPRESSION = new PyElementType("FLOAT_LITERAL_EXPRESSION", node -> new PyNumericLiteralExpressionImpl(node));
          PyElementType IMAGINARY_LITERAL_EXPRESSION = new PyElementType("IMAGINARY_LITERAL_EXPRESSION", node -> new PyNumericLiteralExpressionImpl(node));
          PyElementType STRING_LITERAL_EXPRESSION = new PyElementType("STRING_LITERAL_EXPRESSION", node -> new PyStringLiteralExpressionImpl(node));
          PyElementType NONE_LITERAL_EXPRESSION = new PyElementType("NONE_LITERAL_EXPRESSION", node -> new PyNoneLiteralExpressionImpl(node));
          PyElementType BOOL_LITERAL_EXPRESSION = new PyElementType("BOOL_LITERAL_EXPRESSION", node -> new PyBoolLiteralExpressionImpl(node));

          PyElementType LIST_LITERAL_EXPRESSION = new PyElementType("LIST_LITERAL_EXPRESSION", node -> new PyListLiteralExpressionImpl(node));
          PyElementType DICT_LITERAL_EXPRESSION = new PyElementType("DICT_LITERAL_EXPRESSION", node -> new PyDictLiteralExpressionImpl(node));
          PyElementType SET_LITERAL_EXPRESSION = new PyElementType("SET_LITERAL_EXPRESSION", node -> new PySetLiteralExpressionImpl(node));
         */
        private boolean inferAnnotation(PsiElement element, StringBuilder stringBuilder) {
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
            } else if (elementType.equals(DICT_LITERAL_EXPRESSION)) {
                stringBuilder.append("dict[");
                if (!inferAnnotation(element.getChildren()[0].getChildren()[0], stringBuilder)) {
                    return false;
                }
                stringBuilder.append(":");
                if (!inferAnnotation(element.getChildren()[0].getChildren()[1], stringBuilder)) {
                    return false;
                }
                stringBuilder.append("]");
            } else if (elementType.equals(LIST_LITERAL_EXPRESSION)) {
                stringBuilder.append("list[");
                if (!inferAnnotation(element.getChildren()[0], stringBuilder)) {
                    return false;
                }
                stringBuilder.append("]");
            } else if (elementType.equals(SET_LITERAL_EXPRESSION)) {
                stringBuilder.append("set[");
                if (!inferAnnotation(element.getChildren()[0], stringBuilder)) {
                    return false;
                }
                stringBuilder.append("]");
            } else if (elementType.equals(REFERENCE_EXPRESSION)) {
                return inferReference(element, stringBuilder);
            } else {
                return false;
            }

            return true;
        }

        private boolean inferReference(PsiElement element, StringBuilder stringBuilder) {
            PsiReference reference = element.getReference();
            if (reference == null) {
                return false;
            }
            PsiElement resolve = reference.resolve();
            if (resolve == null) {
                return false;
            }
            PsiElement parent = resolve.getParent();
            if (parent == null) {
                return false;
            }
            PsiElement[] children = parent.getChildren();
            if (children.length < 2) {
                return false;
            }
            stringBuilder.append(children[1].getText().substring(1));
            return true;
        }
    }
}
