package org.gap.eclipse.jdt.types;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

public class SmartStaticProposalComputer extends AbstractSmartProposalComputer implements IJavaCompletionProposalComputer {
	public static final String CATEGORY_ID = "gap.eclipse.jdt.proposalCategory.smartStatic";

	private StaticMemberFinder staticMemberFinder = new StaticMemberFinder();

	@Override
	public List<ICompletionProposal> computeSmartCompletionProposals(JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		Set<String> expectedTypes = resolveExpectedTypes(context);
		if (!expectedTypes.isEmpty()) {
			List<String> types = expectedTypes.stream()
					.filter(Predicate.not(t -> isUnsupportedType(Signature.getTypeErasure(t))))
					.collect(Collectors.toList());
			if (types.isEmpty()) {
				return Collections.emptyList();
			}
			return completionList(monitor, context, types);
		} else {
			return searchFromAST(context, monitor);
		}
	}

	private List<ICompletionProposal> completionList(IProgressMonitor monitor,
			JavaContentAssistInvocationContext context, List<String> typeNames) {
		final Duration blockDuration = isAsyncCompletionActive(context) ? null : Duration.ofMillis(TIMEOUT);
		if (!isPreceedSpaceNewKeyword(context)) {
			return staticMemberFinder.find(typeNames, context, monitor, blockDuration).collect(Collectors.toList());
		}
		return Collections.emptyList();
	}

	private List<ICompletionProposal> searchFromAST(JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		ASTResult result = findInAST(context, monitor);
		List<String> expectedTypeNames = result.getExpectedTypeBindings().stream()
			.filter(t -> !(t.isEnum() || t.isPrimitive()))
			.map(ITypeBinding::getQualifiedName)
			.filter(t -> !isUnsupportedType(Signature.getTypeErasure(t)))
			.collect(Collectors.toList());
		
		
		if(expectedTypeNames.isEmpty() &&  context.getCoreContext().getToken() != null && context.getCoreContext().getToken().length > 0) {
			return completionList(monitor, context, Collections.emptyList());
		}
		
		return completionList(monitor, context, expectedTypeNames);
	}
}
