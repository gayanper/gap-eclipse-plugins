package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;

public final class LastInvocation {
	private int lastOffset = -1;
	private int hitcount = 0;

	public boolean canPerformSecondarySearch(JavaContentAssistInvocationContext context) {
		int invocationOffset = context.getInvocationOffset();
		if (lastOffset == invocationOffset) {
			hitcount++;
			return hitcount >= 2;
		}
		lastOffset = invocationOffset;
		hitcount = 1;
		return false;
	}

	public void reset() {
		hitcount--;
	}

	public boolean wasLastSecondarySearch() {
		return hitcount >= 2;
	}
}
