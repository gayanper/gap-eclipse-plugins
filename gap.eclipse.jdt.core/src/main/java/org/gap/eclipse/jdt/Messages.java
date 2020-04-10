package org.gap.eclipse.jdt;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.gap.eclipse.jdt.messages"; //$NON-NLS-1$
	public static String Hyperlink_OpenReference;
	public static String Hyperlink_SearchDescription;
	public static String SearchJobTracker_JobName;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
