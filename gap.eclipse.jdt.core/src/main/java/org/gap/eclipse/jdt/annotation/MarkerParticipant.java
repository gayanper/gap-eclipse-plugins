package org.gap.eclipse.jdt.annotation;

import java.util.Arrays;
import java.util.stream.Stream;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.BuildContext;
import org.eclipse.jdt.core.compiler.CompilationParticipant;
import org.eclipse.jdt.core.compiler.ReconcileContext;
import org.gap.eclipse.jdt.CorePlugin;

public class MarkerParticipant extends CompilationParticipant {
	@Override
	public void reconcile(ReconcileContext context) {
		if (context.getASTLevel() != ICompilationUnit.NO_AST) {
			try {
				context.getAST(context.getASTLevel()).accept(new RecursiveASTVistor(context));
			} catch (JavaModelException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
			}
		}
	}

	@Override
	public boolean isActive(IJavaProject project) {
		return true;
	}

	@Override
	public boolean isAnnotationProcessor() {
		return false;
	}

	@Override
	public void buildStarting(BuildContext[] files, boolean isBatch) {
		Arrays.stream(files).parallel().flatMap(this::findMarkers).forEach(this::deleteMarker);
	}

	private void deleteMarker(IMarker marker) {
		try {
			marker.delete();
		} catch (CoreException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
	}

	private Stream<? extends IMarker> findMarkers(BuildContext context) {
		try {
			return Arrays.stream(context.getFile().findMarkers(Markers.MARKER_ID, false, IResource.DEPTH_ONE));
		} catch (CoreException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
			return Stream.empty();
		}
	}
}
