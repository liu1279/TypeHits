import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            inferedAnnotation = getCallAnnotation(element);
        } else if (element instanceof PyBinaryExpression pyBinaryExpression) {
            if (Until.isCalculateType(((PyBinaryExpression) element).getOperator())) {
                inferedAnnotation = getInferedAnnotation(pyBinaryExpression.getChildren()[0]);
            } else {
                inferedAnnotation = "bool";
            }
        } else if (element instanceof PySubscriptionExpression pySubscriptionExpression) {
            String temp = getReferenceAnnotation(pySubscriptionExpression.getOperand());
            if (!temp.contains("[")) {
                Until.throwErrorWithPosition(Until.getResolve(pySubscriptionExpression.getOperand()), "no sub type");
            }
            inferedAnnotation = temp.substring(temp.indexOf("[") + 1, temp.lastIndexOf("]"));
        } else if (element instanceof PyConditionalExpression pyConditionalExpression) {
            inferedAnnotation = getInferedAnnotation(pyConditionalExpression.getTruePart());
        } else if (element instanceof PyPrefixExpression) {
            inferedAnnotation = "bool";
        } else {
            Until.throwErrorWithPosition(element, "unexcepted psiElementType: " + elementType);
        }
        return inferedAnnotation;
    }

    @Nullable
    private String getCallAnnotation(PsiElement element) throws Exception {
        String inferedAnnotation = null;
        PyExpression callee = ((PyCallExpression) element).getCallee();
        if (callee == null) {
            Until.throwErrorWithPosition(element, "no callee of PyCallExpression: " + ((PyCallExpression) element).getName());
            return null;
        }
        PsiElement resolve = Until.getResolve(callee);
        if (resolve instanceof PyClass) {
            inferedAnnotation = ((PyClass) resolve).getName();
        } else if (resolve instanceof PyFunction) {
            String anntationStr = null;
            PyType returnStatementType = ((PyFunction) resolve).getReturnStatementType(typeEvalContext);
            if (returnStatementType != null) {
                anntationStr = returnStatementType.getName();
            }
            if ("None".equals(anntationStr)) {
                PyType type = typeEvalContext.getType((PyTypedElement) element);
                if (type != null) {
                    anntationStr = type.getName();
                }
            }
            inferedAnnotation = anntationStr;
        }
        return inferedAnnotation;
    }

    @NotNull
    private String getReferenceAnnotation(PsiElement element) throws Exception {
        if (element instanceof PyCallExpression pyCallExpression) {
            String callAnnotation = getCallAnnotation(pyCallExpression);
            if (callAnnotation == null) {
                Until.throwErrorWithPosition(pyCallExpression,
                        " there is no annotation of pyCallExpression:" + pyCallExpression.getName());
            }
            return callAnnotation;
        }
        PsiElement resolve = Until.getResolve(element);
        if (Until.notNeedAnnotation(resolve)) {
            PyType type = typeEvalContext.getType((PyTypedElement) resolve);
            if (type == null) {
                Until.throwErrorWithPosition(element, " there is no type of resolve:" + resolve.getText());
            }
            return type.getName();
        }
        String annotationValue = getAnnotation(resolve);
        if (annotationValue == null) {
            annotationValue = myQuickFix.applyFixElement(resolve);
        }
        if (annotationValue == null) {
            Until.throwErrorWithPosition(resolve, "infer reference type fail");
        }
        return annotationValue;
    }

    private String getAnnotation(PsiElement element) {
        if (element instanceof PyAnnotationOwner pyAnnotationOwner) {
            return pyAnnotationOwner.getAnnotationValue();
        }
        return null;
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
