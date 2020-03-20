package org.gap.eclipse.jdt.symbol;

import java.util.HashSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.search.IJavaSearchScope;

import com.google.common.collect.Sets;

class AndJavaSearchScope implements IJavaSearchScope {
	private final IJavaSearchScope left, right;
	
	public AndJavaSearchScope(IJavaSearchScope left, IJavaSearchScope right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public boolean encloses(String resourcePath) {
		return left.encloses(resourcePath) && right.encloses(resourcePath);
	}

	@Override
	public boolean encloses(IJavaElement element) {
		return left.encloses(element) && right.encloses(element);
	}

	@Override
	public IPath[] enclosingProjectsAndJars() {
		final HashSet<IPath> leftPAJ = Sets.newHashSet(left.enclosingProjectsAndJars());
		final HashSet<IPath> rightPAJ = Sets.newHashSet(left.enclosingProjectsAndJars());
		return Sets.intersection(leftPAJ, rightPAJ).toArray(new IPath[0]);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean includesBinaries() {
		return left.includesBinaries() && right.includesBinaries();
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean includesClasspaths() {
		return left.includesClasspaths() && right.includesClasspaths();
	}

	@Override
	public void setIncludesBinaries(boolean includesBinaries) {
	}

	@Override
	public void setIncludesClasspaths(boolean includesClasspaths) {
	}
}
