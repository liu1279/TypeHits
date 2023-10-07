import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import org.jetbrains.annotations.NotNull;

public class TypeHits extends PyInspection {
    TypeFix typeFix = new TypeFix();

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PyElementVisitor() {
            @Override
            public void visitPyElement(@NotNull PyElement element) {
                if (element instanceof PyTargetExpression) {
                    if (((PyTargetExpression) element).getAnnotationValue() == null
                            && !(element.getParent() instanceof PyForPart)
                            && !(element.getParent() instanceof PyTupleExpression)
                            && ((PyTargetExpressionImpl) element).getReference().resolve() == element) {
                        holder.registerProblem(element, "No type declare of variable " + element.getText(), typeFix);
                    }
                } else if (element instanceof PyFunction pyFunction) {
                    boolean isNeedFix = pyFunction.getAnnotation() == null;
                    for (PyParameter parameter : pyFunction.getParameterList().getParameters()) {
                        if (!parameter.getText().equals("self") && ((PyNamedParameter) parameter).getAnnotation() == null) {
                            isNeedFix = true;
                            break;
                        }
                    }
                    if (isNeedFix) {
                        holder.registerProblem(pyFunction.getNameNode().getPsi(),
                                "lose some type declare of function " + pyFunction.getName(), typeFix);
                    }
                }
            }
        };

    }
}



