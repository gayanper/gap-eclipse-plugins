package org.gap.eclipse.jdt.types;

class CodeRange {
	private final int start;
	private final int end;

	public CodeRange(int start, int end) {
		this.start = start;
		this.end = end;
	}

	public boolean inRange(int offset) {
		return (offset >= start) && (offset <= end);
	}

	public boolean inRange(CodeRange range) {
		return (range.start >= start) && (range.end <= end);
	}
}