package org.gap.eclipse.jdt.symbol;

import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDescriptorRegistry;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.gap.eclipse.jdt.CorePlugin;

@SuppressWarnings("restriction") class SymbolLabelProvider extends LabelProvider {
	private final ImageDescriptorRegistry imageRegistry = JavaPlugin.getImageDescriptorRegistry();

	@Override
	public String getText(Object element) {
		if (element == null) {
			return "";
		}

		IMember member = (IMember) element;
		StringBuilder builder = new StringBuilder(member.getElementName());
		if (member.getDeclaringType() != null) {
			builder.append(" - ").append(member.getDeclaringType().getFullyQualifiedName());
		}
		return builder.toString();
	}

	@Override
	public Image getImage(Object element) {
		if (element == null) {
			return null;
		}

		final IMember member = (IMember) element;
		final IType declaringType = member.getDeclaringType();
		try {
			final boolean isInterfOrAnno = declaringType.isAnnotation() || declaringType.isInterface();
			if (element instanceof IMethod) {
				return imageRegistry
						.get(JavaElementImageProvider.getMethodImageDescriptor(isInterfOrAnno, member.getFlags()));
			} else {
				return imageRegistry
						.get(JavaElementImageProvider.getFieldImageDescriptor(isInterfOrAnno, member.getFlags()));
			}
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
		return super.getImage(element);
	}
}