package org.gap.eclipse.recommenders.common;

import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.ui.progress.UIJob;

import com.google.common.collect.Sets;

public class DisableCategoryJob extends UIJob {
	private String categoryId;

	private DisableCategoryJob(String categoryId) {
		super("Disable Completion Category Job");
		this.categoryId = categoryId;
	}

	public static DisableCategoryJob forCategory(String categoryId) {
		return new DisableCategoryJob(categoryId);
	}

	@Override
	public IStatus runInUIThread(IProgressMonitor monitor) {
		String[] excluded = PreferenceConstants.getExcludedCompletionProposalCategories();
		Set<String> current = Sets.newHashSet(excluded);
		current.add(categoryId);
		String[] newExcluded = current.toArray(new String[0]);
		PreferenceConstants.setExcludedCompletionProposalCategories(newExcluded);
		return Status.OK_STATUS;
	}

}
