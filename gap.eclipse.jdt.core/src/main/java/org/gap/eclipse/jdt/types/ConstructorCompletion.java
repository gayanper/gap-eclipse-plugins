package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.internal.codeassist.InternalCompletionProposal;

@SuppressWarnings("restriction")
class ConstructorCompletion extends InternalCompletionProposal {

	public ConstructorCompletion(int completionLocation) {
		super(CompletionProposal.CONSTRUCTOR_INVOCATION, completionLocation);
	}

	@Override
	public void setDeclarationPackageName(char[] declarationPackageName) {
		super.setDeclarationPackageName(declarationPackageName);
	}

	@Override
	public void setDeclarationTypeName(char[] declarationTypeName) {
		super.setDeclarationTypeName(declarationTypeName);
	}

	@Override
	public void setParameterPackageNames(char[][] parameterPackageNames) {
		super.setParameterPackageNames(parameterPackageNames);
	}

	@Override
	public void setParameterTypeNames(char[][] parameterTypeNames) {
		super.setParameterTypeNames(parameterTypeNames);
	}

	@Override
	public void setIsContructor(boolean isConstructor) {
		super.setIsContructor(isConstructor);
	}
}
