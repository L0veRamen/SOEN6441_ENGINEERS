package services;

import models.SearchBlock;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionStage;

/**
 * Service for search orchestration
 * Coordinates API calls and business logic
 *
 * Responsibilities:
 * - Validate search inputs
 * - Call NewsApiClient
 * - Build SearchBlock
 * - NO direct API calls (delegates to NewsApiClient)
 *
 * @author [Your Name]
 */
@Singleton
public class SearchService {

    private final NewsApiClient newsApiClient;

    /**
     * Constructor with dependency injection
     *
     * @param newsApiClient API client for news data
     * @author Chen Qian
     */
    @Inject
    public SearchService(NewsApiClient newsApiClient) {
        this.newsApiClient = newsApiClient;
    }

    /**
     * Perform search and build SearchBlock
     *
     * @param query  Search query
     * @param sortBy Sort option
     * @return CompletionStage with SearchBlock
     * @author Chen Qian
     */
    public CompletionStage<SearchBlock> search(String query, String sortBy) {
        return newsApiClient.searchEverything(query, sortBy, 10)
                .thenApply(response -> new SearchBlock(
                        query,
                        sortBy,
                        response.totalResults(),
                        response.articles(),
                        ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
                ));
    }
}
