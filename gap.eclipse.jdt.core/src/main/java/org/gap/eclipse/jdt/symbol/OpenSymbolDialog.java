package org.gap.eclipse.jdt.symbol;

import java.util.Comparator;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;
import org.gap.eclipse.jdt.CorePlugin;

public class OpenSymbolDialog extends FilteredItemsSelectionDialog {
	private static final String DIALOG_SETTINGS = "org.gap.eclipse.jdt.symbol.OpenSymbolDialog";
	private final SearchEngine engine = new SearchEngine();

	public OpenSymbolDialog(Shell shell) {
		super(shell);
		setListLabelProvider(new SymbolLabelProvider());
	}

	@Override
	protected Control createExtendedContentArea(Composite parent) {
		return null;
	}

	@Override
	protected IDialogSettings getDialogSettings() {
		IDialogSettings settings = CorePlugin.getDefault().getDialogSettings().getSection(DIALOG_SETTINGS);
		if (settings == null) {
			settings = CorePlugin.getDefault().getDialogSettings().addNewSection(DIALOG_SETTINGS);
		}
		return settings;
	}

	@Override
	protected IStatus validateItem(Object item) {
		if (item instanceof IMember) {
			return Status.OK_STATUS;
		}
		return new Status(Status.ERROR, CorePlugin.PLUGIN_ID, "");
	}

	@Override
	protected ItemsFilter createFilter() {
		return new SymbolsFilter();
	}

	@Override
	protected Comparator<?> getItemsComparator() {
		return new SymbolComparator();
	}

	@Override
	protected void fillContentProvider(AbstractContentProvider contentProvider, ItemsFilter itemsFilter,
			IProgressMonitor monitor) throws CoreException {
		monitor.setTaskName("Searching for symbols");

		SearchPattern methodpPattern = SearchPattern.createPattern(itemsFilter.getPattern(),
				IJavaSearchConstants.METHOD, IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_PREFIX_MATCH);

		SearchPattern fieldPattern = SearchPattern.createPattern(itemsFilter.getPattern(), IJavaSearchConstants.FIELD,
				IJavaSearchConstants.DECLARATIONS, SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_PREFIX_MATCH);

		engine.search(SearchPattern.createOrPattern(methodpPattern, fieldPattern),
				new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
				SearchEngine.createWorkspaceScope(), new SymbolRequester(contentProvider, itemsFilter), monitor);
	}

	@Override
	public String getElementName(Object item) {
		if (item == null) {
			return "";
		}
		IMember member = (IMember) item;
		return member.getElementName();
	}

	private static class SymbolRequester extends SearchRequestor {
		private AbstractContentProvider contentProvider;
		private ItemsFilter itemsFilter;
		private boolean methodsFound = false, fieldsFound = false;

		public SymbolRequester(AbstractContentProvider contentProvider, ItemsFilter itemsFilter) {
			this.contentProvider = contentProvider;
			this.itemsFilter = itemsFilter;
		}

		@Override
		public void acceptSearchMatch(SearchMatch match) throws CoreException {
			contentProvider.add(match.getElement(), itemsFilter);
		}

		@Override
		public void beginReporting() {
			methodsFound = false;
			fieldsFound = false;
			super.beginReporting();
		}
		
		@Override
		public void endReporting() {
			((SymbolsFilter)itemsFilter).setResultHasBothTypes(methodsFound && fieldsFound);
			super.endReporting();
		}
	}

	private class SymbolsFilter extends ItemsFilter {

		private boolean resultHasBothTypes;

		@Override
		public boolean matchItem(Object item) {
			IMember member = (IMember) item;

			return patternMatcher.matches(member.getElementName());
		}

		@Override
		public boolean isConsistentItem(Object item) {
			return true;
		}

		@Override
		public boolean isSubFilter(ItemsFilter filter) {
			return super.isSubFilter(filter) && resultHasBothTypes;
		}

		public void setResultHasBothTypes(boolean resultHasBothTypes) {
			this.resultHasBothTypes = resultHasBothTypes;
		}
	}

}
