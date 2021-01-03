package org.gap.eclipse.jdt.common;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.gap.eclipse.jdt.CorePlugin;

public final class Images {
	public static final String OBJ_LAMBDA = "lambda_obj";

	public static final String OBJ_METHODREF = "methodref_obj";

	private static final String FILE_EXT = ".png";

	private static final String ICON_PATH = "icons/view16/";

	private Images() {
	}

	public static void initializeImageRegistry(ImageRegistry reg) {
		reg.put(OBJ_LAMBDA, descriptor(OBJ_LAMBDA));
		reg.put(OBJ_METHODREF, descriptor(OBJ_METHODREF));
	}

	private static ImageDescriptor descriptor(String imageKey) {
		return CorePlugin.getImageDescriptor(ICON_PATH + imageKey + FILE_EXT);
	}
}
