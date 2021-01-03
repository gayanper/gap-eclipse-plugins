package org.gap.eclipse.jdt.types;

import java.util.Collections;
import java.util.StringJoiner;

import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.link.LinkedPositionGroup;
import org.eclipse.swt.graphics.Image;
import org.gap.eclipse.jdt.CorePlugin;
import org.gap.eclipse.jdt.common.Images;

public class LambdaCompletionProposal extends LazyJavaCompletionProposal {

	private final String[] parameterNames;
	private final boolean inline;
	private final int[] paramOffsets;
	private final int[] paramLengths;

	protected LambdaCompletionProposal(String[] parameterNames, boolean inline, int relevance,
			JavaContentAssistInvocationContext context) {
		super(generateDisplayString(parameterNames.length, inline), relevance, context);
		this.parameterNames = parameterNames;
		this.inline = inline;

		paramOffsets = new int[parameterNames.length];
		paramLengths = new int[parameterNames.length];
	}

	private static String generateDisplayString(int paraCount, boolean inline) {
		StringBuilder builder = new StringBuilder("(");
		builder.append(String.join("", Collections.nCopies(paraCount, ".")));
		builder.append(") ->");

		if (!inline) {
			builder.append(" {}");
		}
		return builder.toString();
	}

	@Override
	public Image getImage() {
		return CorePlugin.getDefault().getImageRegistry().get(Images.OBJ_LAMBDA);
	}

	@Override
	protected String computeReplacementString() {

		StringBuilder completionString = new StringBuilder(4 + parameterNames.length);

		if (parameterNames.length != 1) {
			completionString.append("(");
		}

		int baseOffset = completionString.length();
		StringJoiner joiner = new StringJoiner(",");
		for (int i = 0; i < parameterNames.length; i++) {
			String para = parameterNames[i];
			if (i > 0) {
				paramOffsets[i] = baseOffset + paramLengths[i - 1] + 1;
			} else {
				paramOffsets[i] = baseOffset;
			}
			paramLengths[i] = para.length();
			joiner.add(para);
		}
		completionString.append(joiner.toString());

		if (parameterNames.length != 1) {
			completionString.append(")");
		}
		completionString.append(" -> ");

		if (!inline) {
			completionString.append(" {}");
		}

		return completionString.toString();
	}

	@Override
	protected int getCursorPosition() {
		return inline ? super.getCursorPosition() : super.getCursorPosition() - 1;
	}

	@Override
	protected LinkedModeModel computeLinkedModeModel(IDocument document) throws BadLocationException {
		LinkedModeModel model = new LinkedModeModel();

		for (int i = 0; i < paramOffsets.length; i++) {
			LinkedPositionGroup group = new LinkedPositionGroup();
			group.addPosition(new LinkedPosition(document, getReplacementOffset() + paramOffsets[i], paramLengths[i],
					LinkedPositionGroup.NO_STOP));
			model.addGroup(group);
		}
		return model;
	}
}
