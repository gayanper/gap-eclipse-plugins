package org.gap.eclipse.jdt.types;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.gap.eclipse.jdt.common.Log;

public class SmartTypeProposalComputer extends AbstractSmartProposalComputer
		implements IJavaCompletionProposalComputer {

	public static final String CATEGORY_ID = "gap.eclipse.jdt.proposalCategory.smartSubType";

	private SubTypeFinder subTypeFinder = new SubTypeFinder();

	private LastInvocation lastInvocation = new LastInvocation();

	@Override
	public List<ICompletionProposal> computeSmartCompletionProposals(JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		if (context.getCoreContext() != null) {
			char[][] expectedTypes = context.getCoreContext().getExpectedTypesSignatures();
			if(expectedTypes != null && expectedTypes.length > 0) {
				return Stream.of(expectedTypes).map(t -> {
					String sig = String.valueOf(t);
					boolean arrayType = Signature.getTypeSignatureKind(t) == Signature.ARRAY_TYPE_SIGNATURE;
					String qualifier = Signature.getSignatureQualifier(sig);
					if (!qualifier.isEmpty()) {
						qualifier = qualifier.concat(".");
					}
					String fqn = qualifier
							.concat(arrayType
									? Signature.getSignatureSimpleName(
											Signature.getTypeErasure(Signature.getElementType(sig)))
									: Signature.getSignatureSimpleName(Signature.getTypeErasure((sig))));
					return Map.entry(fqn, arrayType);
				}).filter(e -> !isUnsupportedType(e.getKey()) || e.getValue())
				.flatMap(e -> {
							Code ptype = PrimitiveType.toCode(e.getKey());
							if (ptype == null) {
								IType type;
								try {
									type = context.getProject().findType(e.getKey());
									if (type != null) {
										return completionList(monitor, context, type,
												lastInvocation.canPerformSecondarySearch(context), e.getValue())
														.stream();
									}
								} catch (JavaModelException ex) {
									Log.error(ex);
								}
							}
					return Stream.empty();
				}).collect(Collectors.toList());
			} else {
				return searchFromAST(context, monitor);
			}
		}
		return Collections.emptyList();
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
				result.add(Proposals.toTypeProposal(expectedType, context, arrayType, false));
				result.add(Proposals.toTypeProposal(expectedType, context, arrayType, true));
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
}
