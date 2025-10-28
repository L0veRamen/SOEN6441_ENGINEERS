package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import models.SourceItem;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Implementation of SourcesService.
 * Fetches news sources from the News API, applies filters, and caches results.
 * Uses Java Streams for processing and Caffeine for local caching.
 *
 * @author Yang Zhang
 */
public class SourcesServiceImpl implements SourcesService {

    private final WSClient ws;
    private final String baseUrl;
    private final String apiKey;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final Cache<String, List<SourceItem>> cache;
    private final long sourcesTtlSec;

    /**
     * Initializes the service with WSClient and configuration settings.
     *
     * @param ws      Play WSClient for HTTP requests
     * @param config  application configuration
     */
    @Inject
    public SourcesServiceImpl(WSClient ws, Config config) {
        this.ws = ws;
        this.baseUrl = config.getString("newsapi.baseUrl");
        this.apiKey = config.getString("newsapi.key");
        this.connectTimeoutMs = (int) Duration.parse("PT" + config.getDuration("newsapi.timeouts.connect").toSeconds() + "S").toMillis();
        this.readTimeoutMs = (int) Duration.parse("PT" + config.getDuration("newsapi.timeouts.read").toSeconds() + "S").toMillis();
        this.sourcesTtlSec = config.getDuration("cache.ttl.sources").toSeconds();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(sourcesTtlSec))
                .maximumSize(Math.max(100, config.getInt("cache.maxSize")))
                .build();
    }

    /**
     * Retrieves a list of news sources with optional filters.
     * Uses caching to avoid repeated API calls.
     *
     * @param country  optional country filter
     * @param category optional category filter
     * @param language optional language filter
     * @return async list of SourceItem objects
     */
    @Override
    public CompletionStage<List<SourceItem>> listSources(Optional<String> country,
                                                         Optional<String> category,
                                                         Optional<String> language) {
        String cacheKey = "src:" + country.orElse("") + "|" + category.orElse("") + "|" + language.orElse("");
        List<SourceItem> hit = cache.getIfPresent(cacheKey);
        if (hit != null) return CompletableFuture.completedFuture(hit);

        WSRequest req = ws.url(baseUrl + "/sources")
                .addHeader("X-Api-Key", apiKey)
                .setRequestTimeout(Duration.ofMillis(connectTimeoutMs + readTimeoutMs));

        country.filter(s -> !s.isBlank()).ifPresent(v -> req.addQueryParameter("country", v));
        category.filter(s -> !s.isBlank()).ifPresent(v -> req.addQueryParameter("category", v));
        language.filter(s -> !s.isBlank()).ifPresent(v -> req.addQueryParameter("language", v));

        return req.get().thenApply(resp -> {
            if (resp.getStatus() != 200) {
                throw new RuntimeException("NewsAPI /sources failed: " + resp.getStatus() + " - " + resp.getBody());
            }

            JsonNode root = Json.parse(resp.getBody());
            JsonNode arr = root.get("sources");
            if (arr == null || !arr.isArray()) return Collections.emptyList();

            List<SourceItem> list = new ArrayList<>();
            arr.forEach(node -> list.add(new SourceItem(
                    text(node, "id"),
                    text(node, "name"),
                    text(node, "description"),
                    text(node, "url"),
                    text(node, "category"),
                    text(node, "language"),
                    text(node, "country")
            )));

            List<SourceItem> processed = list.stream()
                    .filter(s -> s.name != null && !s.name.isBlank())
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(
                                    s -> s.id != null ? s.id : s.url,
                                    s -> s,
                                    (a, b) -> a,
                                    LinkedHashMap::new
                            ),
                            m -> m.values().stream()
                                    .sorted(Comparator.comparing(s -> s.name.toLowerCase(Locale.ROOT)))
                                    .collect(Collectors.toList())
                    ));

            cache.put(cacheKey, processed);
            return processed;
        });
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}