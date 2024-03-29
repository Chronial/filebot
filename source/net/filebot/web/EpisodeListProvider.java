
package net.filebot.web;


import java.net.URI;
import java.util.List;
import java.util.Locale;

import javax.swing.Icon;


public interface EpisodeListProvider {
	
	public String getName();
	
	
	public Icon getIcon();
	
	
	public boolean hasSingleSeasonSupport();
	
	
	public boolean hasLocaleSupport();
	
	
	public List<SearchResult> search(String query, Locale locale) throws Exception;
	
	
	public List<Episode> getEpisodeList(SearchResult searchResult, SortOrder order, Locale locale) throws Exception;
	
	
	public URI getEpisodeListLink(SearchResult searchResult);
	
}
