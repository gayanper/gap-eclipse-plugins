package org.gap.eclipse.recommenders.types;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.ui.text.java.LazyGenericTypeProposal;
import org.eclipse.jdt.internal.ui.text.java.LazyJavaTypeCompletionProposal;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.gap.eclipse.recommenders.CorePlugin;

import com.google.common.base.Predicates;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

@SuppressWarnings("restriction")
public class SubTypeFinder {

	public Flux<ICompletionProposal> find(final IType expectedType, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		return Flux.<IType>create(e -> performSearch(e, expectedType, context, monitor))
				.subscribeOn(Schedulers.single())
				.map(m -> toCompletionProposal(m, context, monitor)).filter(Predicates.notNull());
	}

	private ICompletionProposal toCompletionProposal(IType type, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		try {
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
				return new LazyGenericTypeProposal(proposal, context);
			} else {
				return new LazyJavaTypeCompletionProposal(proposal, context);
			}
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
		return null;
	}

	private boolean supportGenerics(IJavaProject project) {
		String source = project.getOption(JavaCore.COMPILER_SOURCE, true);
		return source != null && JavaCore.compareJavaVersions(JavaCore.VERSION_1_5, source) <= 0;
	}

	private void performSearch(FluxSink<IType> emitter, IType expectedType, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		try {
			IType[] subtypes = expectedType.newTypeHierarchy(monitor).getAllSubtypes(expectedType);
			for (IType t : subtypes) {
				emitter.next(t);
			}
			emitter.complete();
		} catch (JavaModelException e) {
			emitter.error(e);
		}

	}
}
