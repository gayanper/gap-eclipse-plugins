package org.gap.eclipse.jdt.hyperlinks;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.core.manipulation.search.OccurrencesFinder;
import org.eclipse.jdt.internal.ui.search.FindOccurrencesEngine;
import org.eclipse.jdt.internal.ui.search.JavaSearchQuery;
import org.eclipse.jdt.ui.search.ElementQuerySpecification;
import org.eclipse.jdt.ui.search.QuerySpecification;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.search.ui.NewSearchUI;
import org.gap.eclipse.jdt.CorePlugin;
import org.gap.eclipse.jdt.Messages;

@SuppressWarnings("restriction")
class JavaReferenceHyperlink implements IHyperlink {

	private IRegion region;
	
	private IJavaElement element;

	public JavaReferenceHyperlink(IRegion region, IJavaElement element) {
		this.region = region;
		this.element = element;
	}

	@Override
	public IRegion getHyperlinkRegion() {
		return region;
	}

	@Override
	public String getTypeLabel() {
		return null;
	}

	@Override
	public String getHyperlinkText() {
		return Messages.Hyperlink_OpenReference;
	}

	@Override
	public void open() {
		if(element instanceof IMember) {
			try {
				final IMember member = (IMember) element;
				if(Flags.isPrivate(member.getFlags())) {
					findOccurenceInFile(member);
				} else {
					NewSearchUI.runQueryInBackground(new JavaSearchQuery(createQuery(element)));
				}
			} catch (JavaModelException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
			}
		} else {
			NewSearchUI.runQueryInBackground(new JavaSearchQuery(createQuery(element)));
		}
	}
	
	private void findOccurenceInFile(IMember member) {
		FindOccurrencesEngine engine= FindOccurrencesEngine.create(new OccurrencesFinder());
		try {
			ISourceRange range= member.getNameRange();
			engine.run(member.getTypeRoot(), range.getOffset(), range.getLength());
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
	}

	private QuerySpecification createQuery(IJavaElement element) {
		return new ElementQuerySpecification(element, IJavaSearchConstants.REFERENCES,
				SearchEngine.createWorkspaceScope(), Messages.Hyperlink_SearchDescription);
	}
	
}
