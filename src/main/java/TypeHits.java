import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

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
                            && !(element.getParent() instanceof PyWithItem)
                            && !(element.getParent() instanceof PyComprehensionElement)
                            && ((PyTargetExpressionImpl) element).getReference().resolve() == element) {
                        holder.registerProblem(element, "lose type declare of variable " + element.getText(), typeFix);
                    }
                } else if (element instanceof PyFunction pyFunction) {
                    ArrayList<String> loseTypeParameters = new ArrayList<>();
                    for (PyParameter parameter : pyFunction.getParameterList().getParameters()) {
                        if (!parameter.getText().equals("self") && ((PyNamedParameter) parameter).getAnnotation() == null) {
                            loseTypeParameters.add(parameter.getName());
                        }
                    }
                    if (!loseTypeParameters.isEmpty() || pyFunction.getAnnotation() == null) {
                        String returnInfo = pyFunction.getAnnotation() == null ? "return value " : "";
                        String parametersInfo = !loseTypeParameters.isEmpty() ? "parameters " + loseTypeParameters + ", " : "";
                        holder.registerProblem(pyFunction.getNameNode().getPsi(),
                                "lose type declare of " + parametersInfo + returnInfo + "in function " + pyFunction.getName(), typeFix);
                    }
                }
            }
        };

    }
}



