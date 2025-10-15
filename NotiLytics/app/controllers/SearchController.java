package controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.SearchHistoryService;
import services.SearchService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for search operations
 * Handles search requests and redirects
 *
 * Flow:
 * 1. Validate inputs (query param)
 * 2. Call SearchService to perform search
 * 3. Push SearchBlock to SearchHistoryService
 * 4. Redirect to home page (POST-Redirect-GET pattern)
 *
 * @author Chen Qian
 */
@Singleton
public class SearchController extends Controller {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;
    private final SearchHistoryService historyService;

    /**
     * Constructor with dependency injection
     *
     * @param searchService  Service for search logic
     * @param historyService Service for session history
     * @author Chen Qian
     */
    @Inject
    public SearchController(SearchService searchService, SearchHistoryService historyService) {
        this.searchService = searchService;
        this.historyService = historyService;
    }

    /**
     * Handle search request
     * Extracts query parameters, performs search, updates session history
     * <p>
     * Query Parameters:
     * - q (required): search query
     * - sortBy (optional): publishedAt (default), relevancy, or popularity
     *
     * @param request HTTP request with query params and session
     * @return Async redirect to home page
     * @author Chen Qian
     */
    public CompletableFuture<Result> search(Http.Request request) {
        // Extract query parameters
        String query = request.queryString("q").orElse("").trim();
        String sortBy = request.queryString("sortBy").orElse("publishedAt").trim();

        // Validate query parameter
        if (query.isEmpty()) {
            log.warn("Search attempted with empty query");
            return java.util.concurrent.CompletableFuture.completedFuture(
                    badRequest("Search query is required")
            );
        }

        // Validate sortBy parameter
        if (!isValidSortBy(sortBy)) {
            log.warn("Invalid sortBy parameter: {}", sortBy);
            sortBy = "publishedAt"; // Default to publishedAt
        }

        log.info("Search request: query='{}' sortBy='{}'", query, sortBy);

        // Get or create session ID
        String sessionId = getOrCreateSessionId(request);

        // Perform search and update history
        String finalSortBy = sortBy;
        return searchService.search(query, sortBy)
                .thenApply(searchBlock -> {
                    // Add to session history
                    historyService.push(sessionId, searchBlock);
                    log.info("Search completed for session '{}': {} results",
                            sessionId, searchBlock.totalResults());

                    // Redirect to home page with session cookie
                    return redirect("/Notilytics")
                            .addingToSession(request, "sessionId", sessionId);
                })
                .exceptionally(throwable -> {
                    log.error("Search failed for query '{}': {}", query, throwable.getMessage(), throwable);
                    // Redirect to home with error (could be enhanced with flash message)
                    return redirect("/Notilytics")
                            .addingToSession(request, "sessionId", sessionId);
                }).toCompletableFuture();
    }

    /**
     * Extract or create session ID from Play session
     * Generates new UUID if not present
     *
     * @param request HTTP request
     * @return Session ID string
     * @author Chen Qian
     */
    private String getOrCreateSessionId(Http.Request request) {
        return request.session().get("sessionId")
                .orElse(java.util.UUID.randomUUID().toString());
    }

    /**
     * Validate sortBy parameter
     * Only allows: publishedAt, relevancy, popularity
     *
     * @param sortBy Sort option to validate
     * @return true if valid, false otherwise
     * @author Chen Qian
     */
    private boolean isValidSortBy(String sortBy) {
        return "publishedAt".equals(sortBy)
                || "relevancy".equals(sortBy)
                || "popularity".equals(sortBy);
    }
}


