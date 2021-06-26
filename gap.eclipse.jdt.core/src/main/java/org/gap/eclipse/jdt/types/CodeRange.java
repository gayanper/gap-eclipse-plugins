package org.gap.eclipse.jdt.types;

import java.util.function.IntSupplier;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.MethodInvocation;

class CodeRange {
	private final int start;
	private final int end;
	private ASTNode node;

	public CodeRange(int start, int end, ASTNode node) {
		this.start = start;
		this.end = end;
		this.node = node;
	}

	public boolean inRange(int offset) {
		return (offset >= start) && (offset <= end) && withInValidRegion(offset);
	}

	public boolean inRange(CodeRange range) {
		return (range.start >= start) && (range.end <= end);
	}

	// This checks if the offset is right within the parentheses of constructor or
	// method.
	private boolean withInValidRegion(int offset) {
		if (node instanceof MethodInvocation) {
			return offset >= positionOfParentheses(() -> {
				MethodInvocation mi = (MethodInvocation) node;
				return mi.getName().getLength() + ((mi.getExpression() != null) ? mi.getExpression().getLength() : 0);
			});
		} else if (node instanceof ClassInstanceCreation) {
			return offset >= positionOfParentheses(() -> ((ClassInstanceCreation) node).getType().getLength());
		}
		return (start <= offset && end >= offset);
	}

	private int positionOfParentheses(IntSupplier nameLength) {
		// >MethodName(|<
		return node.getStartPosition() + nameLength.getAsInt();
	}
}