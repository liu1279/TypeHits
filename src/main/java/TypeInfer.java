import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.Collections;

import static com.jetbrains.python.PyElementTypes.*;

public class TypeInfer {
    private TypeEvalContext typeEvalContext;
    private TypeFix myQuickFix;

    public TypeInfer(TypeEvalContext typeEvalContext, TypeFix myQuickFix){
        this.typeEvalContext = typeEvalContext;
        this.myQuickFix = myQuickFix;
    }

    public boolean inferAnnotation(PsiElement element, StringBuilder stringBuilder) throws Exception {
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
                if (anntationStr.equals("None")) {
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
        } else if (element instanceof PyConditionalExpression pyConditionalExpression) {
            inferAnnotation(pyConditionalExpression.getTruePart(), stringBuilder);
        }

        else {
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
            myQuickFix.applyFixElement(element.getReference().resolve());
        }
        if (Until.getAnnotationValue(element) == null) {
            Until.throwErrorWithPosition(element.getReference().resolve(), "infer reference type fail");
        }
        stringBuilder.append(Until.getAnnotationValue(element));
        return true;
    }

    public PyType getFunctionReturnType(PyFunction function) {
        return function.getReturnStatementType(typeEvalContext);
    }

    public void addImport(PsiElement element) {
        PyType jetbrainType = typeEvalContext.getType((PyTypedElement) element);
        PyTypeHintGenerationUtil.addImportsForTypeAnnotations(
                Collections.singletonList(jetbrainType), typeEvalContext, element.getContainingFile());
    }
}
