package org.gap.eclipse.jdt.hyperlinks;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.ui.search.JavaSearchQuery;
import org.eclipse.jdt.ui.search.ElementQuerySpecification;
import org.eclipse.jdt.ui.search.QuerySpecification;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.search.ui.NewSearchUI;
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
		return Messages.Hyperlink_FindReference;
	}

	@Override
	public void open() {
		NewSearchUI.runQueryInBackground(new JavaSearchQuery(createQuery(element)));
	}
	
	private QuerySpecification createQuery(IJavaElement element) {
		return new ElementQuerySpecification(element, IJavaSearchConstants.REFERENCES,
				SearchEngine.createWorkspaceScope(), Messages.Hyperlink_SearchDescription);
	}
	
}
