package models;

import java.util.List;

/**
 * Represents a single search result block (10 articles)
 * Immutable record stored in session history
 *
 * @param query         Search query string
 * @param sortBy        Sort option (publishedAt|relevancy|popularity)
 * @param totalResults  Total results available from API
 * @param articles      List of 10 articles
 * @param createdAtIso  When this search was performed
 *
 * @author Chen Qian
 */
public record SearchBlock(
        String query,
        String sortBy,
        int totalResults,
        List<Article> articles,
        String createdAtIso
) {}
