package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.swt.graphics.Image;
import org.gap.eclipse.jdt.CorePlugin;
import org.gap.eclipse.jdt.common.Images;

public class MethodRefCompletionProposal extends LazyJavaCompletionProposal {

	protected MethodRefCompletionProposal(String qualifier, String method, int relevance,
			JavaContentAssistInvocationContext context) {
		super(qualifier.concat("::").concat(method), relevance, context);
	}

	@Override
	public Image getImage() {
		return CorePlugin.getDefault().getImageRegistry().get(Images.OBJ_METHODREF);
	}

	@Override
	protected String computeReplacementString() {
		return getDisplayString();
	}

	@Override
	protected LinkedModeModel computeLinkedModeModel(IDocument document) throws BadLocationException {
		return null;
	}

	@Override
	protected boolean isSupportLinkMode() {
		return false;
	}
}
