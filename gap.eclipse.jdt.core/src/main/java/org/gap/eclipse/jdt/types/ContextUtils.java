package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;

final class ContextUtils {
	private ContextUtils() {
	}

	public static int computeEndOffset(JavaContentAssistInvocationContext context) {
		return context.getInvocationOffset() + context.getTextSelection().getLength();
	}

}
