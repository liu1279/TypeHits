import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import org.jetbrains.annotations.NotNull;

public class TypeHits extends PyInspection {
    private final ReplaceWithEqualsQuickFix myQuickFix = new ReplaceWithEqualsQuickFix();
    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
//        return super.buildVisitor(holder, isOnTheFly);
        return new PyElementVisitor() {
            @Override
            public void visitPyElement(@NotNull PyElement element) {
//                super.visitPyElement(node);
//                if (element.getNode().getElementType().toString().equals("IDENTIFIER")) {
//                    System.out.println(element);
//                }
                ASTNode node = element.getNode();
                RePrint(node, 0);
            }

            private void RePrint(ASTNode node, int level) {
                if (node == null) {
                    return;
                }
                boolean isIdentifier = node.getElementType().toString().equals("Py:IDENTIFIER");
                String targetFlag = isIdentifier?"  【目标】->"+node.getText():"";
                if (isIdentifier) {
                    String parentType = node.getTreeParent().getElementType().toString();
                    if (parentType.equals("Py:NAMED_PARAMETER") || parentType.equals("Py:TARGET_EXPRESSION")) {
                        ASTNode treeNext = parentType.equals("Py:NAMED_PARAMETER")? node.getTreeNext() : node.getTreeParent().getTreeNext();
                        while (treeNext!=null && treeNext.toString().equals("PsiWhiteSpace")) {
                            treeNext = treeNext.getTreeNext();
                        }
                        if (treeNext == null || !treeNext.getElementType().toString().equals("Py:ANNOTATION")) {
                            holder.registerProblem(node.getPsi(), "No type declare", myQuickFix);
                        }
                    } else if (parentType.equals("Py:FUNCTION_DECLARATION")) {
                        ASTNode treeNext = node.getTreeNext();
                        while (treeNext!=null && (treeNext.toString().equals("PsiWhiteSpace") || treeNext.getElementType().toString().equals("Py:PARAMETER_LIST"))) {
                            treeNext = treeNext.getTreeNext();
                        }
                        if (treeNext == null || !treeNext.getElementType().toString().equals("Py:ANNOTATION")) {
                            holder.registerProblem(node.getPsi(), "No type declare", myQuickFix);
                        }
                    }
                }
                System.out.println(getLevelBlanks(level) + node + targetFlag);
                ASTNode[] children = node.getChildren(null);
                for (ASTNode child : children) {
                    RePrint(child, level + 1);
                }
            }
            private static String getLevelBlanks(int level) {
                StringBuilder stringBuilder = new StringBuilder();
                for (int i = 0; i < 2 * level; i++) {
                    stringBuilder.append(" ");
                }
                return stringBuilder.toString();
            }
        };
    }

    private static class ReplaceWithEqualsQuickFix implements LocalQuickFix {


        @Override
        public @IntentionFamilyName @NotNull String getFamilyName() {
            return null;
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {

        }
    }
}
