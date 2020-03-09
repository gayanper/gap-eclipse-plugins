package org.gap.eclipse.jdt.types;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
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
import org.gap.eclipse.jdt.CorePlugin;

import com.google.common.base.Predicates;

@SuppressWarnings("restriction")
public class SubTypeFinder {

	public Stream<ICompletionProposal> find(final IType expectedType, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor, Duration timeout) {
		return performSearch(expectedType, context, monitor, timeout)
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
			proposal.setReplaceRange(context.getInvocationOffset(), ContextUtils.computeEndOffset(context));
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

	private Stream<IType> performSearch(IType expectedType, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor,
			Duration timeout) {
		final List<IType> resultAccumerlator = Collections.synchronizedList(new ArrayList<>());
		final CountDownLatch waiter = new CountDownLatch(1);

		Job job = new Job("Static Member Search Caching") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IType[] subtypes = expectedType.newTypeHierarchy(monitor).getAllSubtypes(expectedType);
					resultAccumerlator.addAll(Arrays.asList(subtypes));
				} catch (JavaModelException e) {
					CorePlugin.getDefault().logError(e.getMessage(), e);
				} finally {
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
		return new ArrayList<>(resultAccumerlator).stream(); // copy and create the stream.
	}
}
