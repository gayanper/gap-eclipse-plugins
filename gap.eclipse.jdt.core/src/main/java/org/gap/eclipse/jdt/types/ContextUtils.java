package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;

final class ContextUtils {
	private ContextUtils() {
	}

	public static int computeEndOffset(JavaContentAssistInvocationContext context) {
		return context.getInvocationOffset() + context.getTextSelection().getLength();
	}

	public static int computeInvocationOffset(JavaContentAssistInvocationContext context) {
		if (context.getCoreContext() != null && context.getCoreContext().getToken() != null
				&& context.getCoreContext().getToken().length > 0) {
			return context.getInvocationOffset() - context.getCoreContext().getToken().length;
		} else {
			return context.getInvocationOffset();
		}
	}
}
