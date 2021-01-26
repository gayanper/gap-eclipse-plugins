package org.gap.eclipse.jdt.types;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.EditorHighlightingSynchronizer;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedModeUI;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI;
import org.gap.eclipse.jdt.common.Log;

@SuppressWarnings("restriction")
public abstract class LazyJavaCompletionProposal implements IJavaCompletionProposal {
	private final int relevance;
	private final JavaContentAssistInvocationContext context;
	private final String displayString;

	private Point selection;
	private String replacementString;
	private int cursorPosition;
	private int offset;

	protected LazyJavaCompletionProposal(String displayString, int relevance,
			JavaContentAssistInvocationContext context) {
		this.displayString = displayString;
		this.relevance = relevance;
		this.context = context;
	}

	@Override
	public void apply(IDocument document) {
		try {
			String token = Proposals.getToken(context);
			String replacementStr = getReplacementString();
			offset = ContextUtils.computeInvocationOffset(context);
			int length = ContextUtils.computeReplacementLength(context);

			if (!token.isEmpty()) {
				if (replacementStr.startsWith(token)) {
					replacementStr = replacementStr.substring(token.length());
				} else {
					offset -= token.length();
				}
			}
			this.replacementString = replacementStr;

			this.cursorPosition = offset + replacementStr.length();

			document.replace(offset, length, replacementStr);

			if (isSupportLinkMode()) {
				LinkedModeModel model = computeLinkedModeModel(document);

				model.forceInstall();
				JavaEditor editor = getEditor();
				if (editor != null) {
					model.addLinkingListener(new EditorHighlightingSynchronizer(editor));
				}

				final ITextViewer viewer = this.context.getViewer();
				LinkedModeUI ui = new EditorLinkedModeUI(model, viewer);
				ui.setExitPosition(viewer, getCursorPosition(), 0, Integer.MAX_VALUE);
				ui.setDoContextInfo(true);
				ui.setCyclingMode(LinkedModeUI.CYCLE_WHEN_NO_PARENT);
				ui.enter();
				IRegion selectedRegion = ui.getSelectedRegion();
				selection = new Point(selectedRegion.getOffset(), selectedRegion.getLength());
			}
		} catch (BadLocationException e) {
			Log.error(e);
		}
	}

	@Override
	public Point getSelection(IDocument document) {
		if (selection == null) {
			selection = new Point(getReplacementOffset() + getReplacementString().length(), 0);
		}
		return selection;
	}

	protected int getReplacementOffset() {
		return offset;
	}

	protected int getCursorPosition() {
		return cursorPosition;
	}

	protected abstract String computeReplacementString();

	protected abstract LinkedModeModel computeLinkedModeModel(IDocument document) throws BadLocationException;

	@Override
	public String getAdditionalProposalInfo() {
		return null;
	}

	@Override
	public String getDisplayString() {
		return this.displayString;
	}

	@Override
	public IContextInformation getContextInformation() {
		return null;
	}

	@Override
	public int getRelevance() {
		return relevance;
	}

	protected String getReplacementString() {
		if (this.replacementString == null) {
			this.replacementString = computeReplacementString();
		}
		return this.replacementString;
	}

	protected boolean isSupportLinkMode() {
		return true;
	}

	private JavaEditor getEditor() {
		IEditorPart editorPart = JavaPlugin.getActivePage().getActiveEditor();
		if (editorPart instanceof JavaEditor) {
			return (JavaEditor) editorPart;
		}
		return null;
	}
}
