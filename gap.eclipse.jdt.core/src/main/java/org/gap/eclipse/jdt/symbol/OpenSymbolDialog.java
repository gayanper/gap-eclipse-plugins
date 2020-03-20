package org.gap.eclipse.jdt.symbol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.ui.search.JavaSearchScopeFactory;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.actions.WorkingSetFilterActionGroup;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;
import org.gap.eclipse.jdt.CorePlugin;

@SuppressWarnings("restriction")
public class OpenSymbolDialog extends FilteredItemsSelectionDialog {
	private static final String DIALOG_SETTINGS = "org.gap.eclipse.jdt.symbol.OpenSymbolDialog";
	private final SearchEngine engine = new SearchEngine();
	private WorkingSetFilterActionGroup filterActionGroup;
	private IWorkingSet workingSet = null;

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
		return new SymbolsFilter(workingSet);
	}

	@Override
	protected Comparator<?> getItemsComparator() {
		return new SymbolComparator();
	}

	@Override
	protected void fillViewMenu(IMenuManager menuManager) {
		super.fillViewMenu(menuManager);

		filterActionGroup = new WorkingSetFilterActionGroup(getShell(), new IPropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent event) {
				workingSet = (IWorkingSet) event.getNewValue();
				applyFilter();
			}
		});
		filterActionGroup.fillContextMenu(menuManager);
	}

	private boolean isEmptyWorkingSet() {
		return workingSet == null || (workingSet.isAggregateWorkingSet() && workingSet.isEmpty());
	}

	@Override
	protected void fillContentProvider(AbstractContentProvider contentProvider, ItemsFilter itemsFilter,
			IProgressMonitor monitor) throws CoreException {
		monitor.setTaskName("Searching for symbols");

		final SymbolsFilter filter = (SymbolsFilter) itemsFilter;
		IJavaSearchScope scope;
		String memberName;

		final String pattern = filter.getPattern();
		if (isQualifiedPattern(pattern)) {
			final String parentType = pattern.substring(0, pattern.lastIndexOf('.')).replace('$', '.');
			IType type = searchForType(parentType, monitor);
			if (type == null) {
				return;
			}

			scope = SearchEngine.createHierarchyScope(type);
			if (!isEmptyWorkingSet()) {
				scope = new AndJavaSearchScope(scope,
						JavaSearchScopeFactory.getInstance().createJavaSearchScope(filter.getWorkingSet(), true));
			}
			memberName = pattern.substring(pattern.lastIndexOf('.') + 1);
			filter.setFullyQualifiedSearch(true);
		} else {
			if (isEmptyWorkingSet()) {
				scope = SearchEngine.createWorkspaceScope();
			} else {
				scope = JavaSearchScopeFactory.getInstance().createJavaSearchScope(filter.getWorkingSet(), true);
			}
			memberName = pattern;
			filter.setFullyQualifiedSearch(false);
		}

		SearchPattern methodPattern = SearchPattern.createPattern(memberName, IJavaSearchConstants.METHOD,
				IJavaSearchConstants.DECLARATIONS, SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_PREFIX_MATCH);

		SearchPattern fieldPattern = SearchPattern.createPattern(memberName, IJavaSearchConstants.FIELD,
				IJavaSearchConstants.DECLARATIONS, SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_PREFIX_MATCH);

		engine.search(SearchPattern.createOrPattern(methodPattern, fieldPattern),
				new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope,
				new SymbolRequester(contentProvider, itemsFilter), monitor);
	}

	private IType searchForType(String parentType, IProgressMonitor monitor) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(parentType, IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS, SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_FULL_MATCH);
		List<IType> result = new ArrayList<>(1);
		engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
				SearchEngine.createWorkspaceScope(), new SearchRequestor() {

					@Override
					public void acceptSearchMatch(SearchMatch match) throws CoreException {
						if ((match.getAccuracy() == SearchMatch.A_ACCURATE) && (match.getElement() instanceof IType)) {
							result.add((IType) match.getElement());
						}
					}
				}, monitor);

		return result.isEmpty() ? null : result.get(0);
	}

	private boolean isQualifiedPattern(String pattern) {
		return pattern.contains(".");
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
			((SymbolsFilter) itemsFilter).setResultHasBothTypes(methodsFound && fieldsFound);
			super.endReporting();
		}
	}

	private class SymbolsFilter extends ItemsFilter {

		private boolean resultHasBothTypes;
		private boolean fullyQualifiedSearch;
		private IWorkingSet workingSet;

		public SymbolsFilter(IWorkingSet workingSet) {
			this.workingSet = workingSet;
		}

		@Override
		public boolean matchItem(Object item) {
			IMember member = (IMember) item;
			final String content = fullyQualifiedSearch ? fqn(member) : member.getElementName();
			return patternMatcher.matches(content);
		}

		private String fqn(IMember member) {
			return JavaElementLabels.getElementLabel(member, JavaElementLabels.F_FULLY_QUALIFIED
					| JavaElementLabels.M_FULLY_QUALIFIED | JavaElementLabels.M_PARAMETER_TYPES);
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

		public void setFullyQualifiedSearch(boolean fullyQualifiedSearch) {
			this.fullyQualifiedSearch = fullyQualifiedSearch;
		}

		@Override
		public boolean equalsFilter(ItemsFilter filter) {
			return ((SymbolsFilter) filter).workingSet == this.workingSet && super.equalsFilter(filter);
		}

		public IWorkingSet getWorkingSet() {
			return workingSet;
		}
	}

}
