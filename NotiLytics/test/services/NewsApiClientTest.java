package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.Article;
import models.SourceProfile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NewsApiClient
 * <p>
 * Test Strategy:
 * - Mock all service dependencies
 * - Test equivalence classes for input validation
 * - Verify async behavior with CompletionStage
 * - Ensure proper session handling
 * - Never call live NewsAPI (use mocks only)
 *
 * @author Group
 */
@RunWith(MockitoJUnitRunner.class)
public class NewsApiClientTest {

    @Mock
    private WSClient wsClient;

    @Mock
    private WSRequest wsRequest;

    @Mock
    private WSResponse wsResponse;

    private ObjectMapper mapper;
    private Config config;
    private NewsApiClient client;
    /**
     * Set up test fixtures before each test
     * Initializes client instance
     *
     * @author Group
     */
    @Before
    public void setUp() {
        mapper = new ObjectMapper();
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("newsapi.key", "test-api-key");
        configMap.put("newsapi.baseUrl", "https://newsapi.org/v2");
        configMap.put("cache.ttl.search", "15 minutes");
        configMap.put("cache.maxSize", 100);
        config = ConfigFactory.parseMap(configMap);

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));

        client = new NewsApiClient(wsClient, config);
    }

    /**
     * Test constructor
     *
     * @author Group
     */
    @Test(expected = IllegalStateException.class)
    public void constructorRequiresApiKey() {
        Config badConfig = ConfigFactory.parseString("newsapi.baseUrl = \"https://newsapi.org/v2\"");
        new NewsApiClient(wsClient, badConfig);
    }

    /**
     * Test empty key
     *
     * @author Group
     */
    @Test(expected = IllegalStateException.class)
    public void TestBlankKey() {
        Map<String, Object> map = new HashMap<>();
        map.put("newsapi.key", " ");
        map.put("newsapi.baseUrl", " ");
        Config badConfig = ConfigFactory.parseMap(map);
        new NewsApiClient(wsClient, badConfig);
    }

    /**
     * Test empty url
     *
     * @author Group
     */
    @Test(expected = IllegalStateException.class)
    public void TestBlankUrl() {
        Map<String, Object> map = new HashMap<>();
        map.put("newsapi.key", "somekey");
        map.put("newsapi.baseUrl", " ");
        Config badConfig = ConfigFactory.parseMap(map);
        new NewsApiClient(wsClient, badConfig);
    }

    /**
     * Test constructor
     *
     * @author Group
     */
    @Test(expected = IllegalStateException.class)
    public void constructorRequiresBaseUrl() {
        Config badConfig = ConfigFactory.parseString("newsapi.key = \"abc\"");
        new NewsApiClient(wsClient, badConfig);
    }

    /**
     * Test constructor with default
     *
     * @author Group
     */
    @Test
    public void constructorDefault() {
        Map<String, Object> map = new HashMap<>();
        map.put("newsapi.key", "somekey");
        map.put("newsapi.baseUrl", "someurl");
        Config badConfig = ConfigFactory.parseMap(map);
        new NewsApiClient(wsClient, badConfig);
    }

    /**
     * Test searchEverything
     *
     * @author Group
     */
    @Test
    public void searchEverythingParsesArticlesAndCachesResult() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 2,
                  "articles": [
                    {
                      "source": {"id": "techcrunch", "name": "TechCrunch"},
                      "title": "First article",
                      "description": "desc",
                      "url": "https://example.com/a",
                      "publishedAt": "2024-01-01T00:00:00Z"
                    },
                    {
                      "source": {"id": "verge", "name": "The Verge"},
                      "title": "Second article",
                      "description": "desc",
                      "url": "https://example.com/b",
                      "publishedAt": "2024-01-01T01:00:00Z"
                    }
                  ]
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse first = client.searchEverything("java", "publishedAt", 10)
                .toCompletableFuture()
                .get();

        assertEquals(2, first.articles().size());
        assertEquals(2, first.totalResults());
        Article article = first.articles().get(0);
        assertEquals("First article", article.title());
        assertEquals("TechCrunch", article.sourceName());

        NewsApiClient.SearchResponse second = client.searchEverything("java", "publishedAt", 10)
                .toCompletableFuture()
                .get();
        assertEquals(first, second);

        verify(wsClient, times(1)).url(contains("q=java"));
        verify(wsRequest, times(1)).get();
    }

    /**
     * Test searchEverything
     *
     * @author Group
     */
    @Test
    public void searchEverythingEncodesQueryAndHandlesHttpError() throws Exception {
        when(wsResponse.getStatus()).thenReturn(500);
        when(wsResponse.getBody()).thenReturn("failure");

        NewsApiClient.SearchResponse response = client.searchEverything("java news", "relevancy", 5)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
        verify(wsClient).url(argThat(url -> url.contains("java+news")));
        verify(wsRequest).get();
    }

    /**
     * Test searchEverything
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesApiErrorStatus() throws Exception {
        String json = """
                {
                  "status": "error",
                  "code": "apiKeyInvalid",
                  "message": "Invalid key"
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    /**
     * Test searchEverything
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesMalformedJson() throws Exception {
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(null);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
    }

    /**
     * Test searchEverything
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesException() throws Exception {
        CompletableFuture<WSResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("network down"));
        when(wsRequest.get()).thenReturn(failed);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    /**
     * Test searchEverything
     *
     * @author Group
     */
    @Test
    public void searchEverythingFiltersInvalidArticlesAndAvoidsCachingEmptyResult() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 2,
                  "articles": [
                    {
                      "source": {"id": "id1", "name": "Source 1"},
                      "title": "",
                      "url": "https://example.com/invalid"
                    },
                    {
                      "source": {"id": null, "name": "Source 2"},
                      "title": "Valid",
                      "description": null,
                      "url": "https://example.com/valid"
                    }
                  ]
                }
                """;
        String emptyJson = """
                {
                  "status": "ok",
                  "totalResults": 0,
                  "articles": []
                }
                """;
        JsonNode firstNode = mapper.readTree(json);
        JsonNode emptyNode = mapper.readTree(emptyJson);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(firstNode, emptyNode, emptyNode);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertEquals(1, response.articles().size());
        assertEquals("Valid", response.articles().get(0).title());

        NewsApiClient.SearchResponse emptyResponse = client.searchEverything("another", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(emptyResponse.articles().isEmpty());

        NewsApiClient.SearchResponse secondCall = client.searchEverything("another", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(secondCall.articles().isEmpty());
        verify(wsRequest, times(3)).get();
    }

    /**
     * Test searchEverything
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesMissingArticlesArray() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 3
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "publishedAt", 5)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(3, response.totalResults());
    }

    /**
     * Test searchEverything
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesJsonParsingException() throws Exception {
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenThrow(new RuntimeException("boom"));

        NewsApiClient.SearchResponse response = client.searchEverything("java", "publishedAt", 5)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    /**
     * Test searchEverything
     *
     * @author Group
     */
    @Test
    public void searchEverythingContinuesWhenArticleParsingFails() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "ok");
        root.put("totalResults", 2);

        ArrayNode articles = mapper.createArrayNode();
        ObjectNode validArticle = mapper.createObjectNode();
        ObjectNode source = mapper.createObjectNode();
        source.put("id", "id1");
        source.put("name", "Source 1");
        validArticle.set("source", source);
        validArticle.put("title", "Valid Article");
        validArticle.put("url", "https://example.com/valid");
        articles.add(validArticle);

        JsonNode badNode = mock(JsonNode.class);
        when(badNode.has(anyString())).thenThrow(new RuntimeException("broken"));
        articles.add(badNode);

        root.set("articles", articles);

        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(root);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "publishedAt", 5)
                .toCompletableFuture()
                .get();

        assertEquals(1, response.articles().size());
        assertEquals("Valid Article", response.articles().get(0).title());
    }

    /**
     * Test searchSourceProfile
     *
     * @author Group
     */
    @Test
    public void searchSourceProfileWhenFound() {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "ok");
        ArrayNode sources = root.putArray("sources");
        ObjectNode sourceNode = sources.addObject();
        sourceNode.put("id", "techcrunch");
        sourceNode.put("name", "TechCrunch");

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));
        when(wsResponse.asJson()).thenReturn(root);

        // Act
        SourceProfile profile = client.searchSourceProfile("techcrunch")
                .toCompletableFuture().join();

        // Assert
        assertNotNull(profile);
        assertEquals("techcrunch", profile.id);
        assertEquals("TechCrunch", profile.name);

        reset(wsClient, wsRequest, wsResponse);

        // Second call: should be served from cache, no wsClient.url() invoked
        SourceProfile second = client.searchSourceProfile("techcrunch")
                .toCompletableFuture().join();

        assertNotNull(second);
        assertEquals("techcrunch", second.id);

        // Verify no HTTP call was made on cache hit
        verifyNoInteractions(wsClient);

        // try a cache miss
        reset(wsClient, wsRequest, wsResponse);
        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));
        when(wsResponse.asJson()).thenReturn(root);
        SourceProfile third = client.searchSourceProfile("cnn")
                .toCompletableFuture().join();
        assertNull(third);
    }

    /**
     * Test searchSourceProfile
     *
     * @author Group
     */
    @Test
    public void searchSourceProfileOnErrorStatus() {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "error");
        root.put("message", "Invalid API key");

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));
        when(wsResponse.asJson()).thenReturn(root);

        // Act
        SourceProfile profile = client.searchSourceProfile("techcrunch")
                .toCompletableFuture().join();
        assertNull(profile);
    }

    /**
     * Test searchEverythingBySource
     *
     * @author Group
     */
    @Test
    public void searchEverythingBySourceWhenFound() {
        // Arrange: API returns ok with one article
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "ok");
        root.put("totalResults", 1);
        ArrayNode articles = root.putArray("articles");
        ObjectNode articleNode = articles.addObject();
        articleNode.put("title", "Test Article");
        articleNode.put("url", "https://example.com");
        articleNode.put("description", "desc");
        articleNode.put("publishedAt", "2024-01-01T00:00:00Z");
        ObjectNode sourceNode = mapper.createObjectNode();
        sourceNode.put("id", "techcrunch");
        sourceNode.put("name", "TechCrunch");
        articleNode.set("source", sourceNode);

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(root);

        // Act
        NewsApiClient.SearchResponse result = client.searchEverythingBySource("techcrunch")
                .toCompletableFuture().join();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.articles().size());
        assertEquals("Test Article", result.articles().get(0).title());

        // Second call should hit cache
        reset(wsClient, wsRequest, wsResponse);
        NewsApiClient.SearchResponse cached = client.searchEverythingBySource("techcrunch")
                .toCompletableFuture().join();
        assertEquals(1, cached.articles().size());
        verifyNoInteractions(wsClient);
    }

    /**
     * Test searchEverythingBySource
     *
     * @author Group
     */
    @Test
    public void searchEverythingBySourceFallsBackToFilter() {
        // Arrange: API returns ok but no articles
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "ok");
        root.put("totalResults", 0);
        root.putArray("articles"); // empty

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(root);

        // Act
        NewsApiClient.SearchResponse result = client.searchEverythingBySource("cnn")
                .toCompletableFuture().join();

        // Assert: should fallback to filter, but since we didn’t stub that call, expect empty
        assertNotNull(result);
        assertTrue(result.articles().isEmpty());
    }

    /**
     * Test searchEverythingByFilter
     *
     * @author Group
     */
    @Test
    public void searchEverythingByFilterWhenFound() {
        // Arrange: API returns ok with one article from CNN
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "ok");
        root.put("totalResults", 1);
        ArrayNode articles = root.putArray("articles");
        ObjectNode articleNode = articles.addObject();
        articleNode.put("title", "CNN Article");
        articleNode.put("url", "https://cnn.com");
        articleNode.put("description", "desc");
        articleNode.put("publishedAt", "2024-01-01T00:00:00Z");
        ObjectNode sourceNode = mapper.createObjectNode();
        sourceNode.put("id", "cnn");
        sourceNode.put("name", "CNN");
        articleNode.set("source", sourceNode);

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(root);

        // Act
        NewsApiClient.SearchResponse result = client.searchEverythingByFilter("cnn")
                .toCompletableFuture().join();

        // Assert: should filter and return CNN article
        assertEquals(1, result.articles().size());
        assertEquals("CNN Article", result.articles().get(0).title());
    }

    /**
     * Test searchEverythingByFilter
     *
     * @author Group
     */
    @Test
    public void searchEverythingByFilterOnErrorStatus() {
        // Arrange: API returns error
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "error");
        root.put("message", "Invalid API key");

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(root);

        // Act
        NewsApiClient.SearchResponse result = client.searchEverythingByFilter("cnn")
                .toCompletableFuture().join();

        // Assert: should return empty response
        assertTrue(result.articles().isEmpty());
        assertEquals(0, result.totalResults());
    }
}
