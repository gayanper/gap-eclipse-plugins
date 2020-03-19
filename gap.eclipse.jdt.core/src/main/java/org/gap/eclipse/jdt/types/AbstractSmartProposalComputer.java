package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.gap.eclipse.jdt.CorePlugin;

public class AbstractSmartProposalComputer {

	public AbstractSmartProposalComputer() {
		super();
	}

	protected CompletionProposal createImportProposal(JavaContentAssistInvocationContext context, IType type)
			throws JavaModelException {
		CompletionProposal proposal = CompletionProposal.create(CompletionProposal.TYPE_IMPORT,
				context.getInvocationOffset());
		String fullyQualifiedName = type.getFullyQualifiedName();
		proposal.setCompletion(fullyQualifiedName.toCharArray());
		proposal.setDeclarationSignature(type.getPackageFragment().getElementName().toCharArray());
		proposal.setFlags(type.getFlags());
		proposal.setSignature(Signature.createTypeSignature(fullyQualifiedName, true).toCharArray());

		return proposal;
	}
	
	protected boolean shouldCompute(ContentAssistInvocationContext context) {
		try {
			return !".".equals(context.getDocument().get(context.getInvocationOffset() - 1, 1));
		} catch (BadLocationException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
			return false;
		}
	}
}
