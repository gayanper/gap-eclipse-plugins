package org.gap.eclipse.jdt.types;

import java.util.Set;
import java.util.Map.Entry;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ITypeBinding;

public final class ASTResult {
	private final Set<IType> expectedTypes;
	private final Set<ITypeBinding> expectedTypeBindings;
	private final Set<Entry<ITypeBinding, IType>> expectedTypeEntries;

	public ASTResult(Set<IType> expectedTypes, Set<ITypeBinding> expectedTypeBindings,
			Set<Entry<ITypeBinding, IType>> expectedTypeEntries) {
		this.expectedTypes = expectedTypes;
		this.expectedTypeBindings = expectedTypeBindings;
		this.expectedTypeEntries = expectedTypeEntries;
	}

	public Set<ITypeBinding> getExpectedTypeBindings() {
		return expectedTypeBindings;
	}

	public Set<IType> getExpectedTypes() {
		return expectedTypes;
	}

	public Set<Entry<ITypeBinding, IType>> getExpectedTypeEntries() {
		return expectedTypeEntries;
	}
}
