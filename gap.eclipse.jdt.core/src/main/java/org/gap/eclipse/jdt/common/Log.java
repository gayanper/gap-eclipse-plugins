package org.gap.eclipse.jdt.common;

import org.gap.eclipse.jdt.CorePlugin;

public final class Log {

	private Log() {

	}

	public static void error(Throwable t) {
		CorePlugin.getDefault().logError(t.getMessage(), t);
	}
}
