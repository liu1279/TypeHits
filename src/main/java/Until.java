import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import org.jetbrains.annotations.NotNull;

public class Until {
    public static TokenSet calculateTokenSet = TokenSet.create(PyTokenTypes.PLUS,
            PyTokenTypes.MINUS, PyTokenTypes.MULT, PyTokenTypes.AT, PyTokenTypes.FLOORDIV, PyTokenTypes.DIV,
            PyTokenTypes.PERC, PyTokenTypes.EXP);

    public static void throwErrorWithPosition(PsiElement element, String message) throws Exception {
        int startOffset = element.getNode().getStartOffset();
        String text = element.getContainingFile().getText();
        int line = 1;
        int column = 1;
        for (int i = 0; i < startOffset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        throw new Exception(message + "\nnear element: " + element.getText() + "\nline: " + line + "\ncolumn: " + column);
    }

    public static boolean notNeedAnnotation(PsiElement element) {
        return element == null
                || (element.getParent() instanceof PyForPart)
                || (element.getParent() instanceof PyTupleExpression)
                || (element.getParent() instanceof PyWithItem)
                || (element.getParent() instanceof PyComprehensionElement)
                || (element.getParent() instanceof PyStarExpression);
    }

    @NotNull
    public static PsiElement getResolve(PsiElement element) throws Exception {
        PsiReference reference = element.getReference();
        if (reference == null) {
            Until.throwErrorWithPosition(element, " there is no reference of element:" + element.getText());
        }
        PsiElement resolve = reference.resolve();
        if (resolve == null) {
            Until.throwErrorWithPosition(element, " there is no resolve of reference:" + reference.getElement().getText());
        }
        return resolve;
    }

    public static boolean isCalculateType(IElementType elementType) {
        return calculateTokenSet.contains(elementType);
    }
}

