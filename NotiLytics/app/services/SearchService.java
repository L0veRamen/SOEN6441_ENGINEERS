package services;

import models.ReadabilityScores;
import models.SearchBlock;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

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
    private final ReadabilityService readabilityService;  // ADDED for Task E
    
    /**
     * Constructor with dependency injection
     *
     * @param newsApiClient API client for news data
     * @param readabilityService Service for readability analysis
     * @author Chen Qian
     */
    @Inject
    public SearchService(NewsApiClient newsApiClient, ReadabilityService readabilityService) {
        this.newsApiClient = newsApiClient;
        this.readabilityService = readabilityService;  // ADDED for Task E
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
                .thenApply(response -> {
                    
                    // Calculate readability scores (Task E)
                    ReadabilityScores averageReadability  = readabilityService.calculateAverageReadability(
                            response.articles()
                    );
                    
                    // OPTIONAL: Calculate individual scores
                    List<ReadabilityScores> individualScores = response.articles().stream()
                            .map(readabilityService::calculateArticleReadability)
                            .collect(Collectors.toList());
                    
                    return new SearchBlock(
                            query,
                            sortBy,
                            response.totalResults(),
                            response.articles(),
                            ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                            averageReadability ,  // ADDED for Task E
                            individualScores  // Add this field to SearchBlock
                    );
                });
    }
}
