package com.jantvrdik.intellij.latte.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.jantvrdik.intellij.latte.psi.LatteFile;
import com.jantvrdik.intellij.latte.psi.LatteMacroTag;
import com.jantvrdik.intellij.latte.psi.LattePhpClass;
import com.jantvrdik.intellij.latte.utils.LatteUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TemplateTypeInspection extends LocalInspectionTool {

	@NotNull
	@Override
	public String getShortName() {
		return "TemplateType";
	}

	@Nullable
	@Override
	public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
		if (!(file instanceof LatteFile)) {
			return null;
		}

		final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
		file.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
			@Override
			public void visitElement(PsiElement element) {
				if (element instanceof LatteMacroTag && ((LatteMacroTag) element).getMacroName().equals("templateType")) {
					List<LatteMacroTag> allMacros = new ArrayList<LatteMacroTag>();
					LatteUtil.findLatteMacroTemplateType(allMacros, (LatteFile) file);
					if (allMacros.size() > 1) {
						ProblemDescriptor problem = manager.createProblemDescriptor(
								element,
								"Macro template type can be used only once per file.",
								true,
								ProblemHighlightType.GENERIC_ERROR,
								isOnTheFly
						);
						problems.add(problem);

					} else {
						List<LattePhpClass> currentClasses = new ArrayList<LattePhpClass>();
						LatteUtil.findLatteTemplateType(currentClasses, element);
						if (currentClasses.size() == 0) {
							ProblemDescriptor problem = manager.createProblemDescriptor(
									element,
									"Invalid class name in macro templateType.",
									true,
									ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
									isOnTheFly
							);
							problems.add(problem);
						}
					}

				} else {
					super.visitElement(element);
				}
			}
		});

		return problems.toArray(new ProblemDescriptor[problems.size()]);
	}
}
