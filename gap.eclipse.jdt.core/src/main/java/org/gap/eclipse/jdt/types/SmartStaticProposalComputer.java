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
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.gap.eclipse.jdt.CorePlugin;

public class SmartStaticProposalComputer extends AbstractSmartProposalComputer implements IJavaCompletionProposalComputer {
	public static final String CATEGORY_ID = "gap.eclipse.jdt.proposalCategory.smartStatic";

	private StaticMemberFinder staticMemberFinder = new StaticMemberFinder();

	private final static long TIMEOUT = Long.getLong("org.gap.eclipse.jdt.types.smartStaticTimeout", 4000);

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
				}
			} else {
				return searchFromAST(context, monitor);
			}
		}
		return Collections.emptyList();
	}

	private List<ICompletionProposal> completionList(IProgressMonitor monitor,
			JavaContentAssistInvocationContext context, List<String> typeNames) {
		final Duration blockDuration = Duration.ofMillis(TIMEOUT);
		if (!isPreceedSpaceNewKeyword(context)) {
			return staticMemberFinder.find(typeNames, context, monitor, blockDuration).collect(Collectors.toList());
		}
		return Collections.emptyList();
	}

	private List<ICompletionProposal> searchFromAST(JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		ASTParser parser = ASTParser.newParser(AST.JLS13);
		parser.setSource(context.getCompilationUnit());
		parser.setProject(context.getProject());
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setBindingsRecovery(true);
		ASTNode ast = parser.createAST(monitor);
		CompletionASTVistor visitor = new CompletionASTVistor(context);
		ast.accept(visitor);
		
		List<String> expectedTypeNames = visitor.getExpectedTypeBindings().stream()
			.filter(t -> !(t.isEnum() || t.isPrimitive()))
			.map(ITypeBinding::getQualifiedName)
			.filter(t -> !isUnsupportedType(Signature.getTypeErasure(t)))
			.collect(Collectors.toList());
		
		
		if(expectedTypeNames.isEmpty() &&  context.getCoreContext().getToken() != null && context.getCoreContext().getToken().length > 0) {
			return completionList(monitor, context, Collections.emptyList());
		}
		
		return completionList(monitor, context, expectedTypeNames);
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
