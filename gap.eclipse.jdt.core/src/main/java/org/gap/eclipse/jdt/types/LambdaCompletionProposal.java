package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.gap.eclipse.jdt.CorePlugin;
import org.gap.eclipse.jdt.common.Images;

public class LambdaCompletionProposal implements IJavaCompletionProposal {

	private final int replacementOffset;
	private final int replacementLength;
	private final String replacementString;
	private final int cursorPosition;
	private final int relevance;

	public LambdaCompletionProposal(int replacementOffset, int replacementLength, String replacementString,
			int fCursorPosition, int relevance) {
		this.replacementOffset = replacementOffset;
		this.replacementLength = replacementLength;
		this.replacementString = replacementString;
		this.cursorPosition = fCursorPosition;
		this.relevance = relevance;
	}

	@Override
	public void apply(IDocument document) {
		try {
			document.replace(replacementOffset, replacementLength, replacementString);
		} catch (BadLocationException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
	}

	@Override
	public Point getSelection(IDocument document) {
		return new Point(replacementOffset + cursorPosition, 0);
	}

	@Override
	public String getAdditionalProposalInfo() {
		return null;
	}

	@Override
	public String getDisplayString() {
		return replacementString;
	}

	@Override
	public Image getImage() {
		return CorePlugin.getDefault().getImageRegistry().get(Images.OBJ_LAMBDA);
	}

	@Override
	public IContextInformation getContextInformation() {
		return null;
	}

	@Override
	public int getRelevance() {
		return relevance;
	}

}
