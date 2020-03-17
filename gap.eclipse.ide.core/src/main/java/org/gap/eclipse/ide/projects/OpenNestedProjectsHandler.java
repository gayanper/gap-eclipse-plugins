package org.gap.eclipse.ide.projects;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ui.IWorkbenchWindow;

public class OpenNestedProjectsHandler extends BaseProjectHandler {

	@Override
	protected void executeOperation(List<IProject> projects, IProgressMonitor pm, IWorkbenchWindow window) {
		projects.forEach(p -> {
			if (!p.isOpen()) {
				try {
					p.open(pm);
				} catch (CoreException e) {
					log(e, p);
				}
			}
		});
	}
}