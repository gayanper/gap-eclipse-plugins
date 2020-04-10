package org.gap.eclipse.jdt.types;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.gap.eclipse.jdt.types.messages"; //$NON-NLS-1$
	public static String WarmUpSearchIndex_Message;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
