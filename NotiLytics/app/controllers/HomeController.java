package controllers;

/**
 * Single unified controller for the NotiLytics application.
 * This controller handles all HTTP requests including:
 * - Home page display with search history
 * - Search operations
 * - Source profile display
 *
 * Responsibilities:
 * - Render home page with search history
 * - Handle search requests and manage session
 * - Display source profile pages
 * - Extract and manage session IDs from Play session
 * - NO business logic (thin controller - delegates to services)
 *
 * @author Group
 */

import models.SearchBlock;
import models.SourceProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.ProfileService;
import services.SearchHistoryService;
import services.SearchService;
import services.SourcesService;
import models.SourceItem;
import views.html.sources;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class HomeController extends Controller {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final SearchService searchService;
    private final SearchHistoryService historyService;
    private final ProfileService profileService;
    private final SourcesService sourcesService;

    /**
     * Constructor with dependency injection.
     * Injects all required services for controller operations.
     *
     * @param searchService  Service for search logic
     * @param historyService Service for session history
     * @param profileService Service for source profile operations
     * @author Chen Qian
     */
    @Inject
    public HomeController(SearchService searchService,
                          SearchHistoryService historyService,
                          ProfileService profileService,SourcesService sourcesService) {
        this.searchService = searchService;
        this.historyService = historyService;
        this.profileService = profileService;
        this.sourcesService = sourcesService;
    }

    /**
     * Display home page with search form and history.
     * Retrieves search history for current session and renders home view.
     *
     * @param request HTTP request with session
     * @return Async result with rendered home view
     * @author Chen Qian
     */
    public CompletionStage<Result> index(Http.Request request) {
        String sessionId = getSessionId(request);
        List<SearchBlock> history = historyService.list(sessionId);

        // Pass both searchHistory and request to the template
        return CompletableFuture.completedFuture(
                ok(views.html.home.render(history, request))
        );
    }

    /**
     * Handle search request.
     * Extracts query parameters, performs search, updates session history,
     * and redirects to home page using POST-Redirect-GET pattern.
     *
     * Flow:
     * 1. Validate inputs (query param)
     * 2. Call SearchService to perform search
     * 3. Push SearchBlock to SearchHistoryService
     * 4. Redirect to home page (POST-Redirect-GET pattern)
     *
     * Query Parameters:
     * - q (required): search query
     * - sortBy (optional): publishedAt (default), relevancy, or popularity
     *
     * @param request HTTP request with query params and session
     * @return Async redirect to home page
     * @author Group
     */
    public CompletableFuture<Result> search(Http.Request request) {
        // Extract query parameters
        String query = request.queryString("q").orElse("").trim();
        String sortBy = request.queryString("sortBy").orElse("publishedAt").trim();

        // Validate query parameter
        if (query.isEmpty()) {
            log.warn("Search attempted with empty query");
            return CompletableFuture.completedFuture(
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
     * Display source profile page.
     * Shows details about a news source and articles from that source.
     *
     * @param request HTTP request with session
     * @param query   Name or id of the source
     * @return Async result with rendered profile view
     * @author Yuhao Ma
     */
    public CompletionStage<Result> source(Http.Request request, String query) {
        var result = profileService.search(query);
        return result.thenApply(res -> {
            if (res.source() == null) {
                var source = new SourceProfile();
                source.name = query;
                return ok(views.html.profile.render(source, res.articles()));
            }
            return ok(views.html.profile.render(res.source(), res.articles()));
        });
    }
    /**
     * Displays the News Sources page.
     * Filters sources by country, category, and language if provided.
     * Calls the SourcesService asynchronously and renders the result page.
     *
     * @param request  current HTTP request
     * @param country  country filter (e.g., "us")
     * @param category category filter (e.g., "business")
     * @param language language filter (e.g., "en")
     * @return async HTTP result with the sources page
     * @author Yang Zhang
     */
    public CompletionStage<Result> sources(Http.Request request,
                                           String country,
                                           String category,
                                           String language) {
        Optional<String> oc   = Optional.ofNullable(country).map(String::toLowerCase).filter(s -> !s.isBlank());
        Optional<String> ocat = Optional.ofNullable(category).map(String::toLowerCase).filter(s -> !s.isBlank());
        Optional<String> olang = Optional.ofNullable(language).map(String::toLowerCase).filter(s -> !s.isBlank());

        return sourcesService.listSources(oc, ocat, olang)
                .thenApply(list -> ok(sources.render(list,
                        country == null ? "" : country,
                        category == null ? "" : category,
                        language == null ? "" : language)));
    }


    /**
     * Extract or create session ID from Play session.
     * Generates new UUID if not present in session.
     *
     * @param request HTTP request
     * @return Session ID string
     * @author Group
     */
    private String getSessionId(Http.Request request) {
        return request.session().get("sessionId")
                .orElse(java.util.UUID.randomUUID().toString());
    }

    /**
     * Extract or create session ID from Play session.
     * Generates new UUID if not present.
     * This is an alias for getSessionId() to maintain clarity in different contexts.
     *
     * @param request HTTP request
     * @return Session ID string
     * @author Group
     */
    private String getOrCreateSessionId(Http.Request request) {
        return request.session().get("sessionId")
                .orElse(java.util.UUID.randomUUID().toString());
    }

    /**
     * Validate sortBy parameter.
     * Only allows: publishedAt, relevancy, popularity
     *
     * @param sortBy Sort option to validate
     * @return true if valid, false otherwise
     * @author Group
     */
    private boolean isValidSortBy(String sortBy) {
        return "publishedAt".equals(sortBy)
                || "relevancy".equals(sortBy)
                || "popularity".equals(sortBy);
    }
}
