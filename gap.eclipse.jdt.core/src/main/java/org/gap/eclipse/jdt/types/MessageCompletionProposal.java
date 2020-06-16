package org.gap.eclipse.jdt.types;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

public class MessageCompletionProposal implements ICompletionProposal {
	private String mesage;

	public MessageCompletionProposal(String mesage) {
		this.mesage = mesage;
	}

	@Override
	public void apply(IDocument document) {
	}

	@Override
	public Point getSelection(IDocument document) {
		return null;
	}

	@Override
	public String getAdditionalProposalInfo() {
		return "";
	}

	@Override
	public String getDisplayString() {
		return mesage;
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public IContextInformation getContextInformation() {
		return null;
	}
}
