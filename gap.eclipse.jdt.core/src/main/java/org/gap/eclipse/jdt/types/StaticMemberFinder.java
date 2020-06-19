package org.gap.eclipse.jdt.types;

import java.time.Duration;
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
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.search.JavaSearchScope;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.gap.eclipse.jdt.CorePlugin;
import org.gap.eclipse.jdt.common.Signatures;

import com.google.common.base.Predicates;

@SuppressWarnings("restriction")
public class StaticMemberFinder {

	private final LastInvocation lastInvocation = new LastInvocation();

	private CachedSearchParticipant cachedSearchParticipant = new CachedSearchParticipant(
			new FilteredSearchParticipant(SearchEngine.getDefaultSearchParticipant()));

	public Stream<ICompletionProposal> find(final List<String> expectedTypeFQNs,
			JavaContentAssistInvocationContext context, IProgressMonitor monitor, Duration timeout) {
		boolean extendedSearch = context.getCoreContext().getToken() != null
				&& context.getCoreContext().getToken().length > 0;
	
		if (lastInvocation.canPerformSecondarySearch(context)) {
			if(!lastInvocation.wasLastSecondarySearch()) {
				cachedSearchParticipant.resetCache();
			}
			
			extendedSearch = true;
		}
	
		return performSearch(expectedTypeFQNs, context, monitor, timeout, extendedSearch)
				.map(m -> toCompletionProposal(m, context, monitor)).filter(Predicates.notNull());
	}

	private List<String> expandSearchTypes(List<String> expectedTypeFQNs, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		final IJavaProject project = context.getCompilationUnit().getJavaProject();
		return expectedTypeFQNs.stream().parallel().flatMap(type -> {
			try {
				IType foundType = project.findType(Signature.getTypeErasure(type), monitor);
				return Stream.of(foundType.newTypeHierarchy(project, monitor).getAllSubtypes(foundType))
						.filter(t -> Signatures.isNoOfTypeParametersEqual(t,type));
			} catch (CoreException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
			}
			return Stream.empty();
		}).map(t -> {
			try {
				return t.getFullyQualifiedParameterizedName().replace('$', '.');
			} catch (CoreException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
				return null;
			}
		}).filter(Predicates.notNull()).collect(Collectors.toList());
	}

	private ICompletionProposal toCompletionProposal(IMember member, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		try {
			if (member instanceof IField) {
				return createStaticFieldProposal((IField) member, context, monitor);
			} else if (member instanceof IMethod) {
				return createStaticMethodProposal((IMethod) member, context, monitor);
			} else if (member instanceof MessageCompletionMember) {
				return createMessageProposal((MessageCompletionMember) member);
			}
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
		return null;
	}

	private ICompletionProposal createMessageProposal(MessageCompletionMember member) {
		return new MessageCompletionProposal(member.getElementName());
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
		proposal.setReplaceRange(context.getInvocationOffset(), ContextUtils.computeEndOffset(context));
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
		proposal.setReplaceRange(ContextUtils.computeInvocationOffset(context), ContextUtils.computeEndOffset(context));
		proposal.setSignature(method.getSignature().replaceAll("/", ".").toCharArray());
		proposal.setRequiredProposals(
				new CompletionProposal[] { createImportProposal(context, method.getDeclaringType()) });

		CompletionProposalCollector collector = new CompletionProposalCollector(context.getCompilationUnit());
		collector.setInvocationContext(context);
		collector.acceptContext(context.getCoreContext());
		collector.accept(proposal);

		return collector.getJavaCompletionProposals()[0];
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

	private char[] createMethodCompletion(IMethod method) throws JavaModelException {
		StringBuilder builder = new StringBuilder();
		builder.append(method.getDeclaringType().getElementName());
		builder.append(".").append(method.getElementName());
		builder.append("(");
		builder.append(Arrays.stream(method.getParameterNames()).collect(Collectors.joining(",")));
		builder.append(")");
		return builder.toString().toCharArray();
	}

	private boolean onlyPublicStatic(IMember member) {
		try {
			return Flags.isStatic(member.getFlags()) && Flags.isPublic(member.getFlags())
					&& Flags.isPublic(member.getDeclaringType().getFlags());
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
			return false;
		}
	}

	private boolean matchReturnTypeIfMethod(IMember member, List<String> typeSigs) {
		try {
			if (member instanceof IMethod) {
				String type = Signatures.getFullQualifiedResolvedReturnType((IMethod) member);

				for (String typeSig : typeSigs) {
					if (Signature.getTypeSignatureKind(type) == Signature.getTypeSignatureKind(typeSig)
							&& Signatures.isAssignable(type, typeSig)) {
						return true;
					}
				}
				return false;
			}
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
			return false;
		}
		return true;
	}

	private boolean matchingElement(SearchMatch match) {
		return match.getElement() instanceof IMethod || match.getElement() instanceof IField;
	}

	@SuppressWarnings("deprecation")
	private Stream<IMember> performSearch(List<String> typeFQNs, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor, Duration timeout, boolean extendedSearch) {
		final SearchJobTracker searchJobTracker = new SearchJobTracker();
		final SearchEngine engine = new SearchEngine();

		final ExecutorService executor = Executors.newSingleThreadExecutor();
		final Set<IMember> resultAccumerlator = Collections.synchronizedSet(new HashSet<>());
		
		Future<?> task = executor.submit(() -> {
			try {
				List<String> expectedTypeFQNs = new ArrayList<>(typeFQNs);
				if(extendedSearch) {
					expectedTypeFQNs.addAll(expandSearchTypes(typeFQNs, context, monitor));
				}
				
				SearchPattern pattern = null;
				int searchInMask = JavaSearchScope.SYSTEM_LIBRARIES | JavaSearchScope.SOURCES;

				final List<String> typeSigs = expectedTypeFQNs.isEmpty() ? Collections.emptyList()
						: expectedTypeFQNs.stream().map(f -> Signature.createTypeSignature(f, true))
								.collect(Collectors.toList());
				if (!expectedTypeFQNs.isEmpty()) {
					for (String fqn : expectedTypeFQNs) {
						SearchPattern p = SearchPattern.createPattern(fqn, IJavaSearchConstants.TYPE,
								IJavaSearchConstants.RETURN_TYPE_REFERENCE,
								SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_ERASURE_MATCH);
						if(p != null) {
							pattern = pattern == null ? p : SearchPattern.createOrPattern(p, pattern);
						}
					}
				}

				if (extendedSearch) {
					searchInMask = searchInMask | JavaSearchScope.REFERENCED_PROJECTS | JavaSearchScope.APPLICATION_LIBRARIES;
				}

				if (context.getCoreContext().getToken() != null && context.getCoreContext().getToken().length > 0) {
					SearchPattern tokenPattern = SearchPattern.createPattern(
							new String(context.getCoreContext().getToken()).concat("*"), IJavaSearchConstants.METHOD,
							IJavaSearchConstants.DECLARATIONS, SearchPattern.R_PATTERN_MATCH);
					if (pattern != null) {
						pattern = SearchPattern.createAndPattern(tokenPattern, pattern);
					} else {
						pattern = tokenPattern;
					}
				}

				// refactor code to avoid this hack.
				if (pattern == null) {
					return;
				}

				final SearchPattern finalPattern = pattern;
				final int includeMask = searchInMask;


				cachedSearchParticipant.beforeSearch(expectedTypeFQNs, new String(context.getCoreContext().getToken()));
				
				searchJobTracker.startTracking();
				engine.search(finalPattern, new SearchParticipant[] { cachedSearchParticipant },
						SearchEngine.createJavaSearchScope(new IJavaElement[] { context.getProject() }, includeMask),
						new SearchRequestor() {
							@Override
							public void acceptSearchMatch(SearchMatch match) throws CoreException {
								cachedSearchParticipant.cacheMatch(match);
								if (matchingElement(match)) {
									final IMember member = (IMember) match.getElement();
									if (onlyPublicStatic(member)
											&& (typeSigs.isEmpty() || matchReturnTypeIfMethod(member, typeSigs))) {
										resultAccumerlator.add((IMember) match.getElement());
									}
								}
							}

							@Override
							public void endReporting() {
								searchJobTracker.finishTracking();
							}

						}, monitor);
			} catch (CoreException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
			}
		});

		try {
			task.get(timeout.getSeconds(), TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			// do nothing since we return what we have collected so far.
			lastInvocation.reset(); // we don't want a expanded search in next try.
			if(resultAccumerlator.isEmpty()) {
				resultAccumerlator.add(new MessageCompletionMember("Searching for static references ⌛"));
				resultAccumerlator.add(new MessageCompletionMember("Try again after static search finish"));
			}
		} catch (Exception e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		} finally {
			executor.shutdown();
		}

		// copy and create a stream.
		return new ArrayList<>(resultAccumerlator).stream().limit(100).parallel();
	}
}
