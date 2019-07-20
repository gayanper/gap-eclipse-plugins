package org.gap.eclipse.jdt.annotation;

import java.util.Optional;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.compiler.ReconcileContext;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.gap.eclipse.jdt.CorePlugin;

class RecursiveASTVistor extends ASTVisitor {
	private Optional<IMethodBinding> currentMethod = Optional.empty();
	private ReconcileContext context;

	public RecursiveASTVistor(ReconcileContext context) {
		this.context = context;
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		currentMethod = Optional.of(node.resolveBinding());
		return super.visit(node);
	}

	@Override
	public void endVisit(MethodDeclaration node) {
		currentMethod = Optional.empty();
		super.endVisit(node);
	}

	@Override
	public boolean visit(MethodInvocation node) {
		if (currentMethod.isPresent()) {
			IMethodBinding binding = node.resolveMethodBinding();
			if ((binding != null) && binding.equals(currentMethod.get())) {
				createRecursiveMarker(node, context.getWorkingCopy().getResource());
			}
		}

		return super.visit(node);
	}

	private void createRecursiveMarker(MethodInvocation node, IResource resource) {
		try {
			IMarker marker = resource.createMarker(Markers.MARKER_ID);
			marker.setAttribute(IMarker.CHAR_START, node.getStartPosition());
			marker.setAttribute(IMarker.CHAR_END, node.getStartPosition() + node.getLength());
			marker.setAttribute(IMarker.MESSAGE, "Recursion of method " + node.getName() + "()");
		} catch (CoreException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
	}
}
