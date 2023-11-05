import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.Collections;

import static com.jetbrains.python.PyElementTypes.*;

public class TypeInfer {
    private final TypeEvalContext typeEvalContext;
    private final TypeFix myQuickFix;

    public TypeInfer(TypeEvalContext typeEvalContext, TypeFix myQuickFix) {
        this.typeEvalContext = typeEvalContext;
        this.myQuickFix = myQuickFix;
    }

    public String getInferedAnnotation(PsiElement element) throws Exception {
        String inferedAnnotation = null;
        if (element == null) {
            throw new Exception("PsiElement is null");
        }
        IElementType elementType = element.getNode().getElementType();
        if (elementType.equals(INTEGER_LITERAL_EXPRESSION)) {
            inferedAnnotation = "int";
        } else if (elementType.equals(FLOAT_LITERAL_EXPRESSION)) {
            inferedAnnotation = "float";
        } else if (elementType.equals(IMAGINARY_LITERAL_EXPRESSION)) {
            inferedAnnotation = "imaginary";
        } else if (elementType.equals(STRING_LITERAL_EXPRESSION)) {
            inferedAnnotation = "str";
        } else if (elementType.equals(NONE_LITERAL_EXPRESSION)) {
            inferedAnnotation = "None";
        } else if (elementType.equals(BOOL_LITERAL_EXPRESSION)) {
            inferedAnnotation = "bool";
        } else if (element instanceof PyDictLiteralExpression) {
            PyKeyValueExpression[] elements = ((PyDictLiteralExpression) element).getElements();
            if (elements.length == 0) {
                return "dict[None]";
            }
            inferedAnnotation = "dict[" + getInferedAnnotation(elements[0].getKey()) + ":" + getInferedAnnotation(elements[0].getValue()) + "]";
        } else if (element instanceof PyListLiteralExpression) {
            PyExpression[] elements = ((PyListLiteralExpression) element).getElements();
            if (elements.length == 0) {
                return "list[None]";
            }
            inferedAnnotation = "list[" + getInferedAnnotation(elements[0]) + "]";
        } else if (element instanceof PySetLiteralExpression) {
            PyExpression[] elements = ((PySetLiteralExpression) element).getElements();
            if (elements.length == 0) {
                return "set[None]";
            }
            inferedAnnotation = "set[" + getInferedAnnotation(elements[0]) + "]";
        } else if (elementType.equals(REFERENCE_EXPRESSION)) {
            inferedAnnotation = getReferenceAnnotation(element);
        } else if (element instanceof PyCallExpression) {
            PsiElement resolve = ((PyCallExpression) element).getCallee().getReference().resolve();
            if (resolve instanceof PyClass) {
                inferedAnnotation = ((PyClass) resolve).getName();
            } else if (resolve instanceof PyFunction) {
                String anntationStr = ((PyFunction) resolve).getReturnStatementType(typeEvalContext).getName();
                if ("None".equals(anntationStr)) {
                    anntationStr = typeEvalContext.getType((PyTypedElement) element).getName();
                }
                inferedAnnotation = anntationStr;
            }
        } else if (element instanceof PyBinaryExpression pyBinaryExpression) {
            inferedAnnotation = getInferedAnnotation(pyBinaryExpression.getChildren()[0]);
        } else if (element instanceof PySubscriptionExpression pySubscriptionExpression) {
            String temp = getReferenceAnnotation(pySubscriptionExpression.getOperand());
            if (!temp.contains("[")) {
                Until.throwErrorWithPosition(pySubscriptionExpression.getOperand().getReference().resolve(),
                        "no sub type");
            }
            inferedAnnotation = temp.substring(temp.indexOf("[") + 1, temp.lastIndexOf("]"));
        } else if (element instanceof PyConditionalExpression pyConditionalExpression) {
            inferedAnnotation = getInferedAnnotation(pyConditionalExpression.getTruePart());
        } else if (element instanceof PyPrefixExpression) {
            inferedAnnotation = "bool";
        } else {
            Until.throwErrorWithPosition(element, "unexcepted psiElementType");
        }
        return inferedAnnotation;
    }

    private String getReferenceAnnotation(PsiElement element) throws Exception {
        PsiElement resolve = element.getReference().resolve();
        if (Until.notNeedAnnotation(resolve)) {
            return typeEvalContext.getType((PyTypedElement) resolve).getName();
        }
        String annotationValue = Until.getAnnotationValue(element);
        if (annotationValue == null) {
            annotationValue = myQuickFix.applyFixElement(resolve);
        }
        if (annotationValue == null) {
            Until.throwErrorWithPosition(resolve, "infer reference type fail");
        }
        return annotationValue;
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
