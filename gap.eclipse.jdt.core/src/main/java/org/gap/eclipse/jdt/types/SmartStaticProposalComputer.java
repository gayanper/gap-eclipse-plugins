package org.gap.eclipse.jdt.types;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.gap.eclipse.jdt.CorePlugin;

public class SmartStaticProposalComputer extends AbstractSmartProposalComputer implements IJavaCompletionProposalComputer {
	public static final String CATEGORY_ID = "gap.eclipse.jdt.proposalCategory.smartStatic";

	private StaticMemberFinder staticMemberFinder = new StaticMemberFinder();

	@Override
	public List<ICompletionProposal> computeSmartCompletionProposals(JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		final IType type = context.getExpectedType();
		// following null check for type eliminates primitive types.
		if (type != null && context.getCoreContext().getExpectedTypesSignatures() != null &&
				context.getCoreContext().getExpectedTypesSignatures().length > 0) {
			
			final String expectedTypeFQN = toParameterizeFQN(context.getCoreContext().getExpectedTypesSignatures()[0]);	
			
			try {
				if (isUnsupportedType(Signature.getTypeErasure(expectedTypeFQN)) || type.isEnum()) {
					return Collections.emptyList();
				}
				return completionList(monitor, context, Arrays.asList(expectedTypeFQN));
			} catch (JavaModelException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
				return Collections.emptyList();
			}
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
