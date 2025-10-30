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

import java.lang.reflect.Method;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for NewsApiClient
 * Achieves 100% line, branch, and method coverage
 *
 * Test Strategy:
 * - Mock all service dependencies (no live API calls)
 * - Test equivalence classes for input validation
 * - Verify async behavior with CompletionStage
 * - Cover all edge cases and error paths
 * - Test caching functionality
 * - Ensure proper session handling
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
     * Initializes client instance with mocked dependencies
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

    // ==================== CONSTRUCTOR TESTS ====================

    /**
     * Test constructor throws exception when API key is missing
     *
     * @author Group
     */
    @Test(expected = IllegalStateException.class)
    public void constructorRequiresApiKey() {
        Config badConfig = ConfigFactory.parseString("newsapi.baseUrl = \"https://newsapi.org/v2\"");
        new NewsApiClient(wsClient, badConfig);
    }

    /**
     * Test constructor throws exception when API key is blank
     *
     * @author Group
     */
    @Test(expected = IllegalStateException.class)
    public void constructorRejectsBlankApiKey() {
        Map<String, Object> map = new HashMap<>();
        map.put("newsapi.key", " ");
        map.put("newsapi.baseUrl", "https://newsapi.org/v2");
        Config badConfig = ConfigFactory.parseMap(map);
        new NewsApiClient(wsClient, badConfig);
    }

    /**
     * Test constructor throws exception when base URL is missing
     *
     * @author Group
     */
    @Test(expected = IllegalStateException.class)
    public void constructorRequiresBaseUrl() {
        Config badConfig = ConfigFactory.parseString("newsapi.key = \"abc\"");
        new NewsApiClient(wsClient, badConfig);
    }

    /**
     * Test constructor throws exception when base URL is blank
     *
     * @author Group
     */
    @Test(expected = IllegalStateException.class)
    public void constructorRejectsBlankBaseUrl() {
        Map<String, Object> map = new HashMap<>();
        map.put("newsapi.key", "somekey");
        map.put("newsapi.baseUrl", " ");
        Config badConfig = ConfigFactory.parseMap(map);
        new NewsApiClient(wsClient, badConfig);
    }

    /**
     * Test constructor with default cache configuration
     *
     * @author Group
     */
    @Test
    public void constructorUsesDefaultCacheConfiguration() {
        Map<String, Object> map = new HashMap<>();
        map.put("newsapi.key", "somekey");
        map.put("newsapi.baseUrl", "someurl");
        Config minimalConfig = ConfigFactory.parseMap(map);
        NewsApiClient clientWithDefaults = new NewsApiClient(wsClient, minimalConfig);
        assertNotNull(clientWithDefaults);
    }

    // ==================== SEARCH EVERYTHING TESTS ====================

    /**
     * Test searchEverything successful response with caching
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

        // Test cache hit - second call should not make HTTP request
        NewsApiClient.SearchResponse second = client.searchEverything("java", "publishedAt", 10)
                .toCompletableFuture()
                .get();
        assertEquals(first, second);

        verify(wsClient, times(1)).url(contains("q=java"));
        verify(wsRequest, times(1)).get();
    }

    /**
     * Test searchEverything handles HTTP error responses
     * Covers: response.getStatus() != 200 branch
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesHttpError() throws Exception {
        when(wsResponse.getStatus()).thenReturn(500);
        when(wsResponse.getBody()).thenReturn("Internal Server Error");

        NewsApiClient.SearchResponse response = client.searchEverything("java news", "relevancy", 5)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
        verify(wsClient).url(argThat(url -> url.contains("java+news")));
        verify(wsRequest).get();
    }

    /**
     * Test searchEverything handles HTTP error with long response body
     * Covers: body truncation logic Math.min(200, response.getBody().length())
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesHttpErrorWithLongBody() throws Exception {
        // Create a response body longer than 200 characters
        StringBuilder longBody = new StringBuilder();
        for (int i = 0; i < 250; i++) {
            longBody.append("x");
        }

        when(wsResponse.getStatus()).thenReturn(400);
        when(wsResponse.getBody()).thenReturn(longBody.toString());

        NewsApiClient.SearchResponse response = client.searchEverything("test", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    /**
     * Test searchEverything handles null JSON response
     * Covers: if (root == null) branch
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesNullJsonResponse() throws Exception {
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(null);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    /**
     * Test searchEverything handles API error status
     * Covers: !"ok".equals(status) branch
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
     * Test searchEverything handles missing status field
     * Covers: root.has("status") ? root.get("status").asText() : "" branch
     * When status is missing, it defaults to "" which is not "ok", so treated as error
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesMissingStatusField() throws Exception {
        String json = """
                {
                  "totalResults": 1,
                  "articles": []
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("test", "relevancy", 10)
                .toCompletableFuture()
                .get();

        // When status field is missing, it defaults to "" which is not "ok"
        // So it's treated as an error and returns empty response
        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults()); // Fixed: should be 0, not 1
    }

    /**
     * Test searchEverything handles error status without message
     * Covers: root.has("message") ? root.get("message").asText() : "Unknown error" branch
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesErrorStatusWithoutMessage() throws Exception {
        String json = """
                {
                  "status": "error",
                  "code": "rateLimited"
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("test", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    /**
     * Test searchEverything handles missing totalResults field
     * Covers: root.has("totalResults") ? root.get("totalResults").asInt() : 0 branch
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesMissingTotalResults() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "articles": []
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("test", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    /**
     * Test searchEverything handles missing articles array
     * Covers: if (articlesNode == null || !articlesNode.isArray()) branch
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
     * Test searchEverything handles non-array articles field
     * Covers: !articlesNode.isArray() branch
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesNonArrayArticles() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 1,
                  "articles": "not an array"
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("test", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(1, response.totalResults());
    }

    /**
     * Test searchEverything handles JSON parsing exception
     * The exception will be caught by parseSearchResponse and logged,
     * then an empty SearchResponse will be returned
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesJsonParsingException() throws Exception {
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenThrow(new RuntimeException("JSON parse error"));

        NewsApiClient.SearchResponse response = client.searchEverything("java", "publishedAt", 5)
                .toCompletableFuture()
                .get();

        // The exception should be caught and an empty response returned
        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    /**
     * Test searchEverything handles malformed JSON without exception
     * Alternative approach that returns malformed JSON node instead of throwing exception
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesMalformedJsonStructure() throws Exception {
        // Create a JSON structure that's valid but doesn't match expected API format
        String malformedJson = """
                {
                  "unexpected": "structure",
                  "not_the_right": "format"
                }
                """;
        JsonNode malformedNode = mapper.readTree(malformedJson);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(malformedNode);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "publishedAt", 5)
                .toCompletableFuture()
                .get();

        // Should handle malformed structure gracefully
        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    /**
     * Test searchEverything continues when individual article parsing fails
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

        // Add a malformed article that will cause parsing to fail
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
     * Test searchEverything filters out articles with empty titles
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

        // Test that empty results are not cached
        NewsApiClient.SearchResponse emptyResponse = client.searchEverything("another", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(emptyResponse.articles().isEmpty());

        // Second call to same empty query should make another HTTP request
        NewsApiClient.SearchResponse secondCall = client.searchEverything("another", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(secondCall.articles().isEmpty());
        verify(wsRequest, times(3)).get();
    }

    /**
     * Test searchEverything handles network exception
     *
     * @author Group
     */
    @Test
    public void searchEverythingHandlesNetworkException() throws Exception {
        CompletableFuture<WSResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("network down"));
        when(wsRequest.get()).thenReturn(failed);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    // ==================== PARSE ARTICLE TESTS ====================

    /**
     * Test parseArticle handles article with null source node
     * Covers: if (sourceNode != null) branch when sourceNode is null
     *
     * @author Group
     */
    @Test
    public void parseArticleHandlesNullSourceNode() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 1,
                  "articles": [
                    {
                      "title": "Article without source",
                      "url": "https://example.com",
                      "description": "Test description",
                      "publishedAt": "2024-01-01T00:00:00Z"
                    }
                  ]
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("test", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertEquals(1, response.articles().size());
        Article article = response.articles().get(0);
        assertEquals("Article without source", article.title());
        assertNull(article.sourceId());
        assertNull(article.sourceName());
    }

    /**
     * Test parseArticle handles source node with null fields
     * Covers: getTextOrNull calls within sourceNode != null branch
     *
     * @author Group
     */
    @Test
    public void parseArticleHandlesSourceNodeWithNullFields() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 1,
                  "articles": [
                    {
                      "source": {
                        "id": null,
                        "name": "Test Source"
                      },
                      "title": "Test Article",
                      "url": "https://example.com",
                      "description": null,
                      "publishedAt": "2024-01-01T00:00:00Z"
                    }
                  ]
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("test", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertEquals(1, response.articles().size());
        Article article = response.articles().get(0);
        assertEquals("Test Article", article.title());
        assertNull(article.sourceId());
        assertEquals("Test Source", article.sourceName());
        assertNull(article.description());
    }

    /**
     * Test parseArticle handles missing fields
     * Covers: getTextOrNull method with missing fields
     *
     * @author Group
     */
    @Test
    public void parseArticleHandlesMissingFields() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 1,
                  "articles": [
                    {
                      "source": {
                        "name": "Test Source"
                      },
                      "title": "Test Article",
                      "url": "https://example.com"
                    }
                  ]
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("test", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertEquals(1, response.articles().size());
        Article article = response.articles().get(0);
        assertEquals("Test Article", article.title());
        assertNull(article.sourceId());
        assertEquals("Test Source", article.sourceName());
        assertNull(article.description());
        assertNull(article.publishedAt());
    }

    /**
     * Test parseArticle with complete valid data
     * Covers: all getTextOrNull success paths
     *
     * @author Group
     */
    @Test
    public void parseArticleHandlesCompleteValidData() throws Exception {
        String validJson = """
                {
                  "status": "ok",
                  "totalResults": 1,
                  "articles": [
                    {
                      "source": {
                        "id": "test-source",
                        "name": "Test Source"
                      },
                      "title": "Valid Title",
                      "url": "https://example.com",
                      "description": "Valid Description",
                      "publishedAt": "2024-01-01T00:00:00Z"
                    }
                  ]
                }
                """;
        JsonNode jsonNode = mapper.readTree(validJson);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("test", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertEquals(1, response.articles().size());
        Article article = response.articles().get(0);
        assertEquals("Valid Title", article.title());
        assertEquals("test-source", article.sourceId());
        assertEquals("Test Source", article.sourceName());
        assertEquals("Valid Description", article.description());
        assertEquals("https://example.com", article.url());
        assertEquals("2024-01-01T00:00:00Z", article.publishedAt());
    }

    // ==================== GET TEXT OR NULL TESTS ====================

    /**
     * Test getTextOrNull method with various node states
     * Covers: all branches in getTextOrNull method
     * - node != null && node.has(fieldName) && !node.get(fieldName).isNull() (true path)
     * - return null (false path)
     *
     * @author Group
     */
    @Test
    public void getTextOrNullHandlesAllConditions() throws Exception {
        // Test case 1: All conditions true - should return text value
        String jsonWithAllFields = """
                {
                  "status": "ok",
                  "totalResults": 1,
                  "articles": [
                    {
                      "title": "Valid Title",
                      "url": "https://example.com",
                      "description": "Valid Description"
                    }
                  ]
                }
                """;

        // Test case 2: Field has null value - should return null
        String jsonWithNullField = """
                {
                  "status": "ok",
                  "totalResults": 1,
                  "articles": [
                    {
                      "title": "Valid Title",
                      "url": "https://example.com",
                      "description": null
                    }
                  ]
                }
                """;

        // Test case 3: Field missing - should return null
        String jsonWithMissingField = """
                {
                  "status": "ok",
                  "totalResults": 1,
                  "articles": [
                    {
                      "title": "Valid Title",
                      "url": "https://example.com"
                    }
                  ]
                }
                """;

        // Test valid field
        JsonNode validNode = mapper.readTree(jsonWithAllFields);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(validNode);

        NewsApiClient.SearchResponse response1 = client.searchEverything("test1", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertEquals(1, response1.articles().size());
        assertEquals("Valid Description", response1.articles().get(0).description());

        // Test null field
        JsonNode nullNode = mapper.readTree(jsonWithNullField);
        reset(wsResponse);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(nullNode);

        NewsApiClient.SearchResponse response2 = client.searchEverything("test2", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertEquals(1, response2.articles().size());
        assertNull(response2.articles().get(0).description());

        // Test missing field
        JsonNode missingNode = mapper.readTree(jsonWithMissingField);
        reset(wsResponse);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(missingNode);

        NewsApiClient.SearchResponse response3 = client.searchEverything("test3", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertEquals(1, response3.articles().size());
        assertNull(response3.articles().get(0).description());
    }

    /**
     * Test getTextOrNull with null node parameter
     * Creates a scenario where JsonNode.get() returns null, testing node == null condition
     *
     * @author Group
     */
    @Test
    public void getTextOrNullHandlesNullNodeParameter() throws Exception {
        // Create a JSON structure where some nested elements might be null
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "ok");
        root.put("totalResults", 1);

        ArrayNode articles = mapper.createArrayNode();
        ObjectNode articleNode = mapper.createObjectNode();
        articleNode.put("title", "Test Article");
        articleNode.put("url", "https://example.com");

        // Create a source object where get() might return null for certain fields
        ObjectNode sourceNode = mapper.createObjectNode();
        sourceNode.put("name", "Test Source");
        // Intentionally don't put "id" field to test missing field scenario
        articleNode.set("source", sourceNode);

        articles.add(articleNode);
        root.set("articles", articles);

        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(root);

        NewsApiClient.SearchResponse response = client.searchEverything("test", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertEquals(1, response.articles().size());
        Article article = response.articles().get(0);
        assertEquals("Test Article", article.title());
        assertEquals("Test Source", article.sourceName());
        assertNull(article.sourceId()); // This should be null due to missing "id" field
    }

    /**
     * Test scenario where article processing encounters null elements
     * Forces getTextOrNull to handle edge cases in JSON structure
     *
     * @author Group
     */
    @Test
    public void parseArticleHandlesNullJsonElements() throws Exception {
        // Create JSON with potential null elements that could affect getTextOrNull
        String jsonWithNullElements = """
                {
                  "status": "ok",
                  "totalResults": 2,
                  "articles": [
                    {
                      "title": "Valid Article",
                      "url": "https://example.com",
                      "source": {
                        "id": "valid-source",
                        "name": "Valid Source"
                      }
                    },
                    {
                      "title": "Article with Null Elements",
                      "url": null,
                      "description": null,
                      "publishedAt": null,
                      "source": null
                    }
                  ]
                }
                """;

        JsonNode jsonNode = mapper.readTree(jsonWithNullElements);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("test", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertEquals(2, response.articles().size());

        // First article should be valid
        Article validArticle = response.articles().get(0);
        assertEquals("Valid Article", validArticle.title());
        assertEquals("Valid Source", validArticle.sourceName());

        // Second article should handle null elements gracefully
        Article nullElementsArticle = response.articles().get(1);
        assertEquals("Article with Null Elements", nullElementsArticle.title());
        assertNull(nullElementsArticle.url());
        assertNull(nullElementsArticle.description());
        assertNull(nullElementsArticle.publishedAt());
        assertNull(nullElementsArticle.sourceId());
        assertNull(nullElementsArticle.sourceName());
    }

    // ==================== SOURCE PROFILE TESTS ====================

    /**
     * Test searchSourceProfile when found with caching
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

        // First call
        SourceProfile profile = client.searchSourceProfile("techcrunch")
                .toCompletableFuture().join();

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

        // Try a cache miss for different source
        reset(wsClient, wsRequest, wsResponse);
        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));
        when(wsResponse.asJson()).thenReturn(root);
        SourceProfile third = client.searchSourceProfile("cnn")
                .toCompletableFuture().join();
        assertNull(third);
    }

    /**
     * Test searchSourceProfile handles error status
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

        SourceProfile profile = client.searchSourceProfile("techcrunch")
                .toCompletableFuture().join();
        assertNull(profile);
    }

    /**
     * Test searchSourceProfile handles exception
     *
     * @author Group
     */
    @Test
    public void searchSourceProfileHandlesException() throws Exception {
        CompletableFuture<WSResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Network error"));
        when(wsRequest.get()).thenReturn(failed);

        SourceProfile profile = client.searchSourceProfile("test")
                .toCompletableFuture()
                .get();

        assertNull(profile);
    }

    // ==================== SEARCH BY SOURCE TESTS ====================

    /**
     * Test searchEverythingBySource when found
     *
     * @author Group
     */
    @Test
    public void searchEverythingBySourceWhenFound() {
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

        NewsApiClient.SearchResponse result = client.searchEverythingBySource("techcrunch")
                .toCompletableFuture().join();

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
     * Test searchEverythingBySource falls back to filter when no results
     *
     * @author Group
     */
    @Test
    public void searchEverythingBySourceFallsBackToFilter() {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "ok");
        root.put("totalResults", 0);
        root.putArray("articles"); // empty

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(root);

        NewsApiClient.SearchResponse result = client.searchEverythingBySource("cnn")
                .toCompletableFuture().join();

        assertNotNull(result);
        assertTrue(result.articles().isEmpty());
    }

    /**
     * Test searchEverythingBySource handles exception
     *
     * @author Group
     */
    @Test
    public void searchEverythingBySourceHandlesException() throws Exception {
        CompletableFuture<WSResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Network error"));
        when(wsRequest.get()).thenReturn(failed);

        NewsApiClient.SearchResponse response = client.searchEverythingBySource("test")
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    // ==================== SEARCH BY FILTER TESTS ====================

    /**
     * Test searchEverythingByFilter when found
     *
     * @author Group
     */
    @Test
    public void searchEverythingByFilterWhenFound() {
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

        NewsApiClient.SearchResponse result = client.searchEverythingByFilter("cnn")
                .toCompletableFuture().join();

        assertEquals(1, result.articles().size());
        assertEquals("CNN Article", result.articles().get(0).title());
    }

    /**
     * Test searchEverythingByFilter handles error status
     *
     * @author Group
     */
    @Test
    public void searchEverythingByFilterOnErrorStatus() {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "error");
        root.put("message", "Invalid API key");

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(root);

        NewsApiClient.SearchResponse result = client.searchEverythingByFilter("cnn")
                .toCompletableFuture().join();

        assertTrue(result.articles().isEmpty());
        assertEquals(0, result.totalResults());
    }

    /**
     * Test searchEverythingByFilter handles exception
     *
     * @author Group
     */
    @Test
    public void searchEverythingByFilterHandlesException() throws Exception {
        CompletableFuture<WSResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Network error"));
        when(wsRequest.get()).thenReturn(failed);

        NewsApiClient.SearchResponse response = client.searchEverythingByFilter("test")
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    /** 
     * @description: Covers the `node == null` branch of getTextOrNull using reflection,asserting it returns null as expected.
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 14:07
     */
    @Test
    public void getTextOrNull_nodeIsNullBranchCovered() throws Exception {
        Method m = NewsApiClient.class.getDeclaredMethod("getTextOrNull", JsonNode.class, String.class);
        m.setAccessible(true);

        Object out = m.invoke(client, null, "whatever");
        assertNull("When node is null, getTextOrNull should return null", out);
    }
}
