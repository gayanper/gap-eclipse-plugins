package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.ui.text.java.LazyJavaTypeCompletionProposal;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.StyledString;

@SuppressWarnings("restriction")
public class LazyArrayJavaTypeProposal extends LazyJavaTypeCompletionProposal {

	public LazyArrayJavaTypeProposal(CompletionProposal proposal, JavaContentAssistInvocationContext context) {
		super(proposal, context);
	}

	@Override
	protected StyledString computeDisplayString() {
		StyledString styledString = super.computeDisplayString();
		String simpleName = String.valueOf(Signature.getSignatureSimpleName(getProposal().getSignature()));
		simpleName = Signature.getSimpleName(simpleName); // This for public inner classes
		int offset = styledString.getString().indexOf(simpleName) + simpleName.length();
		styledString.setStyle(offset, 2, null);
		styledString.insert('[', offset++);
		styledString.insert(']', offset++);
		styledString.setStyle(offset, styledString.length() - offset, StyledString.QUALIFIER_STYLER);
		return styledString;
	}

	@Override
	protected String computeReplacementString() {
		final StringBuilder replacementString = new StringBuilder(super.computeReplacementString());
		replacementString.append("[]");
		return replacementString.toString();
	}
	
	@Override
	protected int computeCursorPosition() {
		return getReplacementString().indexOf('[') + 1;
	}
	
	@Override
	public void apply(IDocument document, char trigger, int offset) {
		super.apply(document, trigger, offset);
		setUpLinkedMode(document, ']');
	}
}
