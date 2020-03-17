package org.gap.eclipse.jdt.quickfixes;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;

final class ASTHelper {
	private ASTHelper() {
	}
	
	public static BodyDeclaration findParentBodyDeclaration(ASTNode node) {
		while ((node != null) && (!(node instanceof BodyDeclaration))) {
			node = node.getParent();
		}
		return (BodyDeclaration) node;
	}

}
