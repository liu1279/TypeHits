import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTargetExpression;

public class Until {
    public static String getLevelBlanks(int level) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 2 * level; i++) {
            stringBuilder.append(" ");
        }
        return stringBuilder.toString();
    }

    public static String getInsertedString(String oldString, String insertBehindString, String needInsertString, int[] bufferIndex) {
        if (bufferIndex[0] > oldString.length() - 1) {
            return null;
        }

        int insertIndex = oldString.indexOf(insertBehindString, bufferIndex[0]);
        if (insertIndex == -1) {
            return null;
        }
        insertIndex += insertBehindString.length();
        bufferIndex[0] = insertIndex + needInsertString.length();
        return oldString.substring(0, insertIndex) + needInsertString + oldString.substring(insertIndex);
    }

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
            result = ((PyTargetExpression)resolve).getAnnotationValue();
        } else if (resolve instanceof PyNamedParameter) {
            result = ((PyNamedParameter)resolve).getAnnotationValue();
        }
        return result;
    }
}

