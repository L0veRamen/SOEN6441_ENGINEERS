package services;

import com.typesafe.config.Config;
import models.Facets;
import models.SourceItem;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 
 * @description: Unit tests for SourcesServiceImpl covering request parameterization, JSON parsing/dedup/sorting, in-memory caching, error handling, and facet caching.
 * @author yang
 * @date: 2025-10-30 13:18
 * @version 1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class SourcesServiceImplTest {

    @Mock
    private WSClient ws;
    @Mock
    private WSRequest request;
    @Mock
    private WSResponse response;
    @Mock
    private Config config;

    private SourcesServiceImpl service;

    private static final String BASE = "https://newsapi.test";
    private static final String KEY = "k";

    /**
     * @description: Initialize mocks, stub config and WS fluent calls, and construct the service under test.
     * @param:
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:54
     */
    @Before
    public void setUp() {
        when(config.getString("newsapi.baseUrl")).thenReturn(BASE);
        when(config.getString("newsapi.key")).thenReturn(KEY);
        when(config.getDuration("newsapi.timeouts.connect")).thenReturn(Duration.ofSeconds(1));
        when(config.getDuration("newsapi.timeouts.read")).thenReturn(Duration.ofSeconds(1));
        when(config.getDuration("cache.ttl.sources")).thenReturn(Duration.ofSeconds(60));
        when(config.getInt("cache.maxSize")).thenReturn(100);

        when(ws.url(BASE + "/sources")).thenReturn(request);
        when(request.addHeader(anyString(), anyString())).thenReturn(request);
        when(request.setRequestTimeout(any())).thenReturn(request);
        when(request.addQueryParameter(anyString(), anyString())).thenReturn(request);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));

        service = new SourcesServiceImpl(ws, config);
    }

    /**
     * @description: Helper to stub the mocked HTTP response with the given status and body.
     * @param: status;body
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:54
     */
    private void stubHttp(int status, String body) {
        when(response.getStatus()).thenReturn(status);
        when(response.getBody()).thenReturn(body);
    }

    /**
     * @description: Verifies JSON is parsed, sources are de-duplicated by id/url, sorted by name, and no query params are sent when filters are empty.
     * @param:
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:54
     */
    @Test
    public void listSources_parsesDedupsAndSortsByName() {
        String body = "{ \"sources\": [" + "{ \"id\":\"abc\",\"name\":\"Zeta\",\"description\":\"d1\",\"url\":\"https://z.com\",\"category\":\"tech\",\"language\":\"en\",\"country\":\"us\" }," + "{ \"id\":\"abc\",\"name\":\"Zeta DUP\",\"description\":\"dup\",\"url\":\"https://z-dup.com\",\"category\":\"tech\",\"language\":\"en\",\"country\":\"us\" }," + "{ \"id\":null,\"name\":\"alpha\",\"description\":\"d2\",\"url\":\"https://a.com\",\"category\":\"biz\",\"language\":\"fr\",\"country\":\"ca\" }" + "]}";
        stubHttp(200, body);

        List<SourceItem> out = service.listSources(Optional.empty(), Optional.empty(), Optional.empty()).toCompletableFuture().join();

        assertEquals(2, out.size());
        assertEquals("alpha", out.get(0).name);
        assertEquals("Zeta", out.get(1).name);

        verify(request, never()).addQueryParameter(eq("country"), anyString());
        verify(request, never()).addQueryParameter(eq("category"), anyString());
        verify(request, never()).addQueryParameter(eq("language"), anyString());
        verify(request).addHeader("X-Api-Key", KEY);
        verify(request).setRequestTimeout(any());
    }

    /**
     * @description: Ensures provided country/category/language filters are added as HTTP query parameters.
     * @param:
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:54
     */
    @Test
    public void listSources_appliesFilterQueryParams() {
        stubHttp(200, "{ \"sources\": [] }");

        service.listSources(Optional.of("us"), Optional.of("business"), Optional.of("en")).toCompletableFuture().join();

        verify(ws).url(BASE + "/sources");
        verify(request).addQueryParameter("country", "us");
        verify(request).addQueryParameter("category", "business");
        verify(request).addQueryParameter("language", "en");
    }

    /**
     * @description: Confirms repeated identical requests are served from cache without issuing another HTTP call.
     * @param:
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:54
     */
    @Test
    public void listSources_usesInMemoryCacheOnRepeatedCall() {
        stubHttp(200, "{ \"sources\": [" + "{ \"id\":\"x\",\"name\":\"NameX\",\"description\":\"d\",\"url\":\"https://x\",\"category\":\"c\",\"language\":\"en\",\"country\":\"us\" }]}");

        List<SourceItem> r1 = service.listSources(Optional.of("us"), Optional.of("c"), Optional.of("en")).toCompletableFuture().join();
        assertEquals(1, r1.size());

        reset(ws, request, response);
        List<SourceItem> r2 = service.listSources(Optional.of("us"), Optional.of("c"), Optional.of("en")).toCompletableFuture().join();
        assertEquals(1, r2.size());

        verify(ws, never()).url(anyString());
    }

    /**
     * @description: Asserts a RuntimeException is thrown when the upstream returns a non-200 status.
     * @param:
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:54
     */
    @Test(expected = RuntimeException.class)
    public void listSources_throwsOnNon200() {
        stubHttp(500, "{ \"status\":\"error\" }");

        service.listSources(Optional.empty(), Optional.empty(), Optional.empty()).toCompletableFuture().join();
    }

    /**
     * @description: Checks that facets are computed as distinct and sorted lists, and subsequent calls hit the facets cache.
     * @param:
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:54
     */
    @Test
    public void getFacets_distinctSortedAndCached() {
        String body = "{ \"sources\": [" + "{ \"id\":\"a\",\"name\":\"n1\",\"description\":\"d\",\"url\":\"https://a\",\"category\":\"technology\",\"language\":\"en\",\"country\":\"us\" }," + "{ \"id\":\"b\",\"name\":\"n2\",\"description\":\"d\",\"url\":\"https://b\",\"category\":\"business\",\"language\":\"en\",\"country\":\"ca\" }," + "{ \"id\":\"c\",\"name\":\"n3\",\"description\":\"d\",\"url\":\"https://c\",\"category\":\"business\",\"language\":\"fr\",\"country\":\"ca\" }" + "]}";
        stubHttp(200, body);

        Facets f1 = service.getFacets().toCompletableFuture().join();
        assertEquals(List.of("ca", "us"), f1.countries);
        assertEquals(List.of("business", "technology"), f1.categories);
        assertEquals(List.of("en", "fr"), f1.languages);

        reset(ws, request, response);

        Facets f2 = service.getFacets().toCompletableFuture().join();
        assertEquals(f1.countries, f2.countries);
        assertEquals(f1.categories, f2.categories);
        assertEquals(f1.languages, f2.languages);

        verify(ws, never()).url(anyString());
    }

    /**
     * @description: Validates that a payload missing the 'sources' array yields an empty list rather than failing.
     * @param:
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:55
     */
    @Test
    public void listSources_handlesMissingSourcesArray() {
        stubHttp(200, "{ \"status\":\"ok\" }");

        List<SourceItem> out = service.listSources(Optional.empty(), Optional.empty(), Optional.empty()).toCompletableFuture().join();

        assertTrue(out.isEmpty());
    }

    /**
     * @description: Covers text() helper behavior by asserting null/missing JSON fields map to nulls while producing a valid item.
     * @param:
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:55
     */
    @Test
    public void listSources_textHelperBranches_nullAndMissingFields() {
        String body = "{ \"sources\": [" + "{ \"id\": null, " + "  \"name\": \"OnlyName\", " + "  \"description\": \"d\", " + "  \"url\": \"https://only\", " + "  \"category\": null, " + "  \"language\": null " + "}" + "]}";

        when(response.getStatus()).thenReturn(200);
        when(response.getBody()).thenReturn(body);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));

        List<SourceItem> out = service.listSources(Optional.of("zz"), Optional.empty(), Optional.empty()).toCompletableFuture().join();

        assertEquals(1, out.size());
        SourceItem it = out.get(0);
        assertEquals("OnlyName", it.name);
        assertEquals("https://only", it.url);
        assertNull(it.category);
        assertNull(it.language);
        assertNull(it.country);
    }
}