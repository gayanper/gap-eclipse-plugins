package org.gap.eclipse.recommenders.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobFunction;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.ui.text.java.LazyGenericTypeProposal;
import org.eclipse.jdt.internal.ui.text.java.LazyJavaTypeCompletionProposal;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.gap.eclipse.recommenders.CorePlugin;
import org.gap.eclipse.recommenders.common.DisableCategoryJob;
import org.gap.eclipse.recommenders.common.JobFuture;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

@SuppressWarnings("restriction")
public class SubTypeProposalComputer implements IJavaCompletionProposalComputer {
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
			return searchForSubTypeProposals((JavaContentAssistInvocationContext) context, monitor);
		}
		return Collections.emptyList();
	}

	protected void disableOnDefaultTabIfNeeded() {
		final Set<String> excluded = Sets.newHashSet(PreferenceConstants.getExcludedCompletionProposalCategories());
		if (!excluded.contains(CATEGORY_ID)) {
			DisableCategoryJob.forCategory(CATEGORY_ID).schedule(300);
		}
	}

	private List<ICompletionProposal> searchForSubTypeProposals(
			JavaContentAssistInvocationContext context, IProgressMonitor monitor) {

		if (context.getExpectedType() == null) {
			return Collections.emptyList();
		}

		final IType expectedType = context.getExpectedType();
		if (unsupportedTypes.contains(expectedType.getFullyQualifiedName())) {
			return Collections.emptyList();
		}

		final Set<IType> subtypes = subtypes(expectedType, context.getProject());
		if (subtypes.isEmpty()) {
			return Collections.emptyList();
		}

		return createProposals(subtypes, context);
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
		return "Error occurred while looking subtypes";
	}

	@Override
	public void sessionEnded() {

	}

}
