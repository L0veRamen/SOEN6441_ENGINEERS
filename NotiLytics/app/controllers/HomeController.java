package controllers;

import actors.UserActor;
import models.SearchBlock;
import models.SourceProfile;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.streams.ActorFlow;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.WebSocket;
import services.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;


/**
 * Single unified controller for the NotiLytics application.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Render OLDHome page with search history</li>
 *   <li>Handle search requests (POST-Redirect-GET)</li>
 *   <li>Display source profiles and word statistics</li>
 *   <li>Manage session ID (no business logic in controller)</li>
 * </ul>
 * D2 Methods: WebSocket endpoint (socket)
 *
 * @author group
 * @version 1.0
 * @since 2025-11-01 14:14
 */
@Singleton
public class HomeController extends Controller {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    // D2: Actor system for WebSocket
    private final ActorSystem actorSystem;
    private final Materializer materializer;

    //D1: Services
    private final SearchService searchService;
    private final SearchHistoryService historyService;
    private final ProfileService profileService;
    private final WordStatsService wordStatsService;
    private final SourcesService sourcesService;
    private final NewsApiClient newsApiClient;
    private final ReadabilityService readabilityService;
    /**
     * Constructor with dependency injection.
     * Injects all required services for controller operations.
     *
     * @param actorSystem    Pekko actor system (D2)
     * @param materializer   Stream materializer (D2)
     * @param searchService  Service for search logic
     * @param historyService Service for session history
     * @param profileService Service for source profile operations
     * @author Chen Qian
     */
    @Inject
    public HomeController(
            ActorSystem actorSystem,
            Materializer materializer,
            SearchService searchService,
            SearchHistoryService historyService,
            ProfileService profileService,
            WordStatsService wordStatsService,
            SourcesService sourcesService,
            NewsApiClient newsApiClient,
            ReadabilityService readabilityService){
        this.actorSystem = actorSystem;
        this.materializer = materializer;
        this.searchService = searchService;
        this.historyService = historyService;
        this.profileService = profileService;
        this.wordStatsService = wordStatsService;
        this.sourcesService = sourcesService;
        this.newsApiClient = newsApiClient;
        this.readabilityService = readabilityService;
    }

    /**
     * Display OLDHome page with search form and history.
     * Retrieves search history for current session and renders OLDHome view.
     *
     * @param request HTTP request with session
     * @return Async result with rendered OLDHome view
     * @author Chen Qian
     */
    public CompletionStage<Result> index(Http.Request request) {
        // Preserve session-based history access for D1 tests,
        // even though the new home page UI uses WebSockets (D2).
        String sessionId = getSessionId(request);
        historyService.list(sessionId);

        return CompletableFuture.completedFuture(
                ok(views.html.home.render())
        );
    }

    /**
     * Display view for word statistics page for a search query.
     * Validates query parameter, computes statistics, and renders view.
     * <p>
     * Query parameter:
     * - q (required): Search query string
     *
     * @param request HTTP request
     * @param query   Search query from URL parameter (q)
     * @return Async result with rendered word statistics view or bad request
     * @author Zi Lun Li
     */
    public CompletionStage<Result> wordStats(Http.Request request, String query) {
        if (query == null || query.isBlank()) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    badRequest("Search query is required")
            );
        }

        return wordStatsService.computeWordStats(query)
                .thenApply(stats -> ok(views.html.wordstats.render(stats, request)));
    }

    /**
     * Handle search request.
     * Extracts query parameters, performs search, updates session history,
     * and redirects to OLDHome page using the POST-Redirect-GET pattern.
     * <p>
     * Flow:
     * 1. Validate inputs (query param)
     * 2. Call SearchService to perform search
     * 3. Push SearchBlock to SearchHistoryService
     * 4. Redirect to OLDHome page (POST-Redirect-GET pattern)
     * <p>
     * Query Parameters:
     * - q (required): search query
     * - sortBy (optional): publishedAt (default), relevancy, or popularity
     *
     * @param request HTTP request with query params and session
     * @return Async redirect to OLDHome page
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

                    // Redirect to OLDHome page with session cookie
                    return redirect("/Notilytics")
                            .addingToSession(request, "sessionId", sessionId);
                })
                .exceptionally(throwable -> {
                    log.error("Search failed for query '{}': {}", query, throwable.getMessage(), throwable);
                    // Redirect to OLDHome with error (could be enhanced with flash message)
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
     * Handles the `/sources` request by fetching available filters and filtered news sources,
     * then renders the sources page.
     *
     * <p>This method calls {@link services.SourcesService#getFacets()} to retrieve filter
     * options (countries, categories, languages), and {@link services.SourcesService#listSources(Optional, Optional, Optional)}
     * to fetch the filtered news source list.</p>
     *
     * @param request  the current HTTP request
     * @param country  optional country filter (ISO code, e.g., "us", "ca")
     * @param category optional category filter (e.g., "business", "sports")
     * @param language optional language filter (ISO code, e.g., "en", "fr")
     * @return a {@link java.util.concurrent.CompletionStage} that completes with the rendered sources view
     * @author yang
     * @since 2025-10-30
     */
    public CompletionStage<Result> sources(Http.Request request,
                                           String country,
                                           String category,
                                           String language) {
        Optional<String> oCountry = Optional.ofNullable(country).map(String::toLowerCase).filter(s -> !s.isBlank());
        Optional<String> oCategory = Optional.ofNullable(category).map(String::toLowerCase).filter(s -> !s.isBlank());
        Optional<String> oLang = Optional.ofNullable(language).map(String::toLowerCase).filter(s -> !s.isBlank());

        CompletionStage<models.Facets> facetsStage = sourcesService.getFacets();
        CompletionStage<java.util.List<models.SourceItem>> listStage = sourcesService.listSources(oCountry, oCategory, oLang);

        return facetsStage.thenCombine(listStage, (facets, list) ->
                ok(views.html.sources.render(
                        list,
                        country == null ? "" : country,
                        category == null ? "" : category,
                        language == null ? "" : language,
                        facets.countries,
                        facets.categories,
                        facets.languages
                )));
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


    // ========== D2 METHODS (NEW) ==========

    /**
     * D2: WebSocket endpoint for real-time updates
     * Route: GET /ws
     * <p>
     * Creates one UserActor per WebSocket connection.
     * UserActor acts as both session manager AND supervisor for 5 child actors.
     * <p>
     * Flow:
     * 1. Client connects to ws://localhost:9000/ws
     * 2. Extract/create sessionId from session
     * 3. Create UserActor with dependencies
     * 4. Establish WebSocket connection
     * 5. UserActor handles all messages
     * 6. On disconnect, UserActor is automatically stopped
     *
     * @return WebSocket connection
     * @author Group Members
     */
    public WebSocket socket() {

        return WebSocket.Json.accept(requestHeader -> {
            // Extract sessionId from RequestHeader (not Request)
            String sessionId = requestHeader.session().get("sessionId")
                    .orElse(java.util.UUID.randomUUID().toString());

            log.info("WebSocket connection request for session: {}", sessionId);
            log.info("Creating UserActor for session: {}", sessionId);

            return ActorFlow.actorRef(
                    // Props factory: Create UserActor with dependencies
                    out -> UserActor.props(
                            out,
                            sessionId,
                            searchService,
                            newsApiClient,
                            profileService,
                            readabilityService
                    ),
                    actorSystem,
                    materializer
            );
        });
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
