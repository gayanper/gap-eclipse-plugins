package org.gap.eclipse.jdt.types;

import java.time.Duration;
import java.util.ArrayList;
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
import org.eclipse.jdt.internal.core.search.indexing.QualifierQuery;
import org.eclipse.jdt.internal.core.search.indexing.QualifierQuery.QueryCategory;
import org.eclipse.jdt.internal.core.search.matching.MatchLocator;
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
		boolean expandSubTypes = false;		
	
		if (lastInvocation.canPerformSecondarySearch(context)) {
			if(!lastInvocation.wasLastSecondarySearch()) {
				cachedSearchParticipant.resetCache();
			}
			expandSubTypes = true;
		}
	
		return performSearch(expectedTypeFQNs, context, monitor, timeout, expandSubTypes)
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
				return Proposals.toFieldProposal((IField) member, context);
			} else if (member instanceof IMethod) {
				return Proposals.toMethodProposal((IMethod) member, context);
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

	private boolean onlyPublicStatic(IMember member) {
		try {
			return Flags.isStatic(member.getFlags()) && Flags.isPublic(member.getFlags())
					&& Flags.isPublic(member.getDeclaringType().getFlags());
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
			return false;
		}
	}

	private boolean matchReturnTypeIfMethod(IMember member, List<String> typeSigs,
			JavaContentAssistInvocationContext context, IProgressMonitor monitor) {
		try {
			if (member instanceof IMethod) {
				String type = Signatures.getFullQualifiedResolvedReturnType((IMethod) member);

				for (String typeSig : typeSigs) {
					if (Signature.getTypeSignatureKind(type) == Signature.getTypeSignatureKind(typeSig)
							&& Signatures.isAssignable(type, typeSig)) {
						return true;
					} else if (isParameterized(type)) {
						IType leftType = context.getProject()
								.findType(Signature
										.getTypeErasure(String.join(".", Signature.getSignatureQualifier(typeSig),
												Signature.getSignatureSimpleName(typeSig))));
						IType rightType = context.getProject()
								.findType(Signature.getTypeErasure(
										String.join(".", String.join(".", Signature.getSignatureQualifier(type),
												Signature.getSignatureSimpleName(type)))));
						if (leftType != null && rightType != null
								&& rightType.newSupertypeHierarchy(monitor).contains(leftType)) {
							return true;
						}
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

	private boolean isParameterized(String type) {
		String[] ta = Signature.getTypeArguments(type);
		return Stream.of(ta).map(Signature::getTypeSignatureKind)
				.allMatch(k -> (k == Signature.WILDCARD_TYPE_SIGNATURE || k == Signature.TYPE_VARIABLE_SIGNATURE));
	}

	private boolean matchingElement(SearchMatch match) {
		return match.getElement() instanceof IMethod || match.getElement() instanceof IField;
	}

	@SuppressWarnings("deprecation")
	private Stream<IMember> performSearch(List<String> typeFQNs, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor, Duration timeout, boolean expandSubTypes) {
		final SearchJobTracker searchJobTracker = new SearchJobTracker();
		final SearchEngine engine = new SearchEngine();

		final ExecutorService executor = Executors.newSingleThreadExecutor();
		final Set<IMember> resultAccumerlator = Collections.synchronizedSet(new HashSet<>());
		
		Future<?> task = executor.submit(() -> {
			try {
				List<String> expectedTypeFQNs = new ArrayList<>(typeFQNs);
				if(expandSubTypes) {
					expectedTypeFQNs.addAll(expandSearchTypes(typeFQNs, context, monitor));
				}
				
				SearchPattern pattern = null;
				int searchInMask = JavaSearchScope.SYSTEM_LIBRARIES | JavaSearchScope.SOURCES
						| JavaSearchScope.REFERENCED_PROJECTS | JavaSearchScope.APPLICATION_LIBRARIES;

				final List<String> typeSigs = expectedTypeFQNs.isEmpty() ? Collections.emptyList()
						: expectedTypeFQNs.stream().map(f -> Signature.createTypeSignature(f, true))
								.collect(Collectors.toList());
				if (!expectedTypeFQNs.isEmpty()) {
					for (String fqn : expectedTypeFQNs) {
						SearchPattern p = SearchPattern.createPattern(fqn, IJavaSearchConstants.TYPE,
								IJavaSearchConstants.RETURN_TYPE_REFERENCE,
								SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_ERASURE_MATCH);
						MatchLocator.setIndexQualifierQuery(p,
								QualifierQuery.encodeQuery(new QueryCategory[] { QueryCategory.REF },
										Signature.getSimpleName(fqn).toCharArray(), fqn.toCharArray()));
						
						if(p != null) {
							pattern = pattern == null ? p : SearchPattern.createOrPattern(p, pattern);
						}
					}
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
											&& (typeSigs.isEmpty()
													|| matchReturnTypeIfMethod(member, typeSigs, context, monitor))) {
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
			if (timeout != null) {
				task.get(timeout.getSeconds(), TimeUnit.SECONDS);
			} else {
				task.get();
			}
		} catch (TimeoutException e) {
			// do nothing since we return what we have collected so far.
			lastInvocation.reset(); // we don't want a expanded search in next try.
			if(resultAccumerlator.isEmpty()) {
				resultAccumerlator.add(new MessageCompletionMember("Searching for static references ⌛"));
				resultAccumerlator.add(new MessageCompletionMember("Try again after static search finish"));
			}
		} catch (InterruptedException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		} finally {
			executor.shutdown();
		}

		// copy and create a stream.
		return new ArrayList<>(resultAccumerlator).stream().limit(100).parallel();
	}
}
