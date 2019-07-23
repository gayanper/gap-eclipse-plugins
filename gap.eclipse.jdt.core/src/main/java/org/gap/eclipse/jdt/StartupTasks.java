package org.gap.eclipse.jdt;

import org.eclipse.ui.IStartup;
import org.gap.eclipse.jdt.common.DisableCategoryJob;
import org.gap.eclipse.jdt.types.SubTypeProposalComputer;

public class StartupTasks implements IStartup {

	@Override
	public void earlyStartup() {
		DisableCategoryJob.forCategory(SubTypeProposalComputer.CATEGORY_ID).schedule();
	}
}
