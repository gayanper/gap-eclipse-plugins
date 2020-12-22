package org.gap.eclipse.jdt.types;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.gap.eclipse.jdt.CorePlugin;

public class SmartTypeProposalComputer extends AbstractSmartProposalComputer
		implements IJavaCompletionProposalComputer {

	public static final String CATEGORY_ID = "gap.eclipse.jdt.proposalCategory.smartSubType";

	private SubTypeFinder subTypeFinder = new SubTypeFinder();

	private LastInvocation lastInvocation = new LastInvocation();

	@Override
	public List<ICompletionProposal> computeSmartCompletionProposals(JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		if (context.getExpectedType() != null) {
			IType expectedType = context.getExpectedType();
			final boolean arrayType = Optional.ofNullable(context.getCoreContext())
					.map(c -> Signature
							.getTypeSignatureKind(c.getExpectedTypesSignatures()[0]) == Signature.ARRAY_TYPE_SIGNATURE)
					.orElse(false);

			if (isUnsupportedType(expectedType.getFullyQualifiedName()) && !arrayType) {
				return Collections.emptyList();
			}
			return completionList(monitor, context, expectedType, lastInvocation.canPerformSecondarySearch(context),
					arrayType);
		} else {
			return searchFromAST(context, monitor);
		}
	}

	private List<ICompletionProposal> completionList(IProgressMonitor monitor,
			JavaContentAssistInvocationContext context, IType expectedType, boolean performSubType, boolean arrayType) {
		List<ICompletionProposal> result = new ArrayList<>();
		if (isPreceedSpaceNewKeyword(context)) {
			if (performSubType) {
				Duration timeout = isAsyncCompletionActive(context) ? null : Duration.ofMillis(TIMEOUT);
				result.addAll(subTypeFinder.find(expectedType, context, monitor, timeout).collect(Collectors.toList()));
			}
			if (arrayType) {
				result.add(SubTypeFinder.toCompletionProposal(expectedType, context, monitor, arrayType, false));
				result.add(SubTypeFinder.toCompletionProposal(expectedType, context, monitor, arrayType, true));
			}
		}
		return result;
	}

	private List<ICompletionProposal> searchFromAST(JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		ASTResult result = findInAST(context, monitor);
		boolean performSubType = lastInvocation.canPerformSecondarySearch(context);
		return result.getExpectedTypeEntries().stream()
				.filter(e -> !isUnsupportedType(e.getValue().getFullyQualifiedName()) || e.getKey().isArray())
				.flatMap(e -> completionList(monitor, context, e.getValue(), performSubType, e.getKey().isArray())
						.stream())
				.collect(Collectors.toList());
	}

	private boolean isPreceedSpaceNewKeyword(ContentAssistInvocationContext context) {
		final int offset = context.getInvocationOffset();
		final String keywordPrefix = "new ";
		if (offset > keywordPrefix.length()) {
			try {
				return context.getDocument().get(offset - keywordPrefix.length(), keywordPrefix.length())
						.equals(keywordPrefix);
			} catch (BadLocationException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
			}
		}
		return false;
	}
}
