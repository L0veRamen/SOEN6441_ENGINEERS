package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import models.SourceItem;
import models.Facets;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/*
 * @Author Yang
 * @Description
 * Service implementation for fetching NewsAPI sources.
 * Uses Play WS for HTTP calls, Caffeine for caching, and Streams for processing.
 * Provides filtered source listing and precomputed facets for dropdowns.
 * @Date 11:38 2025-10-28
 */
public class SourcesServiceImpl implements SourcesService {

    private final WSClient ws;
    private final String baseUrl;
    private final String apiKey;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private final Cache<String, List<SourceItem>> cache;
    private final long sourcesTtlSec;

    private final Cache<String, Facets> facetsCache;

    /*
     * @Author Yang
     * @Description
     * Constructor. Reads config, sets timeouts, and initializes caches.
     * @Date 12:46 2025-10-28
     * @Param ws      Play WS client
     * @Param config  application configuration
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

        this.facetsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(sourcesTtlSec))
                .maximumSize(10)
                .build();
    }

    /*
     * @Author Yang
     * @Description
     * Returns a filtered list of sources. Results are cached by (country|category|language).
     * @Date 11:39 2025-10-28
     * @Param country   optional country code (lowercase)
     * @Param category  optional category
     * @Param language  optional language code (lowercase)
     * @return async list of SourceItem
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
            JsonNode arr = Json.parse(resp.getBody()).get("sources");
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

    /*
     * @Author Yang
     * @Description
     * Computes distinct countries, categories, and languages from all sources.
     * Cached separately to feed dropdown menus.
     * @Date 11:39 2025-10-28
     * @return async Facets containing three distinct option lists
     */
    @Override
    public CompletionStage<Facets> getFacets() {
        Facets hit = facetsCache.getIfPresent("facets");
        if (hit != null) return CompletableFuture.completedFuture(hit);

        return listSources(Optional.empty(), Optional.empty(), Optional.empty()).thenApply(all -> {
            List<String> countries = all.stream()
                    .map(s -> s.country == null ? "" : s.country.trim())
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .sorted()
                    .toList();

            List<String> categories = all.stream()
                    .map(s -> s.category == null ? "" : s.category.trim())
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .sorted()
                    .toList();

            List<String> languages = all.stream()
                    .map(s -> s.language == null ? "" : s.language.trim())
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .sorted()
                    .toList();

            Facets f = new Facets(countries, categories, languages);
            facetsCache.put("facets", f);
            return f;
        });
    }

    /*
     * @Author Yang
     * @Description
     * Safely reads a text field from a JsonNode, returning null if absent.
     * @Date 11:39 2025-10-28
     * @Param node   source JSON node
     * @Param field  field name
     * @return String value or null
     */
    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}