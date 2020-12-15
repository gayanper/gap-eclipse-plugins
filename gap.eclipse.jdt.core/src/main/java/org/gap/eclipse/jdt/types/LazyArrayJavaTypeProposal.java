package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.ui.text.java.LazyJavaTypeCompletionProposal;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.StyledString;

@SuppressWarnings("restriction")
public class LazyArrayJavaTypeProposal extends LazyJavaTypeCompletionProposal {
	private final boolean initialize;
	private final char cursorChar, linkChar;

	public LazyArrayJavaTypeProposal(CompletionProposal proposal, JavaContentAssistInvocationContext context,
			boolean initialize) {
		super(proposal, context);
		this.initialize = initialize;
		this.cursorChar = initialize ? '{' : '[';
		this.linkChar = initialize ? '}' : ']';
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

		if (this.initialize) {
			styledString.setStyle(offset, 2, StyledString.COUNTER_STYLER);
			styledString.insert('{', offset++);
			styledString.insert('}', offset++);
		}

		styledString.setStyle(offset, styledString.length() - offset, StyledString.QUALIFIER_STYLER);
		return styledString;
	}

	@Override
	protected String computeReplacementString() {
		final StringBuilder replacementString = new StringBuilder(super.computeReplacementString());
		replacementString.append("[]");
		if (this.initialize) {
			replacementString.append("{}");
		}
		return replacementString.toString();
	}
	
	@Override
	protected int computeCursorPosition() {
		return getReplacementString().indexOf(cursorChar) + 1;
	}
	
	@Override
	public void apply(IDocument document, char trigger, int offset) {
		super.apply(document, trigger, offset);
		setUpLinkedMode(document, linkChar);
	}
}
