package org.gap.eclipse.ide.projects;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.internal.ide.dialogs.CleanDialog;

@SuppressWarnings("restriction")
public class CleanNestedProjectHandler extends BaseProjectHandler {
	public CleanNestedProjectHandler() {
		super(false);
	}
	
	@Override
	protected void executeOperation(List<IProject> projects, IProgressMonitor pm, IWorkbenchWindow window) {
		CleanDialog cleanDialog = new CleanDialog(window, projects.toArray(new IProject[0]));
		cleanDialog.open();
	}

}
