package org.gap.eclipse.jdt.types;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

final class ComputingProposal implements ICompletionProposal, ICompletionProposalExtension {
	private int offset;
	private String message;

	public ComputingProposal(int offset, String message) {
		this.offset = offset;
		this.message = message;
	}

	@Override
	public void apply(IDocument document) {
		// Nothing to do, maybe show some progress report?
	}

	@Override
	public Point getSelection(IDocument document) {
		return new Point(offset, 0);
	}

	@Override
	public IContextInformation getContextInformation() {
		return null;
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public String getDisplayString() {
		return message;
	}

	@Override
	public String getAdditionalProposalInfo() {
		return "";
	}

	@Override
	public void apply(IDocument document, char trigger, int offset) {
		// Nothing to do
	}

	@Override
	public boolean isValidFor(IDocument document, int offset) {
		return false;
	}

	@Override
	public char[] getTriggerCharacters() {
		return null;
	}

	@Override
	public int getContextInformationPosition() {
		return -1;
	}
}
