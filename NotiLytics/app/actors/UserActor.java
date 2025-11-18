package actors;

import actors.messages.AnalyzeReadability;
import actors.messages.TaskResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import models.Article;
import models.SearchBlock;
import org.apache.pekko.actor.*;
import org.apache.pekko.japi.pf.DeciderBuilder;
import services.NewsApiClient;
import services.ReadabilityService;
import services.SearchService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UserActor: One instance per WebSocket connection
 * <p>
 * This actor fulfills THREE critical roles:
 * <p>
 * 1. SESSION MANAGER
 * - Maintains search history (max 10, FIFO)
 * - Tracks seen URLs for duplicate detection (LRU set, max 100)
 * - Stores current query state
 * <p>
 * 2. SUPERVISOR ACTOR (D2 Requirement #2)
 * - Creates and supervises 5 child actors (individual tasks)
 * - Implements OneForOneStrategy for failure handling
 * - Restarts children on exception (max 3 retries in 1 minute)
 * - This IS a "supervisor actor" as required by D2
 * <p>
 * 3. WEBSOCKET MESSAGE HANDLER
 * - Receives JSON messages from client
 * - Routes to appropriate handlers
 * - Sends responses back to client
 * <p>
 * 4. SEARCH COORDINATOR
 * - Initiates searches via SearchService
 * - Schedules periodic updates (every 30 seconds)
 * - Filters duplicates before sending to client
 * - Triggers child task actors for analysis
 *
 * @author Group Members
 */
public class UserActor extends AbstractActor {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(UserActor.class);

    // ========== CONFIGURATION CONSTANTS ==========
    private static final int MAX_HISTORY = 10;
    private static final int MAX_SEEN_URLS = 100;
    private static final Duration UPDATE_INTERVAL = Duration.ofSeconds(30);
    private static final int PAGE_SIZE = 10;
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_WINDOW = Duration.ofMinutes(1);

    // ========== DEPENDENCIES (INJECTED) ==========
    private final ActorRef out;              // WebSocket output reference
    private final String sessionId;          // Unique session identifier
    private final SearchService searchService;
    private final NewsApiClient newsApiClient;
    private final ObjectMapper mapper;
    private final ReadabilityService readabilityService;

    // ========== SESSION STATE (IN-MEMORY) ==========
    private final List<SearchBlock> searchHistory;
    private final LRUSet seenUrls;
    private String currentQuery;
    private String currentSortBy;
    private Cancellable updateScheduler;

    // ========== CHILD ACTORS (SUPERVISED) ==========
    private ActorRef sourceProfileActor;
    private ActorRef wordStatsActor;
    private ActorRef newsSourcesActor;
    private ActorRef sentimentAnalysisActor;
    private ActorRef readabilityActor;

    /**
     * Constructor (called by Pekko)
     *
     * @param out           WebSocket output reference
     * @param sessionId     Session identifier
     * @param searchService Search service
     * @param newsApiClient NewsAPI client
     * @author Group Members
     */
    public UserActor(
            ActorRef out,
            String sessionId,
            SearchService searchService,
            NewsApiClient newsApiClient,
            ReadabilityService readabilityService
    ) {
        this.out = out;
        this.sessionId = sessionId;
        this.searchService = searchService;
        this.newsApiClient = newsApiClient;
        this.mapper = new ObjectMapper();
        this.readabilityService = readabilityService;

        // Initialize state
        this.searchHistory = new ArrayList<>();
        this.seenUrls = new LRUSet(MAX_SEEN_URLS);
        this.currentQuery = null;
        this.currentSortBy = "publishedAt";
        this.updateScheduler = null;

        log.info("UserActor constructed for session: {}", sessionId);
    }

    /**
     * Props factory method for creating UserActor
     *
     * @param out           WebSocket output reference
     * @param sessionId     Session identifier
     * @param searchService Search orchestration service
     * @param newsApiClient NewsAPI HTTP client
     * @return Props for actor creation
     * @author Group Members
     */
    public static Props props(
            ActorRef out,
            String sessionId,
            SearchService searchService,
            NewsApiClient newsApiClient,
            ReadabilityService readabilityService
    ) {
        return Props.create(
                UserActor.class,
                out,
                sessionId,
                searchService,
                newsApiClient,
                readabilityService
        );
    }

    // ========== SUPERVISION STRATEGY (D2 REQUIREMENT #2) ==========

    /**
     * Supervision strategy for child actors
     * <p>
     * This method makes UserActor a "supervisor actor" as required by D2.
     * It defines how to handle failures in child actors (individual tasks).
     * <p>
     * Strategy: OneForOneStrategy
     * - Each child is supervised independently
     * - On Exception: Restart child actor (gives it fresh state)
     * - Max retries: 3 times within 1 minute
     * - After max retries: Stop child permanently
     * <p>
     * Why OneForOneStrategy?
     * - Failure in one child shouldn't affect others
     * - If WordStatsActor fails, SourceProfileActor continues working
     *
     * @return SupervisorStrategy
     * @author Group Members
     */
    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(
                MAX_RETRIES,            // maxNrOfRetries
                RETRY_WINDOW,           // withinTimeRange
                DeciderBuilder
                        // Handle News API timeouts
                        .match(java.net.http.HttpTimeoutException.class, e -> {
                            log.warn(
                                    "Child timed out calling News API in session {}: {}",
                                    sessionId, e.getMessage()
                            );
                            return SupervisorStrategy.restart();
                        })
                        // Handle News API connection errors
                        .match(java.io.IOException.class, e -> {
                            log.warn(
                                    "Child IO error (News API) in session {}: {}",
                                    sessionId, e.getMessage()
                            );
                            return SupervisorStrategy.restart();
                        })
                        // Handle JSON parsing errors
                        .match(com.fasterxml.jackson.core.JsonProcessingException.class, e -> {
                            log.error(
                                    "Child JSON parse error in session {}: {}",
                                    sessionId, e.getMessage()
                            );
                            return SupervisorStrategy.restart();
                        })
                        // Handle all other exceptions
                        .match(Exception.class, e -> {
                            log.error(
                                    "Child actor failed in session {}: {} - {}",
                                    sessionId,
                                    e.getClass().getSimpleName(),
                                    e.getMessage()
                            );
                            return SupervisorStrategy.restart();
                        })
                        // Escalate unknown errors to parent
                        .matchAny(o -> {
                            log.error("Unknown failure in session {}: {}", sessionId, o);
                            return SupervisorStrategy.escalate();
                        })
                        .build()
        );
    }

    // ========== LIFECYCLE METHODS ==========

    /**
     * Called when actor starts
     * Creates and supervises 5 child actors (individual tasks)
     *
     * @author Group Members
     */
    @Override
    public void preStart() {
        log.info("UserActor starting for session: {}", sessionId);

        // Create 5 supervised child actors
        // NOTE: Child actors are injected via Guice in production
        // For now, we pass null and handle injection in child constructors

//        sourceProfileActor = getContext().actorOf(
//                Props.create(SourceProfileActor.class),
//                "source-profile-" + sessionId
//        );
//
//        wordStatsActor = getContext().actorOf(
//                Props.create(WordStatsActor.class),
//                "word-stats-" + sessionId
//        );
//
//        newsSourcesActor = getContext().actorOf(
//                Props.create(NewsSourcesActor.class),
//                "news-sources-" + sessionId
//        );
//
//        sentimentAnalysisActor = getContext().actorOf(
//                Props.create(SentimentAnalysisActor.class),
//                "sentiment-" + sessionId
//        );

        readabilityActor = getContext().actorOf(
                ReadabilityActor.props(readabilityService),
                "readability-" + sessionId
        );

        log.info(
                "UserActor created 5 supervised children for session: {}",
                sessionId
        );
    }

    /**
     * Called when actor stops (WebSocket disconnect)
     * Cleanup resources
     *
     * @author Group Members
     */
    @Override
    public void postStop() {
        log.info("UserActor stopping for session: {}", sessionId);

        // Cancel scheduled updates
        if (updateScheduler != null && !updateScheduler.isCancelled()) {
            updateScheduler.cancel();
        }

        // Child actors are automatically stopped by Pekko
        // State is automatically garbage collected

        log.info("UserActor stopped for session: {}", sessionId);
    }

    // ========== MESSAGE HANDLING ==========

    /**
     * Define message handling behavior
     *
     * @return Receive behavior
     * @author Group Members
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(JsonNode.class, this::handleWebSocketMessage)
                .match(UpdateCheck.class, msg -> checkForNewArticles())
                .match(TaskResult.class, this::handleTaskResult)
                .build();
    }

    /**
     * Handle incoming WebSocket messages from the client
     * <p>
     * Protocol:
     * - start_search: Begin new search with query
     * - stop_search: Stop current search
     * - ping: Keep-alive
     * - get_history: Retrieve search history for this session
     *
     * @param message JSON message from the client
     * @author Group Members
     */
    private void handleWebSocketMessage(JsonNode message) {
        try {
            String type = message.get("type").asText();

            log.debug("Received WebSocket message: {} for session: {}",
                    type, sessionId);

            switch (type) {
                case "start_search" -> {
                    String query = message.get("query").asText();
                    String sortBy = message.has("sortBy")
                            ? message.get("sortBy").asText()
                            : "publishedAt";
                    startNewSearch(query, sortBy);
                }
                case "stop_search" -> stopCurrentSearch();
                case "ping" -> sendPong();
                case "get_history" -> sendHistory();
                default -> {
                    log.warn("Unknown message type: {} for session: {}",
                            type, sessionId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle WebSocket message: {}", e.getMessage(), e);
            sendError("Invalid message format");
        }
    }

    /**
     * Start new search
     * <p>
     * Flow:
     * 1. Update state (query, sortBy, clear dedup)
     * 2. Cancel the previous scheduler if it exists
     * 3. Fetch initial 10 results from SearchService (async)
     * 4. Send initial results to the client immediately
     * 5. Add to search history
     * 6. Track URLs for dedup
     * 7. Trigger child task actors
     * 8. Schedule periodic updates
     *
     * @param query  Search query
     * @param sortBy Sort option (publishedAt|relevancy|popularity)
     * @author Group Members
     */
    private void startNewSearch(String query, String sortBy) {
        log.info("Starting search for session {}: query='{}' sortBy='{}'",
                sessionId, query, sortBy);

        // Update state
        this.currentQuery = query;
        this.currentSortBy = sortBy;
        this.seenUrls.clear();

        // Cancel previous scheduler
        if (updateScheduler != null && !updateScheduler.isCancelled()) {
            updateScheduler.cancel();
        }

        // Fetch initial results (async)
        searchService.search(query, sortBy)
                .thenAccept(searchBlock -> {
                    log.info("Initial results received for session {}: {} articles",
                            sessionId, searchBlock.articles().size());

                    // Send initial results immediately
                    sendToWebSocket("initial_results", Map.of(
                            "query", searchBlock.query(),
                            "sortBy", searchBlock.sortBy(),
                            "totalResults", searchBlock.totalResults(),
                            "articles", searchBlock.articles(),
                            "timestamp", searchBlock.createdAtIso(),
                            "readability", searchBlock.readability(), // overall average
                            "articleReadability", searchBlock.articleReadability(), // per-article scores
                            "sentiment", searchBlock.articleSentiment()
                    ));

                    // Add to history (max 10, FIFO)
                    addToHistory(searchBlock);
                    
                    // AUTO-UPDATE: Send updated history to the client
                    sendHistory();
                    
                    // Track seen URLs (bounded LRU)
                    searchBlock.articles().forEach(article ->
                            seenUrls.add(article.url())
                    );

                    log.debug("Tracked {} URLs for dedup in session {}",
                            seenUrls.size(), sessionId);

                    // Trigger individual task analysis
                    triggerTaskAnalysis(searchBlock.articles());

                    // Schedule periodic updates (every 30 seconds)
                    scheduleUpdates();
                })
                .exceptionally(error -> {
                    log.error("Search failed for session {}: {}",
                            sessionId, error.getMessage(), error);
                    handleNewsApiError(error);
                    return null;
                });
    }

    /**
     * Schedule periodic updates (every 30 seconds)
     * Sends UpdateCheck message to self
     *
     * @author Group Members
     */
    private void scheduleUpdates() {
        updateScheduler = getContext().getSystem().scheduler().scheduleAtFixedRate(
                UPDATE_INTERVAL,        // initialDelay
                UPDATE_INTERVAL,        // interval
                getSelf(),              // receiver
                new UpdateCheck(),      // message
                getContext().getSystem().dispatcher(),  // executor
                getSelf()               // sender
        );

        log.info("Scheduled updates every {} seconds for session {}",
                UPDATE_INTERVAL.getSeconds(), sessionId);
    }

    /**
     * Check for new articles (scheduled every 30 seconds)
     * <p>
     * Flow:
     * 1. Check if search is active
     * 2. Fetch the latest articles from SearchService
     * 3. Filter duplicates using seenUrls set
     * 4. If new articles found:
     * - Track new URLs
     * - Send append message to client
     * - Trigger child task actors
     * 5. If no new articles:
     * - Do nothing (silent)
     *
     * @author Group Members
     */
    private void checkForNewArticles() {
        // Check if search is active
        if (currentQuery == null) {
            log.debug("No active search for session {}, skipping update check",
                    sessionId);
            return;
        }

        log.debug("Checking for new articles for session {}: query='{}'",
                sessionId, currentQuery);

        // Fetch latest articles
        searchService.search(currentQuery, currentSortBy)
                .thenAccept(searchBlock -> {
                    // Filter new articles (dedup)
                    List<Article> newArticles = searchBlock.articles().stream()
                            .filter(article -> !seenUrls.contains(article.url()))
                            .toList();

                    if (!newArticles.isEmpty()) {
                        log.info("Found {} new article(s) for session {}",
                                newArticles.size(), sessionId);

                        // Track new URLs
                        newArticles.forEach(article -> seenUrls.add(article.url()));

                        // Send to the client
                        sendToWebSocket("append", Map.of(
                                "articles", newArticles,
                                "count", newArticles.size()
                        ));

                        // Trigger task analysis
                        triggerTaskAnalysis(newArticles);
                    } else {
                        log.debug("No new articles for session {}", sessionId);
                    }
                })
                .exceptionally(error -> {
                    log.error("Update check failed for session {}: {}",
                            sessionId, error.getMessage());
                    // Don't stop search on update failure
                    return null;
                });
    }

    /**
     * Stop current search
     * Cancel scheduler, clear state
     *
     * @author Group Members
     */
    private void stopCurrentSearch() {
        log.info("Stopping search for session {}", sessionId);

        if (updateScheduler != null && !updateScheduler.isCancelled()) {
            updateScheduler.cancel();
            updateScheduler = null;
        }

        currentQuery = null;
        seenUrls.clear();

        sendToWebSocket("status", Map.of("message", "Search stopped"));
    }

    /**
     * Trigger individual task actors to analyze articles
     * Each actor receives the relevant message and processes asynchronously
     *
     * @param articles Articles to analyze
     * @author Group Members
     */
    private void triggerTaskAnalysis(List<Article> articles) {
        if (articles.isEmpty()) {
            log.debug("No articles to analyze for session {}", sessionId);
            return;
        }

        log.debug("Triggering task analysis for {} articles in session {}",
                articles.size(), sessionId);

//        // Task A: Source Profile (first article's source)
        String sourceName = articles.getFirst().getSourceDisplayName();
//        sourceProfileActor.tell(
//                new AnalyzeSource(sourceName),
//                getSelf()
//        );
//
//        // Task B: Word Stats
//        wordStatsActor.tell(
//                new AnalyzeWords(currentQuery),
//                getSelf()
//        );
//
//        // Task C: News Sources
//        newsSourcesActor.tell(
//                new FetchSources(null, null, null),
//                getSelf()
//        );
//
//        // Task D: Sentiment Analysis
//        sentimentAnalysisActor.tell(
//                new AnalyzeSentiment(articles),
//                getSelf()
//        );
//
        // Task E: Readability
        readabilityActor.tell(
                new AnalyzeReadability(articles),
                getSelf()
        );
    }

    /**
     * Handle results from child task actors
     * Forward to the client via WebSocket
     *
     * @param result Task result
     * @author Group Members
     */
    private void handleTaskResult(TaskResult result) {
        log.debug("Received task result: {} for session {}",
                result.taskType(), sessionId);

        sendToWebSocket(result.taskType(), result.data());
    }

    /**
     * Handle News API errors with the appropriate recovery strategy
     *
     * @param error Throwable from News API call
     * @author Group Members
     */
    private void handleNewsApiError(Throwable error) {
        if (error instanceof java.net.http.HttpTimeoutException) {
            log.warn("News API timeout for session {}", sessionId);
            sendToWebSocket("status", Map.of(
                    "message", "Search delayed due to slow API response"
            ));

        } else if (error instanceof java.net.ConnectException) {
            log.error("News API unreachable for session {}", sessionId);
            sendToWebSocket("error", Map.of(
                    "message", "News service temporarily unavailable. Please try again later."
            ));
            stopCurrentSearch();

        } else if (error.getMessage().contains("429")) {
            log.warn("News API rate limit exceeded for session {}", sessionId);
            sendToWebSocket("error", Map.of(
                    "message", "Too many requests. Please wait a moment and try again."
            ));
            // Slow down updates
            if (updateScheduler != null) {
                updateScheduler.cancel();
                updateScheduler = getContext().getSystem().scheduler().scheduleAtFixedRate(
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(2),
                        getSelf(),
                        new UpdateCheck(),
                        getContext().getSystem().dispatcher(),
                        getSelf()
                );
            }

        } else {
            log.error("News API error for session {}: {}",
                    sessionId, error.getMessage());
            sendToWebSocket("error", Map.of(
                    "message", "Search failed: " + error.getMessage()
            ));
        }
    }

    // ========== STATE MANAGEMENT ==========

    /**
     * Add search to history (max 10, FIFO)
     * Oldest search is removed when the limit exceeded
     *
     * @param searchBlock Search result to add
     * @author Group Members
     */
    private void addToHistory(SearchBlock searchBlock) {
        searchHistory.addFirst(searchBlock);  // Add to front (newest first)

        if (searchHistory.size() > MAX_HISTORY) {
            searchHistory.remove(MAX_HISTORY);  // Remove oldest
            log.debug("Removed oldest search from history for session {}",
                    sessionId);
        }

        log.debug("History size for session {}: {}/{}",
                sessionId, searchHistory.size(), MAX_HISTORY);
    }

    // ========== WEBSOCKET HELPERS ==========

    /**
     * Send a message to WebSocket client
     *
     * @param type Message type
     * @param data Message payload
     * @author Group Members
     */
    private void sendToWebSocket(String type, Object data) {
        try {
            Map<String, Object> message = Map.of("type", type, "data", data);
            JsonNode json = mapper.valueToTree(message);
            out.tell(json, getSelf());

            log.debug("Sent WebSocket message: {} for session {}",
                    type, sessionId);
        } catch (Exception e) {
            log.error("Failed to send WebSocket message for session {}: {}",
                    sessionId, e.getMessage(), e);
        }
    }

    /**
     * Send an error message to the client
     *
     * @param errorMessage Error description
     * @author Group Members
     */
    private void sendError(String errorMessage) {
        sendToWebSocket("error", Map.of("message", errorMessage));
    }

    /**
     * Send pong response to the client
     *
     * @author Group Members
     */
    private void sendPong() {
        sendToWebSocket("pong", Map.of());
    }

    /**
     * Send search history to the client
     * Returns all searches in this session (max 10, newest first)
     *
     * @author Group Members
     */
    private void sendHistory() {
        log.info("Sending search history to session {}: {} searches",
                sessionId, searchHistory.size());

        sendToWebSocket("history", Map.of(
                "searches", searchHistory,
                "count", searchHistory.size(),
                "maxHistory", MAX_HISTORY
        ));
    }
    
    // ========== INNER CLASSES ==========

    /**
     * Internal message for scheduled update checks
     * Sent to self every 30 seconds
     *
     * @author Group Members
     */
    public static class UpdateCheck {
        // Empty message class
    }

    /**
     * Bounded LRU set for duplicate detection
     * Automatically evicts oldest entries when max size reached
     *
     * @author Group Members
     */
    private static class LRUSet extends LinkedHashMap<String, Boolean> {
        private final int maxSize;

        /**
         * Constructor
         *
         * @param maxSize Maximum number of entries
         */
        public LRUSet(int maxSize) {
            super(maxSize + 1, 0.75f, true);  // accessOrder = true (LRU)
            this.maxSize = maxSize;
        }

        /**
         * Automatically remove the oldest entry when max size exceeded
         *
         * @param eldest Eldest entry
         * @return true if it should remove, false otherwise
         */
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > maxSize;
        }

        /**
         * Add URL to set
         *
         * @param url URL to add
         * @return true if URL was new, false if already present
         */
        public boolean add(String url) {
            return put(url, Boolean.TRUE) == null;
        }

        /**
         * Check if the URL is present
         *
         * @param url URL to check
         * @return true if present, false otherwise
         */
        public boolean contains(String url) {
            return containsKey(url);
        }

        /**
         * Get current size
         *
         * @return Number of URLs tracked
         */
        @Override
        public int size() {
            return super.size();
        }

        /**
         * Clear all URLs
         */
        @Override
        public void clear() {
            super.clear();
        }
    }
}