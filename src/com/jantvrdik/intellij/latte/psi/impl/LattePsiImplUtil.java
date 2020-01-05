package com.jantvrdik.intellij.latte.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.jantvrdik.intellij.latte.config.LatteConfiguration;
import com.jantvrdik.intellij.latte.config.LatteDefaultVariable;
import com.jantvrdik.intellij.latte.psi.*;
import com.jantvrdik.intellij.latte.utils.LattePhpType;
import com.jantvrdik.intellij.latte.utils.LattePhpUtil;
import com.jantvrdik.intellij.latte.utils.LatteUtil;
import com.jantvrdik.intellij.latte.utils.PsiPositionedElement;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.jantvrdik.intellij.latte.psi.LatteTypes.*;

public class LattePsiImplUtil {
	@NotNull
	public static String getMacroName(LatteMacroTag element) {
		ASTNode elementNode = element.getNode();
		ASTNode nameNode = elementNode.findChildByType(T_MACRO_NAME);
		if (nameNode != null) {
			return nameNode.getText();
		}

		nameNode = elementNode.findChildByType(T_MACRO_SHORTNAME);
		if (nameNode != null) {
			return nameNode.getText();
		}
		LatteMacroContent content = element.getMacroContent();
		if (content == null || element instanceof LatteMacroCloseTag) {
			return "";
		}
		return "=";
	}

	@Nullable
	public static LattePhpContent getFirstPhpContent(@NotNull LatteMacroContent macroContent) {
		List<LattePhpContent> phpContents = macroContent.getPhpContentList();
		return phpContents.stream().findFirst().isPresent() ? phpContents.stream().findFirst().get() : null;
	}

	public static String getVariableName(@NotNull PsiElement element) {
		PsiElement found = findFirstChildWithType(element, T_MACRO_ARGS_VAR);
		return found != null ? LattePhpUtil.normalizePhpVariable(found.getText()) : null;
	}

	public static String getConstantName(@NotNull PsiElement element) {
		return getPropertyName(element);
	}

	public static String getMethodName(@NotNull PsiElement element) {
		PsiElement found = findFirstChildWithType(element, T_PHP_METHOD);
		return found != null ? found.getText() : null;
	}

	public static String getPropertyName(@NotNull PsiElement element) {
		PsiElement found = findFirstChildWithType(element, T_PHP_IDENTIFIER);
		return found != null ? found.getText() : null;
	}

	public static String getClassName(@NotNull PsiElement element) {
		PsiElement found = findFirstChildWithType(element, T_PHP_VAR_TYPE);
		return found != null ? found.getText() : null;
	}

	@Nullable
	public static LattePhpType detectVariableTypeFromTemplateType(@NotNull PsiElement element, @NotNull String variableName)
	{
		if (!(element.getContainingFile() instanceof LatteFile)) {
			return null;
		}

		LattePhpType templateType = LatteUtil.findFirstLatteTemplateType((LatteFile) element.getContainingFile());
		if (templateType == null) {
			return null;
		}

		Collection<PhpClass> classes = templateType.getPhpClasses(element.getProject());
		if (classes == null) {
			return null;
		}
		for (PhpClass phpClass : classes) {
			for (Field field : phpClass.getFields()) {
				if (!field.isConstant() && field.getModifier().isPublic() && variableName.equals(field.getName())) {
					return new LattePhpType(field.getName(), field.getType().toString(), field.getType().isNullable());
				}
			}
		}
		return null;
	}

	private static LattePhpType detectVariableType(@NotNull PsiElement element, @NotNull String variableName)
	{
		LatteDefaultVariable defaultVariable = LatteConfiguration.INSTANCE.getVariable(element.getProject(), variableName);
		if (defaultVariable != null) {
			return defaultVariable.type;
		}

		LattePhpType templateType = detectVariableTypeFromTemplateType(element, variableName);
		if (templateType != null) {
			return templateType;
		}

		List<PsiPositionedElement> all = LatteUtil.findVariablesInFileBeforeElement(element, element.getContainingFile().getOriginalFile().getVirtualFile(), variableName);
		List<PsiPositionedElement> definitions = all.stream().filter(
				psiPositionedElement -> psiPositionedElement.getElement() instanceof LattePhpVariable
						&& ((LattePhpVariable) psiPositionedElement.getElement()).isDefinition()
		).collect(Collectors.toList());

		for (PsiPositionedElement positionedElement : definitions) {
			if (!(positionedElement.getElement() instanceof LattePhpVariable)) {
				continue;
			}

			if (isVarTypeDefinition((LattePhpVariable) positionedElement.getElement())) {
				String prevPhpType = findPrevPhpType(positionedElement.getElement());
				boolean nullable = false;
				List<String> types = new ArrayList<String>();
				for (String part : prevPhpType.split(Pattern.quote("|"))) {
					if (part.equals("null")) {
						nullable = true;
						continue;
					}
					types.add(part);
				}
				return new LattePhpType(types.toArray(new String[types.size()]), nullable);
			}
		}
		return new LattePhpType("mixed", false);
	}

	private static String findPrevPhpType(PsiElement element)
	{
		return findPrevPhpType(element, "");
	}

	private static String findPrevPhpType(PsiElement element, String phpType)
	{
		PsiElement prevElement = PsiTreeUtil.prevLeaf(element, true);
		if (prevElement == null || prevElement.getNode().getElementType() == T_MACRO_NAME) {
			return phpType;
		}

		String text = prevElement.getText();
		if (text.trim().length() == 0) {
			return findPrevPhpType(prevElement, phpType);
		}

		return findPrevPhpType(prevElement, text + phpType);
	}

	public static @NotNull LattePhpType getPhpType(@NotNull PsiElement element) {
		PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(element);
		if (prev == null || (prev.getNode().getElementType() != T_PHP_DOUBLE_COLON && prev.getNode().getElementType() != T_PHP_OBJECT_OPERATOR)) {
			if (element instanceof LattePhpVariable) {
				return detectVariableType(element, ((LattePhpVariable) element).getVariableName());
			}
			return new LattePhpType("mixed", false);
		}

		PsiElement prevElement;
		if (prev.getParent().getNode().getElementType() == PHP_FOREACH) {
			prevElement = PsiTreeUtil.skipWhitespacesBackward(prev.getParent());
		} else {
			prevElement = PsiTreeUtil.skipWhitespacesBackward(prev);
		}

		if (prevElement != null && prevElement.getText().equals(")")) {
			PsiElement beforeBraces = PsiTreeUtil.skipWhitespacesBackward(prevElement);
			if (beforeBraces instanceof LattePhpMethodArgs) {
				beforeBraces = PsiTreeUtil.skipWhitespacesBackward(beforeBraces);
			}

			if (beforeBraces != null && beforeBraces.getText().equals("(")) {
				prevElement = PsiTreeUtil.skipWhitespacesBackward(beforeBraces);
			}
		}

		LattePhpType type = null;
		if (prevElement instanceof LattePhpStaticVariable) {
			type = ((LattePhpStaticVariable) prevElement).getPropertyType();
		} else if (prevElement instanceof LattePhpClass) {
			type = ((LattePhpClass) prevElement).getPhpType();
		} else if (prevElement instanceof LattePhpMethod) {
			type = ((LattePhpMethod) prevElement).getReturnType();
		} else if (prevElement instanceof LattePhpProperty) {
			type = ((LattePhpProperty) prevElement).getPropertyType();
		} else if (prevElement instanceof LattePhpConstant) {
			type = ((LattePhpConstant) prevElement).getConstantType();
		} else if (prevElement instanceof LattePhpVariable) {
			type = ((LattePhpVariable) prevElement).getPhpType();
		}
		return type != null ? type : new LattePhpType("mixed", false);
	}

	public static boolean isStatic(@NotNull PsiElement element) {
		PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(element);
		return prev != null && prev.getNode().getElementType() == T_PHP_DOUBLE_COLON;
	}

	public static boolean isFunction(@NotNull PsiElement element) {
		PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(element);
		return prev != null && prev.getNode().getElementType() != T_PHP_DOUBLE_COLON && prev.getNode().getElementType() != T_PHP_OBJECT_OPERATOR;
	}

	public static LattePhpType getReturnType(@NotNull LattePhpMethod element) {
		return getMethodType(element.getProject(), element.getPhpType(), element.getMethodName());
	}

	public static LattePhpType getPropertyType(@NotNull LattePhpStaticVariable element) {
		return getPropertyType(element.getProject(), element.getPhpType(), element.getVariableName());
	}

	public static LattePhpType getConstantType(@NotNull LattePhpConstant element) {
		return getPropertyType(element.getProject(), element.getPhpType(), element.getConstantName());
	}

	public static LattePhpType getPropertyType(@NotNull LattePhpProperty element) {
		return getPropertyType(element.getProject(), element.getPhpType(), element.getPropertyName());
	}

	private static LattePhpType getPropertyType(@NotNull Project project, @NotNull LattePhpType type, @NotNull String elementName) {
		PhpClass first = type.getFirstPhpClass(project);
		if (first == null) {
			return null;
		}

		for (Field field : first.getFields()) {
			if (field.getName().equals(LattePhpUtil.normalizePhpVariable(elementName))) {
				return new LattePhpType(field.getType().toString(), field.getType().isNullable());
			}
		}
		return null;
	}

	private static LattePhpType getMethodType(@NotNull Project project, @NotNull LattePhpType type, @NotNull String elementName) {
		PhpClass first = type.getFirstPhpClass(project);
		if (first == null) {
			return null;
		}

		for (Method phpMethod : first.getMethods()) {
			if (phpMethod.getName().equals(elementName)) {
				return new LattePhpType(phpMethod.getType().toString(), phpMethod.getType().isNullable());
			}
		}
		return null;
	}

	public static LattePhpType getPhpType(@NotNull LattePhpClass element) {
		return new LattePhpType(element.getClassName(), false);
	}

	public static boolean isTemplateType(@NotNull LattePhpClass element) {
		PsiElement parent = element.getParent();
		if (parent == null) {
			return false;
		}

		PsiElement prevParent = PsiTreeUtil.prevLeaf(parent.getParent(), true);
		return prevParent != null && prevParent.getText().equals("templateType");
	}

	public static boolean isVarTypeDefinition(@NotNull LattePhpVariable element) {
		PsiElement parent = element.getParent();
		if (parent == null) {
			return false;
		}

		PsiElement prevParent = PsiTreeUtil.prevLeaf(parent.getParent(), true);
		return prevParent != null && prevParent.getText().equals("varType");
	}

	public static boolean isDefinition(@NotNull LattePhpVariable element) {
		if (isVarTypeDefinition(element)) {
			return true;
		}

		PsiElement parent = element.getParent();
		if (parent == null) {
			return false;
		}

		if (parent.getNode().getElementType() == PHP_ARRAY_OF_VARIABLES) {
			PsiElement parentPrevElement = PsiTreeUtil.skipWhitespacesBackward(parent);
			IElementType type = parentPrevElement != null ? parentPrevElement.getNode().getElementType() : null;
			return type == T_PHP_AS || type == T_PHP_DOUBLE_ARROW;
		}

		if (parent.getNode().getElementType() == PHP_FOREACH) {
			PsiElement prevElement = PsiTreeUtil.skipWhitespacesBackward(element);
			IElementType type = prevElement != null ? prevElement.getNode().getElementType() : null;
			return type == T_PHP_AS || type == T_PHP_DOUBLE_ARROW;
		}

		LatteNetteAttrValue parentAttr = PsiTreeUtil.getParentOfType(element, LatteNetteAttrValue.class);
		if (parentAttr != null) {
			PsiElement nextElement = PsiTreeUtil.skipWhitespacesForward(element);
			if (nextElement == null || !nextElement.getText().equals("=")) {
				return false;
			}
			PsiElement prevElement = PsiTreeUtil.skipWhitespacesBackward(parentAttr);
			if (prevElement == null || !prevElement.getText().equals("=")) {
				return false;
			}

			prevElement = PsiTreeUtil.skipWhitespacesBackward(prevElement);
			return prevElement != null && prevElement.getText().equals("n:for");
		}

		PsiElement prev = PsiTreeUtil.prevLeaf(parent, true);
		if (prev == null) {
			return false;
		}

		if (prev.getText().equals("for") || prev.getText().equals("var")) {
			PsiElement nextElement = PsiTreeUtil.skipWhitespacesForward(element);
			if (nextElement != null && nextElement.getText().equals("=")) {
				return true;
			}
		}

		return false;
	}

	public static String getName(LattePhpVariable element) {
		return element.getVariableName();
	}

	public static PsiElement setName(LattePhpMethod element, String newName) {
		ASTNode keyNode = element.getFirstChild().getNode();
		PsiElement method = LatteElementFactory.createMethod(element.getProject(), newName);
		if (method == null) {
			return element;
		}
		return replaceChildNode(element, method, keyNode);
	}

	public static PsiElement setName(LattePhpProperty element, String newName) {
		ASTNode keyNode = element.getFirstChild().getNode();
		PsiElement property = LatteElementFactory.createProperty(element.getProject(), newName);
		if (property == null) {
			return element;
		}
		return replaceChildNode(element, property, keyNode);
	}

	public static PsiElement setName(LattePhpConstant element, String newName) {
		ASTNode keyNode = element.getFirstChild().getNode();
		PsiElement property = LatteElementFactory.createConstant(element.getProject(), newName);
		if (property == null) {
			return element;
		}
		return replaceChildNode(element, property, keyNode);
	}

	public static PsiElement setName(LattePhpStaticVariable element, String newName) {
		ASTNode keyNode = element.getFirstChild().getNode();
		PsiElement property = LatteElementFactory.createStaticVariable(element.getProject(), newName);
		if (property == null) {
			return element;
		}
		return replaceChildNode(element, property, keyNode);
	}

	public static PsiElement setName(LattePhpVariable element, String newName) {
		ASTNode keyNode = element.getFirstChild().getNode();
		LattePhpVariable variable = LatteElementFactory.createVariable(element.getProject(), newName);
		if (variable == null) {
			return element;
		}
		return replaceChildNode(element, variable, keyNode);
	}

	@NotNull
	private static PsiElement replaceChildNode(@NotNull PsiElement psiElement, @NotNull PsiElement newElement, @Nullable ASTNode keyNode) {
		ASTNode newKeyNode = newElement.getFirstChild().getNode();
		if (newKeyNode == null) {
			return psiElement;
		}

		if (keyNode == null) {
			psiElement.getNode().addChild(newKeyNode);

		} else {
			psiElement.getNode().replaceChild(keyNode, newKeyNode);
		}
		return psiElement;
	}

	public static PsiElement getNameIdentifier(LattePhpVariable element) {
		return findFirstChildWithType(element, T_MACRO_ARGS_VAR);
	}

	public static String getName(LattePhpStaticVariable element) {
		PsiElement found = findFirstChildWithType(element, T_MACRO_ARGS_VAR);
		return found != null ? LattePhpUtil.normalizePhpVariable(found.getText()) : null;
	}

	public static PsiElement getNameIdentifier(LattePhpStaticVariable element) {
		return findFirstChildWithType(element, T_MACRO_ARGS_VAR);
	}

	public static PsiElement getNameIdentifier(PsiElement element) {
		return findFirstChildWithType(element, T_PHP_METHOD);
	}

	public static PsiElement getNameIdentifier(LattePhpConstant element) {
		return findFirstChildWithType(element, T_PHP_IDENTIFIER);
	}

	public static PsiElement getNameIdentifier(LattePhpProperty element) {
		return findFirstChildWithType(element, T_PHP_IDENTIFIER);
	}

	public static PsiElement getNameIdentifier(LatteMacroTag element) {
		return findFirstChildWithType(element, T_MACRO_NAME);
	}

	public static String getName(LatteMacroTag element) {
		return element.getMacroName();
	}

	public static PsiElement getNameIdentifier(LattePhpClass element) {
		return findFirstChildWithType(element, T_PHP_VAR_TYPE);
	}

	public static String getName(PsiElement element) {
		PsiElement found = findFirstChildWithType(element, T_PHP_METHOD);
		return found != null ? found.getText() : null;
	}

	public static String getName(LattePhpConstant element) {
		PsiElement found = findFirstChildWithType(element, T_PHP_IDENTIFIER);
		return found != null ? found.getText() : null;
	}

	public static String getName(LattePhpProperty element) {
		PsiElement found = findFirstChildWithType(element, T_PHP_IDENTIFIER);
		return found != null ? found.getText() : null;
	}

	public static PsiElement setName(PsiElement element, String newName) {
		return element;
	}

	private static PsiElement findFirstChildWithType(PsiElement element, @NotNull IElementType type) {
		ASTNode keyNode = element.getNode().findChildByType(type);
		if (keyNode != null) {
			return keyNode.getPsi();
		} else {
			return null;
		}
	}
}
