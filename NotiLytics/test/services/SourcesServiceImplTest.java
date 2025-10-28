package services;

import com.typesafe.config.Config;
import models.Facets;
import models.SourceItem;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SourcesServiceImpl.
 * - Verifies query parameters are applied
 * - Parses and de-duplicates sources, then sorts by name (case-insensitive)
 * - Caches listSources() results by (country|category|language)
 * - Throws on non-200 responses
 * - getFacets() computes distinct, sorted facets and caches them
 */
@RunWith(MockitoJUnitRunner.class)
public class SourcesServiceImplTest {

    @Mock private WSClient ws;
    @Mock private WSRequest request;
    @Mock private WSResponse response;
    @Mock private Config config;

    private SourcesServiceImpl service;

    private static final String BASE = "https://newsapi.test";
    private static final String KEY  = "k";

    @Before
    public void setUp() {
        // --- Config stubs ---
        when(config.getString("newsapi.baseUrl")).thenReturn(BASE);
        when(config.getString("newsapi.key")).thenReturn(KEY);
        when(config.getDuration("newsapi.timeouts.connect")).thenReturn(Duration.ofSeconds(1));
        when(config.getDuration("newsapi.timeouts.read")).thenReturn(Duration.ofSeconds(1));
        when(config.getDuration("cache.ttl.sources")).thenReturn(Duration.ofSeconds(60));
        when(config.getInt("cache.maxSize")).thenReturn(100);

        // --- WS chain stubs (fluent API returns same request) ---
        when(ws.url(BASE + "/sources")).thenReturn(request);
        when(request.addHeader(anyString(), anyString())).thenReturn(request);
        when(request.setRequestTimeout(any())).thenReturn(request);
        when(request.addQueryParameter(anyString(), anyString())).thenReturn(request);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));

        service = new SourcesServiceImpl(ws, config);
    }

    private void stubHttp(int status, String body) {
        when(response.getStatus()).thenReturn(status);
        when(response.getBody()).thenReturn(body);
    }

    // ========== listSources: parses, dedup by id/url, sort by name ==========
    @Test
    public void listSources_parsesDedupsAndSortsByName() {
        // Two records share same id -> dedup keeps first; other one unique (no id, dedup by URL)
        String body = "{ \"sources\": [" +
                "{ \"id\":\"abc\",\"name\":\"Zeta\",\"description\":\"d1\",\"url\":\"https://z.com\",\"category\":\"tech\",\"language\":\"en\",\"country\":\"us\" }," +
                "{ \"id\":\"abc\",\"name\":\"Zeta DUP\",\"description\":\"dup\",\"url\":\"https://z-dup.com\",\"category\":\"tech\",\"language\":\"en\",\"country\":\"us\" }," +
                "{ \"id\":null,\"name\":\"alpha\",\"description\":\"d2\",\"url\":\"https://a.com\",\"category\":\"biz\",\"language\":\"fr\",\"country\":\"ca\" }" +
                "]}";
        stubHttp(200, body);

        List<SourceItem> out = service
                .listSources(Optional.empty(), Optional.empty(), Optional.empty())
                .toCompletableFuture().join();

        // Dedup -> 2 left
        assertEquals(2, out.size());
        // Sorted by name case-insensitive: alpha, Zeta
        assertEquals("alpha", out.get(0).name);
        assertEquals("Zeta",  out.get(1).name);

        // No filter params added for empty optionals
        verify(request, never()).addQueryParameter(eq("country"), anyString());
        verify(request, never()).addQueryParameter(eq("category"), anyString());
        verify(request, never()).addQueryParameter(eq("language"), anyString());
        // Header + timeout always added
        verify(request).addHeader("X-Api-Key", KEY);
        verify(request).setRequestTimeout(any());
    }

    // ========== listSources: applies filters to query params ==========
    @Test
    public void listSources_appliesFilterQueryParams() {
        stubHttp(200, "{ \"sources\": [] }");

        service.listSources(Optional.of("us"), Optional.of("business"), Optional.of("en"))
                .toCompletableFuture().join();

        verify(ws).url(BASE + "/sources");
        verify(request).addQueryParameter("country", "us");
        verify(request).addQueryParameter("category", "business");
        verify(request).addQueryParameter("language", "en");
    }

    // ========== listSources: caches results per (country|category|language) ==========
    @Test
    public void listSources_usesInMemoryCacheOnRepeatedCall() {
        stubHttp(200, "{ \"sources\": [" +
                "{ \"id\":\"x\",\"name\":\"NameX\",\"description\":\"d\",\"url\":\"https://x\",\"category\":\"c\",\"language\":\"en\",\"country\":\"us\" }]}");

        // First call: hits HTTP
        List<SourceItem> r1 = service
                .listSources(Optional.of("us"), Optional.of("c"), Optional.of("en"))
                .toCompletableFuture().join();
        assertEquals(1, r1.size());

        // Reset mocks to detect no further HTTP calls
        reset(ws, request, response);
        // But service cache should still return same data
        List<SourceItem> r2 = service
                .listSources(Optional.of("us"), Optional.of("c"), Optional.of("en"))
                .toCompletableFuture().join();
        assertEquals(1, r2.size());

        // Verify no new HTTP call (ws.url not invoked after reset)
        verify(ws, never()).url(anyString());
    }

    // ========== listSources: non-200 -> throws ==========
    @Test(expected = RuntimeException.class)
    public void listSources_throwsOnNon200() {
        stubHttp(500, "{ \"status\":\"error\" }");

        service.listSources(Optional.empty(), Optional.empty(), Optional.empty())
                .toCompletableFuture().join();
    }

    // ========== getFacets: computes distinct, sorted, and caches ==========
    @Test
    public void getFacets_distinctSortedAndCached() {
        String body = "{ \"sources\": [" +
                "{ \"id\":\"a\",\"name\":\"n1\",\"description\":\"d\",\"url\":\"https://a\",\"category\":\"technology\",\"language\":\"en\",\"country\":\"us\" }," +
                "{ \"id\":\"b\",\"name\":\"n2\",\"description\":\"d\",\"url\":\"https://b\",\"category\":\"business\",\"language\":\"en\",\"country\":\"ca\" }," +
                "{ \"id\":\"c\",\"name\":\"n3\",\"description\":\"d\",\"url\":\"https://c\",\"category\":\"business\",\"language\":\"fr\",\"country\":\"ca\" }" +
                "]}";
        stubHttp(200, body);

        // First call: will do HTTP via listSources(empty, empty, empty)
        Facets f1 = service.getFacets().toCompletableFuture().join();
        assertEquals(List.of("ca", "us"), f1.countries);         // sorted
        assertEquals(List.of("business", "technology"), f1.categories);
        assertEquals(List.of("en", "fr"), f1.languages);

        // Reset network mocks to ensure subsequent call is served by facets cache
        reset(ws, request, response);

        Facets f2 = service.getFacets().toCompletableFuture().join();
        assertEquals(List.of("ca", "us"), f2.countries);
        assertEquals(List.of("business", "technology"), f2.categories);
        assertEquals(List.of("en", "fr"), f2.languages);

        // No second HTTP call
        verify(ws, never()).url(anyString());
    }

    // ========== Defensive: empty/invalid "sources" array -> empty list ==========
    @Test
    public void listSources_handlesMissingSourcesArray() {
        stubHttp(200, "{ \"status\":\"ok\" }"); // no 'sources' field

        List<SourceItem> out = service
                .listSources(Optional.empty(), Optional.empty(), Optional.empty())
                .toCompletableFuture().join();

        assertTrue(out.isEmpty());
    }

    @Test
    public void listSources_textHelperBranches_nullAndMissingFields() {
        String body = "{ \"sources\": [" +
                "{ \"id\": null, " +
                "  \"name\": \"OnlyName\", " +
                "  \"description\": \"d\", " +
                "  \"url\": \"https://only\", " +
                "  \"category\": null, " +
                "  \"language\": null " +
                "}" +
                "]}";

        when(response.getStatus()).thenReturn(200);
        when(response.getBody()).thenReturn(body);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));

        List<SourceItem> out = service
                .listSources(Optional.of("zz"), Optional.empty(), Optional.empty())
                .toCompletableFuture().join();

        assertEquals(1, out.size());
        SourceItem it = out.get(0);
        assertEquals("OnlyName", it.name);
        assertEquals("https://only", it.url);
        assertNull(it.category);
        assertNull(it.language);
        assertNull(it.country);
    }
}