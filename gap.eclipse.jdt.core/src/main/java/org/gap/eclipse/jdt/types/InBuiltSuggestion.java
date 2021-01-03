package org.gap.eclipse.jdt.types;

import java.util.List;

public final class InBuiltSuggestion {
	private InBuiltSuggestion() {
	}

	static List<String> getMethodReferenceTypeSuggestions() {
		return List.of("java.util.Objects", "java.util.function.Predicate", "java.util.function.Function",
				"com.google.common.base.Predicates", "com.google.common.base.Functions");
	}
}
