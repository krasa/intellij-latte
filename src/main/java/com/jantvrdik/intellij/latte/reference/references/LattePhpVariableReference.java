package com.jantvrdik.intellij.latte.reference.references;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.jantvrdik.intellij.latte.config.LatteFileConfiguration;
import com.jantvrdik.intellij.latte.php.NettePhpType;
import com.jantvrdik.intellij.latte.psi.*;
import com.jantvrdik.intellij.latte.psi.elements.BaseLattePhpElement;
import com.jantvrdik.intellij.latte.php.LattePhpUtil;
import com.jantvrdik.intellij.latte.utils.LattePhpVariableDefinition;
import com.jantvrdik.intellij.latte.utils.LatteUtil;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.*;

import java.util.*;

public class LattePhpVariableReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

    private final String variableName;

    public LattePhpVariableReference(@NotNull LattePhpVariable element, TextRange textRange) {
        super(element, textRange);
        variableName = element.getVariableName();
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        if (!(getElement() instanceof LattePhpVariable || getElement().getContainingFile().getVirtualFile() == null)) {
            return new ResolveResult[0];
        }
        LattePhpVariable variable = (LattePhpVariable) getElement();

        Project project = getElement().getProject();
        List<ResolveResult> results = new ArrayList<>();
        for (XmlAttributeValue attributeValue : LatteFileConfiguration.getAllMatchedXmlAttributeValues(project, "variable", variableName)) {
            results.add(new PsiElementResolveResult(attributeValue));
        }

        NettePhpType fields = LatteUtil.findFirstLatteTemplateType(getElement().getContainingFile());
        String name = ((BaseLattePhpElement) getElement()).getPhpElementName();
        if (fields != null) {
            for (PhpClass phpClass : fields.getPhpClasses(project)) {
                for (Field field : phpClass.getFields()) {
                    if (!field.isConstant() && field.getName().equals(name)) {
                        results.add(new PsiElementResolveResult(field));
                    }
                }
            }
        }

        final List<LattePhpVariableDefinition> variables = LatteUtil.getVariableDefinition(variable);
        for (LattePhpVariableDefinition variableDefinition : variables) {
            results.add(new PsiElementResolveResult(variableDefinition.getElement()));
        }
        return results.toArray(new ResolveResult[0]);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }

    @Override
    public boolean isSoft() {
        return true;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }

    @NotNull
    @Override
    public String getCanonicalText() {
        return LattePhpUtil.normalizePhpVariable(getElement().getText());
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newName) {
        if (getElement() instanceof LattePhpVariable) {
            ((LattePhpVariable) getElement()).setName(newName);
        }
        return getElement();
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        if (element instanceof LattePhpVariable) {
            return !((LattePhpVariable) element).isDefinition() && ((LattePhpVariable) element).getVariableName().equals(variableName);
        }

        PsiElement currentElement = element;
        if (element instanceof PomTargetPsiElement) {
            currentElement = LatteFileConfiguration.getPsiElementFromDomTarget("variable", element);
            if (currentElement == null) {
                currentElement = element;
            }
        }
        if (currentElement instanceof XmlAttributeValue && LatteFileConfiguration.hasParentXmlTagName(currentElement, "variable")) {
            return ((XmlAttributeValue) currentElement).getValue().equals(variableName);
        }
        return super.isReferenceTo(element);
    }
}