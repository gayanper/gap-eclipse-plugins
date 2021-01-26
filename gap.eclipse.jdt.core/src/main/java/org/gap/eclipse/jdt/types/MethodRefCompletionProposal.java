package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.corext.util.Strings;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.BoldStylerProvider;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension7;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.gap.eclipse.jdt.CorePlugin;
import org.gap.eclipse.jdt.common.Images;

@SuppressWarnings("restriction")
public class MethodRefCompletionProposal extends LazyJavaCompletionProposal implements ICompletionProposalExtension7 {

	private int relevance;

	private int matchRule;

	private String pattern;

	protected MethodRefCompletionProposal(String qualifier, String method,
			String pattern, JavaContentAssistInvocationContext context) {
		super(qualifier.concat("::").concat(method), 0, context);
		this.pattern = pattern;
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

	public void setRelevance(int value) {
		this.relevance = value;
	}

	@Override
	public int getRelevance() {
		return this.relevance;
	}

	public void setMatchRule(int matchRule) {
		this.matchRule = matchRule;
	}

	public int getMatchRule() {
		return matchRule;
	}

	@Override
	public StyledString getStyledDisplayString(IDocument document, int offset, BoldStylerProvider boldStylerProvider) {
		StyledString styledString = new StyledString(getDisplayString());
		if(!pattern.isEmpty()) {
			int[] matchingRegions = SearchPattern.getMatchingRegions(pattern.replace("::", "__"),
					getDisplayString().replace("::", "__"),
					matchRule);
			Strings.markMatchingRegions(styledString, 0, matchingRegions, boldStylerProvider.getBoldStyler());
		}
		return styledString;
	}
}
