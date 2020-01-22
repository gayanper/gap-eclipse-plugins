package org.gap.eclipse.jdt.types;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
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

import com.google.common.base.Predicates;

@SuppressWarnings("restriction")
public class StaticMemberFinder {

	public Stream<ICompletionProposal> find(final IType expectedType, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor, Duration timeout) {
		return performSearch(expectedType, context, monitor, timeout)
				.map(m -> toCompletionProposal(m, context, monitor))
				.filter(Predicates.notNull());
	}

	private boolean isMatching(IMember member, IType expectedType) {
		try {
			if (member instanceof IMethod) {
				return isMatchingMethod((IMethod) member, expectedType);
			} else if (member instanceof IField) {
				return isMatchingField((IField) member, expectedType);
			}
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
		return false;
	}

	private boolean isMatchingField(IField field, IType expectedType) throws JavaModelException {
		final String erasureSignature = Signature.getTypeErasure(field.getTypeSignature());
		return resolvedTypeName(field, erasureSignature).filter(expectedType.getFullyQualifiedName()::equals)
				.isPresent();
	}

	private boolean isMatchingMethod(IMethod method, IType expectedType) throws JavaModelException {
		final String erasureSignature = Signature.getTypeErasure(method.getReturnType());
		return resolvedTypeName(method, erasureSignature)
				.filter(expectedType.getFullyQualifiedName()::equals)
				.isPresent();
	}

	private Optional<String> resolvedTypeName(IMember member, String signature)
			throws JavaModelException
	{
		final String packageName = Signature.getSignatureQualifier(signature);
		final String className = Signature.getSignatureSimpleName(signature);
		final String fqn = packageName.isEmpty() ? className : packageName.concat(".").concat(className);
		final IType declaringType = member.getDeclaringType();
		return Optional.ofNullable(declaringType.resolveType(fqn, null)).map(v -> v[0]).map(e -> String.join(".", e));
	}

	private ICompletionProposal toCompletionProposal(IMember member, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		try {
			if (member instanceof IField) {
				return createStaticFieldProposal((IField) member, context, monitor);
			} else if (member instanceof IMethod) {
				return createStaticMethodProposal((IMethod) member, context, monitor);
			}
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
		return null;
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
			return Flags.isStatic(member.getFlags()) && Flags.isPublic(member.getFlags());
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
			return false;
		}
	}

	private Stream<IMember> performSearch(IType expectedType, JavaContentAssistInvocationContext context,
			IProgressMonitor parentMonitor, Duration timeout) {
		SearchEngine engine = new SearchEngine();
		SearchPattern pattern = SearchPattern.createPattern(fixInnerType(expectedType.getFullyQualifiedName()),
				IJavaSearchConstants.TYPE, IJavaSearchConstants.REFERENCES, SearchPattern.R_ERASURE_MATCH);

		final List<IMember> resultAccumerlator = Collections.synchronizedList(new ArrayList<>());
		final CountDownLatch waiter = new CountDownLatch(1);

		final Job job = new Job("Static Member Search Caching") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
							SearchEngine.createJavaSearchScope(new IJavaElement[] { context.getProject() },
									JavaSearchScope.REFERENCED_PROJECTS | JavaSearchScope.APPLICATION_LIBRARIES
											| JavaSearchScope.SYSTEM_LIBRARIES | JavaSearchScope.SOURCES),
							new SearchRequestor() {
								@Override
								public void acceptSearchMatch(SearchMatch match) throws CoreException {
									if (match.getAccuracy() == SearchMatch.A_ACCURATE && match.isExact()
											&& (match.getElement() instanceof IMember)) {
										final IMember member = (IMember) match.getElement();
										if(onlyPublicStatic(member) && isMatching(member, expectedType)) {
											resultAccumerlator.add((IMember) match.getElement());
										}
									}
								}

								@Override
								public void endReporting() {
									waiter.countDown();
								}

							}, monitor);
				} catch (CoreException e) {
					CorePlugin.getDefault().logError(e.getMessage(), e);
					waiter.countDown();
				}
				return Status.OK_STATUS;
			}
		};
		job.setPriority(Job.INTERACTIVE);
		job.schedule();
		try {
			waiter.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
		// copy and create a stream.
		return new ArrayList<>(resultAccumerlator).stream();
	}

	private String fixInnerType(String fqn) {
		return fqn.replace('$', '.');
	}
}
