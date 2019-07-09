package org.gap.eclipse.jdt.types;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.gap.eclipse.jdt.CorePlugin;
import org.gap.eclipse.jdt.common.DisableCategoryJob;

import com.google.common.collect.Sets;

public class SubTypeProposalComputer implements IJavaCompletionProposalComputer {
	// TODO: this class need heavy refactoring after testing the current
	// implementation.

	private static final String CATEGORY_ID = "gap.eclipse.jdt.proposalCategory.subType";
	private Set<String> unsupportedTypes = Sets.newHashSet("java.lang.String", "java.lang.Object",
			"java.lang.Cloneable", "java.lang.Throwable", "java.lang.Exception");

	private StaticMemberFinder staticMemberFinder = new StaticMemberFinder();
	private SubTypeFinder subTypeFinder = new SubTypeFinder();

	private final static long TIMEOUT = Long.getLong("org.eclipse.jdt.ui.codeAssistTimeout", 5000);

	@Override
	public void sessionStarted() {
		disableOnDefaultTabIfNeeded();
	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext invocationContext,
			IProgressMonitor monitor) {
		if (invocationContext instanceof JavaContentAssistInvocationContext) {
			JavaContentAssistInvocationContext context = (JavaContentAssistInvocationContext) invocationContext;
			if (context.getExpectedType() != null) {
				IType expectedType = context.getExpectedType();
				if (unsupportedTypes.contains(expectedType.getFullyQualifiedName())) {
					return Collections.emptyList();
				}
				return completionList(monitor, context, expectedType);
			} else {
				return searchFromAST((JavaContentAssistInvocationContext) context, monitor);
			}
		}
		return Collections.emptyList();
	}

	private List<ICompletionProposal> completionList(IProgressMonitor monitor,
			JavaContentAssistInvocationContext context, IType expectedType) {
		final Duration blockDuration = Duration.ofMillis(TIMEOUT).minusMillis(1000);
		if (isPreceedSpaceNewKeyword(context)) {
			return subTypeFinder.find(expectedType, context, monitor, blockDuration).collect(Collectors.toList());

		} else {
			return staticMemberFinder.find(expectedType, context, monitor, blockDuration).collect(Collectors.toList());
		}
	}

	protected void disableOnDefaultTabIfNeeded() {
		final Set<String> excluded = Sets.newHashSet(PreferenceConstants.getExcludedCompletionProposalCategories());
		if (!excluded.contains(CATEGORY_ID)) {
			DisableCategoryJob.forCategory(CATEGORY_ID).schedule(300);
		}
	}

	private List<ICompletionProposal> searchFromAST(JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		ASTParser parser = ASTParser.newParser(AST.JLS11);
		parser.setSource(context.getCompilationUnit());
		parser.setProject(context.getProject());
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setBindingsRecovery(true);
		ASTNode ast = parser.createAST(monitor);
		CompletionASTVistor visitor = new CompletionASTVistor(context);
		ast.accept(visitor);
		final IType expectedType = visitor.getExpectedType();
		if (expectedType == null || unsupportedTypes.contains(expectedType.getFullyQualifiedName())) {
			return Collections.emptyList();
		}

		try {
			if (expectedType.isEnum()) {
				Set<IField> literals = Arrays.stream(expectedType.getChildren()).filter(
						e -> (e.getElementType() == IJavaElement.FIELD) && !e.getElementName().equals("$VALUES"))
						.map(e -> (IField) e).collect(Collectors.toSet());
				return createEnumProposals(literals, context);
			} else {
				return completionList(monitor, context, expectedType);
			}
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
			return Collections.emptyList();
		}
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

	private List<ICompletionProposal> createEnumProposals(Set<IField> literals,
			JavaContentAssistInvocationContext context) {
		final ArrayList<ICompletionProposal> response = new ArrayList<>(literals.size());
		try {
			for (IField field : literals) {
				CompletionProposal proposal = CompletionProposal.create(CompletionProposal.FIELD_REF,
						context.getInvocationOffset());
				String fullyQualifiedName = field.getDeclaringType().getElementName().concat(".")
						.concat(field.getElementName());
				proposal.setName(field.getElementName().toCharArray());
				proposal.setCompletion(fullyQualifiedName.toCharArray());
				proposal.setDeclarationSignature(Signature
						.createTypeSignature(field.getDeclaringType().getFullyQualifiedName(), true).toCharArray());
				proposal.setFlags(field.getFlags());
				float relevance = context.getHistoryRelevance(fullyQualifiedName);
				proposal.setRelevance((int) (1000 * (relevance < 0.1 ? 0.1 : relevance)));
				proposal.setReplaceRange(context.getInvocationOffset(), context.getInvocationOffset());
				proposal.setSignature(field.getTypeSignature().toCharArray());
				proposal.setRequiredProposals(
						new CompletionProposal[] { createImportProposal(context, field.getDeclaringType()) });

				CompletionProposalCollector collector = new CompletionProposalCollector(context.getCompilationUnit());
				collector.setInvocationContext(context);
				collector.acceptContext(context.getCoreContext());
				collector.accept(proposal);

				response.addAll(Arrays.asList(collector.getJavaCompletionProposals()));
			}
		} catch (Exception e) {
			CorePlugin.getDefault().logError("Error occured while creating proposals.", e);
			response.trimToSize();
		}
		return response;
	}

	private CompletionProposal createImportProposal(JavaContentAssistInvocationContext context, IType type)
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

	private class CompletionASTVistor extends ASTVisitor {
		private int offset;
		private IType expectedType;
		private IJavaProject project;
		private boolean preceedSpace = false;

		private CodeRange lastVisited;

		public CompletionASTVistor(JavaContentAssistInvocationContext context) {
			this.offset = context.getInvocationOffset();
			this.project = context.getProject();
			try {
				preceedSpace = context.getDocument().get(offset - 1, 1).equals(" ");
			} catch (BadLocationException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
			}
		}

		@Override
		public boolean visit(MethodInvocation node) {
			CodeRange current = new CodeRange(node.getStartPosition(), node.getStartPosition() + node.getLength());
			if (current.inRange(offset)) {
				if (lastVisited == null || lastVisited.inRange(current)) {
					IMethodBinding method = node.resolveMethodBinding();
					ITypeBinding typeAtOffset = null;
					if (node.arguments().isEmpty()) {
						typeAtOffset = method.getParameterTypes()[0];
					} else {
						typeAtOffset = findParameterTypeAtOffset(node, method);
					}

					try {
						if (typeAtOffset != null) {
							expectedType = project.findType(Signature.getTypeErasure(typeAtOffset.getQualifiedName()));
						} else {
							return true;
						}
					} catch (JavaModelException e) {
						CorePlugin.getDefault().logError(e.getMessage(), e);
					}
				}
			}
			return false;
		}

		private ITypeBinding findParameterTypeAtOffset(MethodInvocation node, IMethodBinding method) {
			@SuppressWarnings("unchecked")
			final List<Object> arguments = node.arguments();
			int typeIndex = -1;
			int checkOffset = preceedSpace ? offset - 1 : offset;

			for (int i = 0; i < arguments.size(); i++) {
				final ASTNode astNode = (ASTNode) arguments.get(i);
				if (astNode.getStartPosition() <= checkOffset
						&& (astNode.getStartPosition() + astNode.getLength()) >= checkOffset) {
					typeIndex = i;
					break;
				}
			}

			if (typeIndex > -1) {
				return method.getParameterTypes()[typeIndex];
			}
			return null;
		}

		public IType getExpectedType() {
			return expectedType;
		}
	}
}
