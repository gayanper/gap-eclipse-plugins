package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;

public final class LastInvocation {
	private int lastOffset = -1;

	public boolean canPerformSecondarySearch(JavaContentAssistInvocationContext context) {
		int invocationOffset = context.getInvocationOffset();
		if (lastOffset == invocationOffset) {
			return true;
		}
		lastOffset = invocationOffset;
		return false;
	}
	
	public void reset() {
		lastOffset = -1;
	}
}
