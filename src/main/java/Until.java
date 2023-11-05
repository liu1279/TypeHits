import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;

public class Until {
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

    public static String getAnnotationValue(PsiElement element) {
        PsiElement resolve = element.getReference().resolve();
        String result = null;
        if (resolve instanceof PyTargetExpression) {
            result = ((PyTargetExpression) resolve).getAnnotationValue();
        } else if (resolve instanceof PyNamedParameter) {
            result = ((PyNamedParameter) resolve).getAnnotationValue();
        }
        return result;
    }

    public static boolean notNeedAnnotation(PsiElement element) {
        return (element.getParent() instanceof PyForPart)
                || (element.getParent() instanceof PyTupleExpression)
                || (element.getParent() instanceof PyWithItem)
                || (element.getParent() instanceof PyComprehensionElement)
                || ((PyTargetExpressionImpl) element).getReference().resolve() == element;
    }
}

