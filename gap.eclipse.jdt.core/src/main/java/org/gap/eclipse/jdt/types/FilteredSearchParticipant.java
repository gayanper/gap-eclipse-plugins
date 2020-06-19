package org.gap.eclipse.jdt.types;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import com.google.common.base.Predicates;

public class FilteredSearchParticipant extends SearchParticipant {
	private SearchParticipant participant;
	
	public FilteredSearchParticipant(SearchParticipant participant) {
		super();
		this.participant = participant;
	}

	@Override
	public SearchDocument getDocument(String documentPath) {
		return participant.getDocument(documentPath);
	}

	@Override
	public void indexDocument(SearchDocument document, IPath indexLocation) {
		participant.indexDocument(document, indexLocation);
	}

	@Override
	public void locateMatches(SearchDocument[] documents, SearchPattern pattern, IJavaSearchScope scope,
			SearchRequestor requestor, IProgressMonitor monitor) throws CoreException {
		List<SearchDocument> prioDocuments = new LinkedList<SearchDocument>();
		Arrays.stream(documents).filter(searchInDocuments())
			.forEach(d -> {
				if(d.getPath().contains("|java/util") || d.getPath().contains("|java/util/stream") ||
						d.getPath().contains("|com/google/common/collect")) {
					prioDocuments.add(0, d);
				} else {
					prioDocuments.add(d);
				}
			});
		participant.locateMatches(prioDocuments.toArray(new SearchDocument[0]), pattern, scope, requestor, monitor);
	}

	private Predicate<SearchDocument> searchInDocuments() {
		return Predicates.<SearchDocument>not((SearchDocument d) -> {
			return d.getPath().contains("|sun") ||
					d.getPath().contains("|com/sun") ||
					d.getPath().contains("|com/oracle") ||
					d.getPath().contains("|jdk");
		});
	}
	
	@Override
	public IPath[] selectIndexes(SearchPattern query, IJavaSearchScope scope) {
		return participant.selectIndexes(query, scope);
	}

}
