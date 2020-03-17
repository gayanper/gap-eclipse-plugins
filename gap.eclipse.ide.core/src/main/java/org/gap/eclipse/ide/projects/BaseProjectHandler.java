package org.gap.eclipse.ide.projects;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.progress.IProgressService;
import org.gap.eclipse.ide.CorePlugin;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

public abstract class BaseProjectHandler extends AbstractHandler {

	private boolean useProgressService;

	public BaseProjectHandler(boolean useProgressService) {
		this.useProgressService = useProgressService;
	}

	public BaseProjectHandler() {
		this(true);
	}

	@Override
	public final Object execute(ExecutionEvent event) throws ExecutionException {
		final IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);

		final ISelectionService selectionService = window.getSelectionService();
		final IStructuredSelection selection = ((IStructuredSelection) selectionService.getSelection());
		final List<IProject> selectionProjects = resolve(selection);

		if (useProgressService) {
			final IProgressService progressService = window.getWorkbench().getProgressService();
			try {
				progressService.run(true, false, pm -> executeOnSelection(selectionProjects, pm, window));
			} catch (InvocationTargetException | InterruptedException e) {
				CorePlugin.getDefault().logError(String.format("Job [%s] interrupted or ended with an error", getClass().getName()), e);
			}
		} else {
			executeOnSelection(selectionProjects, null, window);
		}
		return null;
	}

	private void executeOnSelection(List<IProject> projects, IProgressMonitor pm, IWorkbenchWindow window) {
		List<IProject> projectsToOperate = new ArrayList<>(projects);
		projects.forEach(p -> projectsToOperate.addAll(NestedProjectWalker.walkerFor(p).toList()));
		executeOperation(projectsToOperate, pm, window);
	}

	protected abstract void executeOperation(List<IProject> projects, IProgressMonitor pm, IWorkbenchWindow window);

	@SuppressWarnings("unchecked")
	protected List<IProject> resolve(IStructuredSelection selection) {
		List<IProject> projects = new ArrayList<>();
		selection.forEach(p -> projects.add((IProject) p));
		return projects;
	}

	protected void log(CoreException e, IProject p) {
		CorePlugin.getDefault().logError(String.format("Error closing project %s", p.getName()), e);
	}
}
