package org.gap.eclipse.jdt.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.gap.eclipse.jdt.CorePlugin;

import com.google.common.base.Predicates;

public class SmartEnumLiteralProposalComputer extends AbstractSmartProposalComputer
		implements IJavaCompletionProposalComputer {

	public static final String CATEGORY_ID = "gap.eclipse.jdt.proposalCategory.smartEnum";

	private final LastInvocation lastInvocation = new LastInvocation();

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
			if (context.getExpectedType() == null) {
				return searchFromAST((JavaContentAssistInvocationContext) context, monitor);
			}
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
		return visitor.getExpectedTypes().stream().parallel()
			.filter(t -> !isUnsupportedType(t.getFullyQualifiedName()))
			.flatMap(t -> {
					try {
						if(t.isInterface() && lastInvocation.canPerformSecondarySearch(context)) {
							ExecutorService executor = Executors.newSingleThreadExecutor();
							final Set<IType> types = Collections.synchronizedSet(new HashSet<>());
							
							Future<?> future = executor.submit(() -> {
								SearchPattern pattern = SearchPattern.createPattern(t, IJavaSearchConstants.IMPLEMENTORS);
								SearchEngine engine = new SearchEngine();
								try {
									engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant()}, 
											SearchEngine.createJavaSearchScope(new IJavaElement[] { context.getProject() }), new SearchRequestor() {

												@Override
												public void acceptSearchMatch(SearchMatch match) throws CoreException {
													if(match.getAccuracy() == SearchMatch.A_ACCURATE &&
															match.getElement() instanceof IType) {
														types.add((IType) match.getElement());
													}
												}
									}, monitor);
								} catch (CoreException e) {
									CorePlugin.getDefault().logError(e.getMessage(), e);
								}
							});
							
							try {
								if (isAsyncCompletionActive(context)) {
									future.get();
								} else {
									future.get(TIMEOUT, TimeUnit.SECONDS);
								}
							} catch (TimeoutException e) {
								lastInvocation.reset(); // we don't want a expanded search in next try.
							} catch (InterruptedException e) {
								CorePlugin.getDefault().logError(e.getMessage(), e);
								Thread.currentThread().interrupt();
							} catch (Exception e) {
								CorePlugin.getDefault().logError(e.getMessage(), e);
							} finally {
								executor.shutdown();
							}
							return types.stream();
						} else {
							return Stream.of(t);
						}
					} catch (CoreException e) {
						CorePlugin.getDefault().logError(e.getMessage(), e);
					}
					return Stream.empty();
				})
			.flatMap(t -> {
					try {
						if(t.isEnum()) {
							return createEnumProposals(context, t).stream();
						}
					} catch (JavaModelException e) {
						CorePlugin.getDefault().logError(e.getMessage(), e);
					}
					return null;
				})
			.filter(Predicates.notNull())
			.collect(Collectors.toList());
	}

	private List<ICompletionProposal> createEnumProposals(JavaContentAssistInvocationContext context,
			final IType expectedType) throws JavaModelException {
		Set<IField> literals = Arrays.stream(expectedType.getChildren())
				.filter(e -> {
					try {
						return (e.getElementType() == IJavaElement.FIELD) && Flags.isPublic(((IField)e).getFlags()) && !e.getElementName().equals("$VALUES");
					} catch (JavaModelException e1) {
						CorePlugin.getDefault().logError(e1.getMessage(), e1);
					}
					return false;
				})
				.map(e -> (IField) e).collect(Collectors.toSet());
		return createEnumProposals(literals, context);
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
				proposal.setReplaceRange(context.getInvocationOffset(), ContextUtils.computeEndOffset(context));
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
