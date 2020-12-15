package org.gap.eclipse.jdt.types;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.gap.eclipse.jdt.CorePlugin;

public class SmartTypeProposalComputer extends AbstractSmartProposalComputer
		implements IJavaCompletionProposalComputer {

	public static final String CATEGORY_ID = "gap.eclipse.jdt.proposalCategory.smartSubType";

	private SubTypeFinder subTypeFinder = new SubTypeFinder();

	private final static long TIMEOUT = Long.getLong("org.gap.eclipse.jdt.types.smartSubtypeTimeout", 3000);

	private LastInvocation lastInvocation = new LastInvocation();
	
	@Override
	public void sessionStarted() {
	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext invocationContext,
			IProgressMonitor monitor) {
		if(!shouldCompute(invocationContext)) {
			return Collections.emptyList();
		}
		
		if (invocationContext instanceof JavaContentAssistInvocationContext) {
			JavaContentAssistInvocationContext context = (JavaContentAssistInvocationContext) invocationContext;
			if (context.getExpectedType() != null) {
				IType expectedType = context.getExpectedType();
				final boolean arrayType = Optional.ofNullable(((JavaContentAssistInvocationContext) invocationContext).getCoreContext())
						.map(c -> Signature.getTypeSignatureKind(c.getExpectedTypesSignatures()[0]) == Signature.ARRAY_TYPE_SIGNATURE)
						.orElse(false);

				if (isUnsupportedType(expectedType.getFullyQualifiedName()) && !arrayType) {
					return Collections.emptyList();
				}
				return completionList(monitor, context, expectedType, lastInvocation.canPerformSecondarySearch(context), arrayType);
			} else {
				return searchFromAST((JavaContentAssistInvocationContext) context, monitor);
			}
		}
		return Collections.emptyList();
	}

	private List<ICompletionProposal> completionList(IProgressMonitor monitor,
			JavaContentAssistInvocationContext context, IType expectedType, boolean performSubType, boolean arrayType) {
		List<ICompletionProposal> result = new ArrayList<ICompletionProposal>();
		if (isPreceedSpaceNewKeyword(context)) {
			if(performSubType) {
				Duration timeout = isAsyncCompletionActive(context) ? null : Duration.ofMillis(TIMEOUT);
				result.addAll(subTypeFinder.find(expectedType, context, monitor, timeout).collect(Collectors.toList()));
			}
			if(arrayType) {
				result.add(SubTypeFinder.toCompletionProposal(expectedType, context, monitor, arrayType, false));
				result.add(SubTypeFinder.toCompletionProposal(expectedType, context, monitor, arrayType, true));
			}
		}
		return result;
	}

	private List<ICompletionProposal> searchFromAST(JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		ASTParser parser = ASTParser.newParser(AST.JLS14);
		parser.setSource(context.getCompilationUnit());
		parser.setProject(context.getProject());
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setBindingsRecovery(true);
		ASTNode ast = parser.createAST(monitor);
		CompletionASTVistor visitor = new CompletionASTVistor(context);
		ast.accept(visitor);
		
		boolean performSubType = lastInvocation.canPerformSecondarySearch(context);
		return visitor.getExpectedTypeEntries().stream()
				.filter(e -> !isUnsupportedType(e.getValue().getFullyQualifiedName()) || e.getKey().isArray())
			.flatMap(e -> completionList(monitor, context, e.getValue(), performSubType, e.getKey().isArray()).stream())
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

	@Override
	public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		return Collections.emptyList();
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public void sessionEnded() {

	}

}
