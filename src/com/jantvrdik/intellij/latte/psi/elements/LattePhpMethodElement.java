package com.jantvrdik.intellij.latte.psi.elements;

import com.intellij.psi.PsiNameIdentifierOwner;

public interface LattePhpMethodElement extends PsiNameIdentifierOwner {

	public abstract String getMethodName();

}