package org.gap.eclipse.jdt.symbol;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.SelectionDialog;
import org.gap.eclipse.jdt.CorePlugin;

public class OpenSymbolCommand extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		SelectionDialog selectionDialog = new OpenSymbolDialog(
				CorePlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getShell());
		selectionDialog.setTitle("Open Symbol");

		final int result = selectionDialog.open();
		if (result == IStatus.OK) {
			IMember selected = (IMember) selectionDialog.getResult()[0];
			try {
				JavaUI.openInEditor(selected, true, true);
			} catch (PartInitException | JavaModelException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
			}
		}
		return null;
	}
}
