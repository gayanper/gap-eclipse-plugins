package org.gap.eclipse.jdt.types;

import java.util.Arrays;
import java.util.stream.Collectors;

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

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@SuppressWarnings("restriction")
public class StaticMemberFinder {

	public Flux<ICompletionProposal> find(final IType expectedType, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		return Flux.create(e -> performSearch(e, expectedType, context, monitor))
				.map(o -> (IMember) o)
				.filter(this::onlyPublicStatic).map(m -> toCompletionProposal(m, context, monitor))
				.filter(Predicates.notNull());
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

	private void performSearch(FluxSink<Object> emitter, IType expectedType, JavaContentAssistInvocationContext context,
			IProgressMonitor parentMonitor) {
		SearchEngine engine = new SearchEngine();
		SearchPattern pattern = SearchPattern.createPattern(expectedType.getFullyQualifiedName(),
				IJavaSearchConstants.TYPE,
				IJavaSearchConstants.FIELD_DECLARATION_TYPE_REFERENCE | IJavaSearchConstants.RETURN_TYPE_REFERENCE,
				SearchPattern.R_EXACT_MATCH);

		Job job = new Job("Static Member Search Caching") {

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
									if (!emitter.isCancelled() && (match.getAccuracy() == SearchMatch.A_ACCURATE)) {
										emitter.next(match.getElement());
									}
								}

								@Override
								public void endReporting() {
									emitter.complete();
								}

							}, monitor);
				} catch (CoreException e) {
					if (!emitter.isCancelled()) {
						emitter.error(e);
					}
				}
				return Status.OK_STATUS;
			}
		};
		job.setPriority(Job.INTERACTIVE);
		job.schedule();
	}
}
