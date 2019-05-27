package org.gap.eclipse.recommenders.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobFunction;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.search.JavaSearchScope;
import org.eclipse.jdt.internal.ui.text.java.LazyGenericTypeProposal;
import org.eclipse.jdt.internal.ui.text.java.LazyJavaTypeCompletionProposal;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.gap.eclipse.recommenders.CorePlugin;
import org.gap.eclipse.recommenders.common.DisableCategoryJob;
import org.gap.eclipse.recommenders.common.JobFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

@SuppressWarnings("restriction")
public class SubTypeProposalComputer implements IJavaCompletionProposalComputer {
	// TODO: this class need heavy refactoring after testing the current
	// implementation.

	private static final String CATEGORY_ID = "gap.eclipse.recommenders.proposalCategory.subType";
	private Set<String> unsupportedTypes = Sets.newHashSet("java.lang.Object", "java.lang.Cloneable",
			"java.lang.Throwable", "java.lang.Exception");

	@Override
	public void sessionStarted() {
		disableOnDefaultTabIfNeeded();
	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		if (context instanceof JavaContentAssistInvocationContext) {
			if (((JavaContentAssistInvocationContext) context).getExpectedType() != null) {
				IType expectedType = ((JavaContentAssistInvocationContext) context).getExpectedType();
				Builder<ICompletionProposal> builder = ImmutableList.<ICompletionProposal>builder();
				builder.addAll(
						searchForStaticProposals(expectedType, (JavaContentAssistInvocationContext) context, monitor));
				builder.addAll(searchForSubTypeProposals(expectedType,
						(JavaContentAssistInvocationContext) context, monitor));
				return builder.build();
			} else {
				return searchFromAST((JavaContentAssistInvocationContext) context, monitor);
			}
		}
		return Collections.emptyList();
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
		CompletionASTVistor visitor = new CompletionASTVistor(context.getInvocationOffset(), context.getProject());
		ast.accept(visitor);

		final IType expectedType = visitor.getExpectedType();
		if (expectedType == null) {
			return Collections.emptyList();
		}

		try {
			if (expectedType.isEnum()) {
				Set<IField> literals = Arrays.stream(expectedType.getChildren()).filter(
						e -> (e.getElementType() == IJavaElement.FIELD) && !e.getElementName().equals("$VALUES"))
						.map(e -> (IField) e).collect(Collectors.toSet());
				return createEnumProposals(literals, context);
			} else {
				Builder<ICompletionProposal> builder = ImmutableList.<ICompletionProposal>builder();
				builder.addAll(searchForStaticProposals(expectedType, context, monitor));
				builder.addAll(searchForSubTypeProposals(expectedType, context, monitor));
				return builder.build();
			}
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
			return Collections.emptyList();
		}
	}

	private List<ICompletionProposal> searchForStaticProposals(IType expectedType,
			JavaContentAssistInvocationContext context, IProgressMonitor monitor) {
		final Set<IMember> result = Collections.synchronizedSet(new HashSet<>());
		final JobFuture future = JobFuture.forJob(Job.create("StaticCompletionSessionProcessor", new IJobFunction() {

			@Override
			public IStatus run(IProgressMonitor monitor) {
				try {
					Set<IMember> interResults = new HashSet<>();
					SearchEngine engine = new SearchEngine();
					SearchPattern pattern = SearchPattern.createPattern(expectedType.getFullyQualifiedName(),
							IJavaSearchConstants.TYPE,
							IJavaSearchConstants.FIELD_DECLARATION_TYPE_REFERENCE
									| IJavaSearchConstants.RETURN_TYPE_REFERENCE,
							SearchPattern.R_FULL_MATCH);

					engine.search(pattern,
							new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
							SearchEngine.createJavaSearchScope(new IJavaElement[] { context.getProject() },
									JavaSearchScope.REFERENCED_PROJECTS | JavaSearchScope.APPLICATION_LIBRARIES
											| JavaSearchScope.SYSTEM_LIBRARIES | JavaSearchScope.SOURCES),
							new SearchRequestor() {

								@Override
								public void acceptSearchMatch(SearchMatch match) throws CoreException {

									IMember element = (IMember) match.getElement();
									if (Flags.isStatic(element.getFlags()) && Flags.isPublic(element.getFlags())) {
										interResults.add(element);
									}
								}

							}, monitor);
					result.addAll(interResults);
				} catch (CoreException e) {
					CorePlugin.getDefault().logError("Error while searching static reference.", e);
				}
				return Status.OK_STATUS;
			}
		}));
		future.schedule();
		try {
			// if the search takes more than 4 seconds then return empty.
			future.get(10000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException | TimeoutException | ExecutionException e) {
			CorePlugin.getDefault().logInfo("Skipping due to type heirarchy build.");
		}

		return createForStatics(result, context, monitor);
	}

	private List<ICompletionProposal> createForStatics(Set<IMember> members, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		List<ICompletionProposal> proposals = new ArrayList<>();

		for (IMember member : members) {
			try {
				if (member instanceof IField) {
					proposals.add(createStaticFieldProposal((IField) member, context, monitor));
				} else if (member instanceof IMethod) {
					proposals.add(createStaticMethodProposal((IMethod) member, context, monitor));
				}
			} catch (JavaModelException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
			}
		}
		return proposals;
	}

	private ICompletionProposal createStaticFieldProposal(IField field, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) throws JavaModelException {

		CompletionProposal proposal = CompletionProposal.create(CompletionProposal.FIELD_REF,
				context.getInvocationOffset());
		String fullyQualifiedName = field.getDeclaringType().getElementName().concat(".")
				.concat(field.getElementName());
		proposal.setName(field.getElementName().toCharArray());
		proposal.setCompletion(fullyQualifiedName.toCharArray());
		proposal.setDeclarationSignature(
				Signature.createTypeSignature(field.getDeclaringType().getFullyQualifiedName(), true).toCharArray());
		proposal.setFlags(field.getFlags());
		float relevance = context.getHistoryRelevance(fullyQualifiedName);
		proposal.setRelevance((int) (50 * (relevance < 0.1 ? 0.1 : relevance)));
		proposal.setReplaceRange(context.getInvocationOffset(), context.getInvocationOffset());
		proposal.setSignature(field.getTypeSignature().toCharArray());
		proposal.setRequiredProposals(
				new CompletionProposal[] { createImportProposal(context, field.getDeclaringType()) });

		CompletionProposalCollector collector = new CompletionProposalCollector(context.getCompilationUnit());
		collector.setInvocationContext(context);
		collector.acceptContext(context.getCoreContext());
		collector.accept(proposal);

		return collector.getJavaCompletionProposals()[0];
	}

	private ICompletionProposal createStaticMethodProposal(IMethod method, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) throws JavaModelException {

		CompletionProposal proposal = CompletionProposal.create(CompletionProposal.METHOD_REF,
				context.getInvocationOffset());
		String fullyQualifiedName = method.getDeclaringType().getElementName().concat(".")
				.concat(method.getElementName());
		proposal.setName(method.getElementName().toCharArray());
		proposal.setCompletion(createMethodCompletion(method));
		proposal.setDeclarationSignature(
				Signature.createTypeSignature(method.getDeclaringType().getFullyQualifiedName(), true).toCharArray());
		proposal.setFlags(method.getFlags());
		float relevance = context.getHistoryRelevance(fullyQualifiedName);
		proposal.setRelevance((int) (100 * (relevance < 0.1 ? 0.1 : relevance)));
		proposal.setReplaceRange(context.getInvocationOffset(), context.getInvocationOffset());
		proposal.setSignature(method.getSignature().replaceAll("/", ".").toCharArray());
		proposal.setRequiredProposals(
				new CompletionProposal[] { createImportProposal(context, method.getDeclaringType()) });

		CompletionProposalCollector collector = new CompletionProposalCollector(context.getCompilationUnit());
		collector.setInvocationContext(context);
		collector.acceptContext(context.getCoreContext());
		collector.accept(proposal);

		return collector.getJavaCompletionProposals()[0];
	}

	private char[] createMethodCompletion(IMethod method) throws JavaModelException {
		StringBuilder builder = new StringBuilder();
		builder.append(method.getDeclaringType().getElementName());
		builder.append(".").append(method.getElementName());
		builder.append("(");
		builder.append(Arrays.stream(method.getParameterNames())
				.collect(Collectors.joining(",")));
		builder.append(")");
		return builder.toString().toCharArray();
	}

	private List<ICompletionProposal> searchForSubTypeProposals(IType expectedType,
			JavaContentAssistInvocationContext context, IProgressMonitor monitor) {

		if (unsupportedTypes.contains(expectedType.getFullyQualifiedName())) {
			return Collections.emptyList();
		}

		final Set<IType> subtypes = subtypes(expectedType, context.getProject());
		if (subtypes.isEmpty()) {
			return Collections.emptyList();
		}

		return createProposals(subtypes, context);
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

	private List<ICompletionProposal> createProposals(Set<IType> subtypes, JavaContentAssistInvocationContext context) {
		final ArrayList<ICompletionProposal> response = new ArrayList<>(subtypes.size());
		try {
			for (IType type : subtypes) {
				CompletionProposal proposal = CompletionProposal.create(CompletionProposal.TYPE_REF,
						context.getInvocationOffset());
				String fullyQualifiedName = type.getFullyQualifiedName();
				proposal.setCompletion(fullyQualifiedName.toCharArray());
				proposal.setDeclarationSignature(type.getPackageFragment().getElementName().toCharArray());
				proposal.setFlags(type.getFlags());
				float relevance = context.getHistoryRelevance(fullyQualifiedName);
				proposal.setRelevance((int) (10 * (relevance < 0.1 ? 0.1 : relevance)));
				proposal.setReplaceRange(context.getInvocationOffset(), context.getInvocationOffset());
				proposal.setSignature(Signature.createTypeSignature(fullyQualifiedName, true).toCharArray());

				if (supportGenerics(context.getProject())) {
					response.add(new LazyGenericTypeProposal(proposal, context));
				} else {
					response.add(new LazyJavaTypeCompletionProposal(proposal, context));
				}
			}
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError("Error occured while creating proposals.", e);
			response.trimToSize();
		}
		return response;
	}

	private boolean supportGenerics(IJavaProject project) {
		String source = project.getOption(JavaCore.COMPILER_SOURCE, true);
		return source != null && JavaCore.compareJavaVersions(JavaCore.VERSION_1_5, source) <= 0;
	}

	private Set<IType> subtypes(final IType expected, final IJavaProject project) {
		final Set<IType> result = Collections.synchronizedSet(new HashSet<>());
		final JobFuture future = JobFuture.forJob(Job.create("TypesCompletionSessionProcessor", new IJobFunction() {

			@Override
			public IStatus run(IProgressMonitor monitor) {
				try {
					Set<IType> interResults = new HashSet<>();
					IType[] subtypes = expected.newTypeHierarchy(monitor).getAllSubtypes(expected);
					for (IType subtype : subtypes) {
						interResults.add(subtype);
					}
					result.addAll(interResults);
				} catch (JavaModelException e) {
					CorePlugin.getDefault().logError("Error while searching type hierarchy.", e);
				}
				return Status.OK_STATUS;
			}
		}));
		future.schedule();
		try {
			// if the search takes more than 3 seconds then return empty.
			future.get(2000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException | TimeoutException | ExecutionException e) {
			CorePlugin.getDefault().logInfo("Skipping due to type heirarchy build.");
		}

		return ImmutableSet.copyOf(result);
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

		public CompletionASTVistor(int offset, IJavaProject project) {
			this.offset = offset;
			this.project = project;
		}

		@Override
		public boolean visit(MethodInvocation node) {
			if (offset > node.getStartPosition() && offset < (node.getStartPosition() + node.getLength())) {
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
					}
				} catch (JavaModelException e) {
					e.printStackTrace();
				}
			}
			return false;
		}

		private ITypeBinding findParameterTypeAtOffset(MethodInvocation node, IMethodBinding method) {
			@SuppressWarnings("unchecked")
			final List<Object> arguments = node.arguments();
			int typeIndex = -1;

			for (int i = 0; i < arguments.size(); i++) {
				final ASTNode astNode = (ASTNode) arguments.get(i);
				if (astNode.getStartPosition() <= offset
						&& (astNode.getStartPosition() + astNode.getLength()) >= offset) {
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
