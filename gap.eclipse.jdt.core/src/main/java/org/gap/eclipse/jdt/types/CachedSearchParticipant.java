package org.gap.eclipse.jdt.types;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.gap.eclipse.jdt.CorePlugin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class CachedSearchParticipant extends SearchParticipant {
	private List<String> lastType;
	
	private String lastToken;
	
	private boolean useCache;
	
	private Cache<SearchMatch, SearchMatch> cache = CacheBuilder.newBuilder().softValues().build();

	private SearchParticipant participant;

	public CachedSearchParticipant(SearchParticipant participant) {
		this.participant = participant;
	}

	public void indexDocument(SearchDocument document, IPath indexPath) {
		participant.indexDocument(document, indexPath);
	}

	public void locateMatches(SearchDocument[] indexMatches, SearchPattern pattern, IJavaSearchScope scope,
			SearchRequestor requestor, IProgressMonitor monitor) throws CoreException {
		if (useCache) {
			Collection<SearchMatch> values = cache.asMap().values();
			if(values.isEmpty()) {
				participant.locateMatches(indexMatches, pattern, scope, requestor, monitor);
				return;
			}
			requestor.beginReporting();
			values.stream()
			.filter(m -> m.getElement() instanceof IMember)
			.filter(m -> ((IMember)m.getElement()).getElementName().startsWith(lastToken))
			.forEach(v -> {
				try {
					requestor.acceptSearchMatch(v);
				} catch (CoreException e) {
					CorePlugin.getDefault().logError(e.getMessage(), e);
				}
			});
			requestor.endReporting();
		} else {
			cache.invalidateAll();
			participant.locateMatches(indexMatches, pattern, scope, requestor, monitor);
		}
	}

	public IPath[] selectIndexes(SearchPattern pattern, IJavaSearchScope scope) {
		return participant.selectIndexes(pattern, scope);
	}

	@Override
	public SearchDocument getDocument(String documentPath) {
		return participant.getDocument(documentPath);
	}
	
	public void cacheMatch(SearchMatch match) {
		cache.put(match, match);
	}
	
	public void beforeSearch(List<String> expectedTypeFQNs, String token) {
		useCache = (lastType != null && lastType.equals(expectedTypeFQNs) && 
				lastToken != null && (lastToken.equals(token) || (!lastToken.isEmpty() && token.startsWith(lastToken))));
		this.lastToken = token;
		this.lastType = expectedTypeFQNs;
	}
	
	public void resetCache() {
		lastToken = null;
		lastType = null;
	}
}
