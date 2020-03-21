
package org.gap.eclipse.jdt.debug;

import java.io.FileWriter;
import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IViewActionDelegate;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PlatformUI;
import org.gap.eclipse.jdt.CorePlugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class DumpVariableAction implements IViewActionDelegate {

	private ISelection selection;

	private JsonGenerator generator = new JsonGenerator();

	public DumpVariableAction() {
	}

	@Override
	public void run(IAction action) {
		if (!selection.isEmpty() && selection instanceof TreeSelection) {
			TreeSelection varSel = ((TreeSelection) selection);
			Object obj = varSel.getFirstElement();
			if (obj instanceof IJavaVariable) {
				try {
					final IJavaVariable variable = (IJavaVariable) obj;
					final JsonObject jsonObject = generator.construct(variable);
					saveToFile(jsonObject, fileName(variable));
				} catch (CoreException e) {
					CorePlugin.getDefault().logError("Error constructing JSON String", e);
				}
			}
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		this.selection = selection;
	}

	@Override
	public void init(IViewPart view) {
	}

	private String fileName(IJavaVariable variable) throws DebugException {
		return String.format("debug_dump_%s_%s.json", variable.getReferenceTypeName(), variable.getName());
	}

	private void saveToFile(JsonObject jsonObject, String fileName) {
		FileDialog dialog = new FileDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), SWT.SAVE);
		dialog.setOverwrite(true);
		dialog.setFileName(fileName);
		dialog.setText("Dump Variable");
		String nameToSave = dialog.open();

		if (nameToSave != null) {
			Gson gson = new Gson();
			try (FileWriter writer = new FileWriter(nameToSave)) {
				gson.toJson(jsonObject, writer);
			} catch (IOException e) {
				CorePlugin.getDefault().logError("Error writing file to disk", e);
			}
		}

	}
}
