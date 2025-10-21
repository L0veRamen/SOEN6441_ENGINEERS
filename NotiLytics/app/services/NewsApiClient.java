//package services;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import com.typesafe.config.Config;
//import models.Article;
//import play.libs.ws.WSClient;
//import play.libs.ws.WSResponse;
//
//import javax.inject.Inject;
//import javax.inject.Singleton;
//import java.net.URLEncoder;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.concurrent.CompletionStage;
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.JsonNode;
//import play.libs.Json;
//
///**
// * Client for NewsAPI HTTP requests
// * Handles API communication, URL building, JSON parsing
// *
// * Responsibilities:
// * - Build API requests with proper parameters
// * - Parse JSON responses into domain models
// * - Handle API errors gracefully
// *
// * @author [Your Name]
// */
//@Singleton
//public class NewsApiClient {
//    private static final Logger log = LoggerFactory.getLogger(NewsApiClient.class);
//
//    private final WSClient wsClient;
//    private final String apiKey;
//    private final String baseUrl;
//
//    private String safeBody(WSResponse resp) {
//        try { return resp.getBody(); } catch (Exception e) { return "<unreadable>"; }
//    }
//    
//    /**
//     * Constructor with dependency injection
//     *
//     * @param wsClient Play WSClient for HTTP requests
//     * @param config   Application configuration
//     * @author [Your Name]
//     */
//    @Inject
//    public NewsApiClient(WSClient wsClient, Config config) {
//        this.wsClient = wsClient;
//        this.apiKey = config.hasPath("newsapi.key") ? config.getString("newsapi.key") : "";
//        this.baseUrl = config.hasPath("newsapi.baseUrl") ? config.getString("newsapi.baseUrl") : "";
//
//        if (apiKey == null || apiKey.isBlank()) {
//            throw new IllegalStateException("Missing configuration: newsapi.key");
//        }
//        if (baseUrl == null || baseUrl.isBlank()) {
//            throw new IllegalStateException("Missing configuration: newsapi.baseUrl");
//        }
//
//        log.info("NewsApiClient configured baseUrl={} (apiKey present: {})",
//                this.baseUrl, !this.apiKey.isBlank());
//    }
//
//    /**
//     * Search articles using NewsAPI /v2/everything endpoint
//     *
//     * @param query    Search query string
//     * @param sortBy   Sort option (publishedAt|relevancy|popularity)
//     * @param pageSize Number of results (default 10)
//     * @return CompletionStage with a list of articles and total count
//     * @author [Your Name]
//     */
//    public CompletionStage<SearchResponse> searchEverything(
//            String query,
//            String sortBy,
//            int pageSize
//    ) {
//        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
//        String url = String.format(
//                "%s/everything?q=%s&sortBy=%s&pageSize=%d&apiKey=%s",
//                baseUrl, encodedQuery, sortBy, pageSize, apiKey
//        );
//        log.info("NewsApiClient apiKey present={}, last4={}",
//                !apiKey.isBlank(),
//                apiKey.length() >= 4 ? apiKey.substring(apiKey.length() - 4) : apiKey);
//
//
//
//        return wsClient.url(url)
//                .get()
//                .thenApply(this::parseSearchResponse)
//                .exceptionally(t -> {
//                    // Log error and return empty response
//                    log.error("NewsAPI call failed: {}", t.toString(), t);
//                    return new SearchResponse(List.of(), 0);
//                });
//    }
//
//    /**
//     * Parse WSResponse JSON into SearchResponse
//     *
//     * @param resp HTTP response from NewsAPI
//     * @return SearchResponse with articles
//     * @author [Your Name]
//     */
//    private SearchResponse parseSearchResponse(WSResponse resp) {
//        // TODO: Implement JSON parsing
//        // Extract "articles" array and "totalResults"
//        // Map to Article records
//        try {
//            if (resp.getStatus() != 200) {
//                log.warn("NewsAPI HTTP {} - body: {}", resp.getStatus(), safeBody(resp));
//                return new SearchResponse(List.of(), 0);
//            }
//
//            JsonNode root = resp.asJson();
//            if (!"ok".equals(root.path("status").asText())) {
//                log.warn("NewsAPI error: code={}, message={}",
//                        root.path("code").asText(""), root.path("message").asText(""));
//                return new SearchResponse(List.of(), 0);
//            }
//
//            int total = root.path("totalResults").asInt(0);
//            JsonNode articlesNode = root.path("articles");
//
//            // Auto-map JSON array -> List<Article>
//            List<Article> articles = Json.mapper().convertValue(
//                    articlesNode, new TypeReference<List<Article>>() {}
//            );
//
//            // Defensive: never return null list
//            return new SearchResponse(articles == null ? List.of() : List.copyOf(articles), total);
//
//        } catch (Exception e) {
//            log.error("Failed to parse NewsAPI response", e);
//            return new SearchResponse(List.of(), 0);
//        }
//    }
//    
//    /**
//     * Response container for search results
//     *
//     * @param articles     List of articles
//     * @param totalResults Total available results
//     * @author [Your Name]
//     */
//    public record SearchResponse(List<Article> articles, int totalResults) {}
//}
package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.Config;
import models.Article;
import models.SourceProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import play.libs.Json;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Client for NewsAPI HTTP requests
 * Handles API communication, URL building, JSON parsing with caching
 *
 * Responsibilities:
 * - Build API requests with proper parameters
 * - Parse JSON responses into domain models
 * - Handle API errors gracefully
 * - Cache search results to prevent duplicate API calls
 *
 * @author Group
 */
@Singleton
public class NewsApiClient {

    private static final Logger log = LoggerFactory.getLogger(NewsApiClient.class);

    private final WSClient wsClient;
    private final String apiKey;
    private final String baseUrl;
    private final Cache<String, SearchResponse> searchCache;
    private final List<SourceProfile> sourceCache;

    /**
     * Constructor with dependency injection
     *
     * @param wsClient Play WSClient for HTTP requests
     * @param config   Application configuration
     * @author Chen Qian
     */
    @Inject
    public NewsApiClient(WSClient wsClient, Config config) {
        this.wsClient = wsClient;
        this.apiKey = config.hasPath("newsapi.key") ?
                config.getString("newsapi.key") : "";
        this.baseUrl = config.hasPath("newsapi.baseUrl") ?
                config.getString("newsapi.baseUrl") : "";

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing configuration: newsapi.key");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Missing configuration: newsapi.baseUrl");
        }

        // Configure cache with TTL from config or default to 15 minutes
        Duration cacheTTL = config.hasPath("cache.ttl.search")
                ? config.getDuration("cache.ttl.search")
                : Duration.ofMinutes(15);
        int maxSize = config.hasPath("cache.maxSize")
                ? config.getInt("cache.maxSize")
                : 1000;

        this.searchCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTTL)
                .maximumSize(maxSize)
                .build();
        sourceCache = new ArrayList<>();
        log.info("NewsApiClient configured baseUrl={} cacheTTL={} maxSize={}",
                this.baseUrl, cacheTTL, maxSize);
    }

    /**
     * Search articles using NewsAPI /v2/everything endpoint
     * Uses caching to prevent duplicate API calls
     *
     * @param query    Search query string
     * @param sortBy   Sort option (publishedAt|relevancy|popularity)
     * @param pageSize Number of results (default 10)
     * @return CompletionStage with a list of articles and total count
     * @author Chen Qian
     */
    public CompletionStage<SearchResponse> searchEverything(
            String query,
            String sortBy,
            int pageSize
    ) {
        // Create cache key
        String cacheKey = String.format("%s:%s:%d", query, sortBy, pageSize);

        // Try cache first
        SearchResponse cached = searchCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Cache HIT for key: {}", cacheKey);
            return CompletableFuture.completedFuture(cached);
        }

        log.debug("Cache MISS for key: {}", cacheKey);

        // Cache miss - fetch from API
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = String.format(
                "%s/everything?q=%s&sortBy=%s&pageSize=%d&apiKey=%s",
                baseUrl, encodedQuery, sortBy, pageSize, apiKey
        );

        return wsClient.url(url)
                .get()
                .thenApply(response -> {
                    SearchResponse result = parseSearchResponse(response);
                    // Cache successful responses
                    if (!result.articles().isEmpty()) {
                        searchCache.put(cacheKey, result);
                    }
                    return result;
                })
                .exceptionally(throwable -> {
                    log.error("NewsAPI call failed for query '{}': {}", query, throwable.getMessage(), throwable);
                    return new SearchResponse(List.of(), 0);
                });
    }

    /**
     * Parse WSResponse JSON into SearchResponse using Java Streams
     * Adds hyperlinks to articles and source websites
     *
     * @param response HTTP response from NewsAPI
     * @return SearchResponse with articles
     * @author Chen Qian
     */
    private SearchResponse parseSearchResponse(WSResponse response) {
        try {
            if (response.getStatus() != 200) {
                log.warn("NewsAPI HTTP {} - body: {}",
                        response.getStatus(),
                        response.getBody().substring(0, Math.min(200, response.getBody().length())));
                return new SearchResponse(List.of(), 0);
            }

            JsonNode root = response.asJson();
            if (root == null) {
                log.error("Failed to parse JSON response");
                return new SearchResponse(List.of(), 0);
            }

            // Check API status
            String status = root.has("status") ? root.get("status").asText() : "";
            if (!"ok".equals(status)) {
                String message = root.has("message") ? root.get("message").asText() : "Unknown error";
                log.warn("NewsAPI returned error status: {}", message);
                return new SearchResponse(List.of(), 0);
            }

            // Extract total results
            int totalResults = root.has("totalResults") ? root.get("totalResults").asInt() : 0;

            // Extract and process articles using Java Streams API
            JsonNode articlesNode = root.get("articles");
            if (articlesNode == null || !articlesNode.isArray()) {
                log.warn("No articles array in response");
                return new SearchResponse(List.of(), totalResults);
            }

            // Use Streams API to process articles
            List<Article> articles = StreamSupport.stream(articlesNode.spliterator(), false)
                    .filter(articleNode -> articleNode != null)
                    .map(this::parseArticle)
                    .filter(article -> article != null)
                    .filter(article -> article.title() != null && !article.title().isBlank())
                    .collect(Collectors.toList());

            log.info("Parsed {} articles from NewsAPI (total available: {})",
                    articles.size(), totalResults);

            return new SearchResponse(articles, totalResults);

        } catch (Exception e) {
            log.error("Error parsing NewsAPI response: {}", e.getMessage(), e);
            return new SearchResponse(List.of(), 0);
        }
    }

    /**
     * Parse a single article JSON node into an Article record
     * Extracts all necessary fields with proper null handling
     *
     * @param articleNode JSON node containing article data
     * @return Article record or null if parsing fails
     * @author Chen Qian
     */
    private Article parseArticle(JsonNode articleNode) {
        try {
            // Extract basic fields
            String title = getTextOrNull(articleNode, "title");
            String url = getTextOrNull(articleNode, "url");
            String description = getTextOrNull(articleNode, "description");
            String publishedAt = getTextOrNull(articleNode, "publishedAt");

            // Extract source information
            String sourceId = null;
            String sourceName = null;

            JsonNode sourceNode = articleNode.get("source");
            if (sourceNode != null) {
                sourceId = getTextOrNull(sourceNode, "id");
                sourceName = getTextOrNull(sourceNode, "name");
            }

            // Create Article record
            return new Article(
                    title,
                    url,
                    description,
                    sourceId,
                    sourceName,
                    publishedAt
            );
        } catch (Exception e) {
            log.error("Error parsing article: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Safely extract text value from JSON node
     *
     * @param node      JSON node
     * @param fieldName Field name
     * @return Text value or null
     * @author Chen Qian
     */
    private String getTextOrNull(JsonNode node, String fieldName) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return null;
    }

    /**
     * Response container for search results
     *
     * @param articles     List of articles
     * @param totalResults Total available results
     * @author Chen Qian
     */
    public record SearchResponse(List<Article> articles, int totalResults) {}


    // Edits for source profile search
    /**
     * Search source profiles using NewsAPI /v2/top-headlines endpoint
     * Uses caching to prevent duplicate API calls
     *
     * @param query    Search query string
     * @return CompletionStage with requested source profile
     * @author Yuhao Ma
     */
    public CompletionStage<SourceProfile> searchSourceProfile(String query) {
        // Try cache first
        if (!sourceCache.isEmpty()) {
            log.debug("Cache HIT for source profiles");
            var cachedResult = filterSearchProfile(sourceCache, query);
            if (cachedResult.isPresent()) {
                return CompletableFuture.completedFuture(cachedResult.get());
            }
        }

        log.debug("Cache source profiles not loaded for " + query);

        // Cache miss - fetch from API
        String url = String.format(
                "%s/top-headlines/sources?apiKey=%s",
                baseUrl, apiKey
        );

        return wsClient.url(url)
                .get()
                .thenApply(response -> {
                    JsonNode root = response.asJson();
                    String status = root.has("status") ? root.get("status").asText() : "";
                    if (!"ok".equals(status)) {
                        String message = root.has("message") ? root.get("message").asText() : "Unknown error";
                        log.warn("NewsAPI returned error status: {}", message);
                        return null;
                    }
                    root = root.get("sources");
                    List<SourceProfile> sources = StreamSupport.stream(root.spliterator(), false)
                            .map(node -> Json.fromJson(node, SourceProfile.class))
                            .toList();
                    // Cache successful responses
                    sourceCache.clear();
                    sourceCache.addAll(sources);
                    var result = filterSearchProfile(sources, query);
                    if (result.isEmpty()) {
                        log.warn("{} is not found in source profile response", query);
                        return null;
                    }
                    return result.get();
                })
                .exceptionally(throwable -> {
                    log.error("NewsAPI call failed for query '{}': {}", query, throwable.getMessage(), throwable);
                    return null;
                });
    }

    private Optional<SourceProfile> filterSearchProfile(List<SourceProfile> sources, String query) {
        return sources.stream()
                .filter(profile -> profile.id.equalsIgnoreCase(query) || profile.name.equalsIgnoreCase(query))
                .findAny();
    }

    /**
     * Search articles by source name
     * Uses caching to prevent duplicate API calls
     *
     * @param query    Source name
     * @return CompletionStage with a list of articles and total count
     * @author Yuhao Ma
     */
    public CompletionStage<SearchResponse> searchEverythingBySource(String query) {
        String cacheKey = String.format("Source:%s", query);
        SearchResponse cached = searchCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Cache HIT for key: {}", cacheKey);
            return CompletableFuture.completedFuture(cached);
        }

        log.debug("Cache MISS for key: {}", cacheKey);

        // Cache miss - fetch from API
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = String.format(
                "%s/top-headlines?sources=%s&pageSize=10&apiKey=%s",
                baseUrl, encodedQuery, apiKey
        );

        return wsClient.url(url)
                .get()
                .thenCompose(response -> {
                    SearchResponse result = parseSearchResponse(response);
                    // Cache successful responses
                    if (!result.articles().isEmpty()) {
                        searchCache.put(cacheKey, result);
                        return CompletableFuture.completedFuture(result);
                    } else {
                        return searchEverythingByFilter(encodedQuery).thenApply( res -> {
                            searchCache.put(cacheKey, res);
                            return res;
                        });
                    }
                })
                .exceptionally(throwable -> {
                    log.error("NewsAPI call failed for query '{}': {}", query, throwable.getMessage(), throwable);
                    return new SearchResponse(List.of(), 0);
                });
    }

    /**
     * Search articles by everything endpoint and then filter with the source name
     * Only called if searchEverythingBySource does not find result
     *
     * @param query    Source name
     * @return CompletionStage with a list of articles and total count
     * @author Yuhao Ma
     */
    public CompletionStage<SearchResponse> searchEverythingByFilter(String query) {
        String url = String.format(
                "%s/everything?q=%s&apiKey=%s",
                baseUrl, query, apiKey
        );
        return wsClient.url(url)
                .get()
                .thenApply(response -> {
                    SearchResponse result = parseSearchResponse(response);
                    List<Article> arts = result.articles.stream()
                            .filter(article -> article.sourceName().equalsIgnoreCase(query))
                            .sorted(Comparator.comparing(a -> Instant.parse(a.publishedAt()), Comparator.reverseOrder()))
                            .limit(10)
                            .toList();
                    result = new SearchResponse(arts, arts.size());
                    return result;
                })
                .exceptionally(throwable -> {
                    log.error("NewsAPI call failed for query '{}': {}", query, throwable.getMessage(), throwable);
                    return new SearchResponse(List.of(), 0);
                });
    }

}