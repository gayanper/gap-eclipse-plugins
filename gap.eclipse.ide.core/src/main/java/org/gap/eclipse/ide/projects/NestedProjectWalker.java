package org.gap.eclipse.ide.projects;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;

final class NestedProjectWalker {
	private IPath rootLocation;

	private NestedProjectWalker(IProject root) {
		this.rootLocation = root.getLocation();
	}

	public static NestedProjectWalker walkerFor(IProject root) {
		return new NestedProjectWalker(root);
	}

	public List<IProject> toList() {
		List<IProject> projects = new ArrayList<>();
		for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (isNested(p)) {
				projects.add(p);
			}
		}
		return projects;
	}

	private boolean isNested(IProject p) {
		final IPath pLocation = p.getLocation();
		return (((pLocation.segmentCount() - rootLocation.segmentCount()) > 0) && rootLocation.isPrefixOf(pLocation));
	}
}
