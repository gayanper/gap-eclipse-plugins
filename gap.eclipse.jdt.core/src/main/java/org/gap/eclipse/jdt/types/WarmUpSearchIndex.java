package org.gap.eclipse.jdt.types;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.ui.IStartup;

public class WarmUpSearchIndex implements IStartup {

	@Override
	public void earlyStartup() {
		Job job = Job.create(Messages.WarmUpSearchIndex_Message, new ICoreRunnable() {

			@Override
			public void run(IProgressMonitor monitor) throws CoreException {
				SearchPattern warmupPattern = SearchPattern.createPattern("*", IJavaSearchConstants.METHOD, //$NON-NLS-1$
						IJavaSearchConstants.DECLARATIONS, SearchPattern.R_PATTERN_MATCH);
				SearchEngine engine = new SearchEngine();
				engine.search(warmupPattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
						SearchEngine.createWorkspaceScope(), new SearchRequestor() {
							@Override
							public void acceptSearchMatch(SearchMatch match) throws CoreException {
							}

							@Override
							public void endReporting() {
							}

						}, monitor);
			}
		});
		job.schedule();
	}
}
