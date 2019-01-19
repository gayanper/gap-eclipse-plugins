package org.gap.eclipse.recommenders.common;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;

import com.google.common.util.concurrent.AbstractFuture;

public class JobFuture extends AbstractFuture<IStatus> implements IJobChangeListener {
	private Job job;

	public static JobFuture forJob(Job job) {
		return new JobFuture(job);
	}

	private JobFuture(Job job) {
		super();
		this.job = job;
		this.job.addJobChangeListener(this);
	}

	@Override
	public void aboutToRun(IJobChangeEvent var1) {

	}

	@Override
	public void awake(IJobChangeEvent var1) {

	}

	@Override
	public void done(IJobChangeEvent event) {
		this.job.removeJobChangeListener(this);
		if (Status.OK_STATUS.equals(event.getResult())) {
			set(event.getResult());
		} else {
			setException(event.getResult().getException());
		}
	}

	@Override
	public void running(IJobChangeEvent var1) {
	}

	@Override
	public void scheduled(IJobChangeEvent var1) {

	}

	@Override
	public void sleeping(IJobChangeEvent var1) {

	}

	@Override
	protected void interruptTask() {
		job.cancel();
	}

	public void schedule() {
		job.schedule();
	}
}
