package org.gap.eclipse.jdt.types;

import java.util.concurrent.CountDownLatch;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.gap.eclipse.jdt.Messages;

class SearchJobTracker {
	private CountDownLatch latch;
	private Job job;

	public void startTracking() {
		latch = new CountDownLatch(1);
		job = Job.create(Messages.SearchJobTracker_JobName, new ICoreRunnable() {

			@Override
			public void run(IProgressMonitor monitor) throws CoreException {
				try {
					latch.await();
				} catch (InterruptedException e) {
				}
			}
		});
		job.schedule();
	}

	public void finishTracking() {
		if (latch == null) {
			return;
		}

		latch.countDown();
		latch = null;
	}
}
