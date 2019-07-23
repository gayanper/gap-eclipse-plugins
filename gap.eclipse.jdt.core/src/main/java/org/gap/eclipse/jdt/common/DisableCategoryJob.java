package org.gap.eclipse.jdt.common;

import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

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
		disableInCategoryOrder();
		// This should be last call to reload the completion registry.
		disableInExcludedCategories();
		return Status.OK_STATUS;
	}

	private void disableInExcludedCategories() {
		String[] excluded = PreferenceConstants.getExcludedCompletionProposalCategories();
		Set<String> current = Sets.newHashSet(excluded);
		current.add(categoryId);
		String[] newExcluded = current.toArray(new String[0]);
		PreferenceConstants.setExcludedCompletionProposalCategories(newExcluded);
	}

	private void disableInCategoryOrder() {
		String encodedPreference = PreferenceConstants.getPreference(PreferenceConstants.CODEASSIST_CATEGORY_ORDER,
				null);
		StringTokenizer tokenizer = new StringTokenizer(encodedPreference, "\0"); //$NON-NLS-1$
		Set<String> result = Sets.newHashSetWithExpectedSize(tokenizer.countTokens());
		boolean found = false;
		String subtypeToken = categoryId + ":65536";
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if (token.startsWith(categoryId)) {
				token = subtypeToken;
				found = true;
			}
			result.add(token);
		}
		if (!found) {
			result.add(subtypeToken);
		}
		PreferenceConstants.getPreferenceStore().setValue(PreferenceConstants.CODEASSIST_CATEGORY_ORDER,
				result.stream().collect(Collectors.joining("\0")));
	}

}
