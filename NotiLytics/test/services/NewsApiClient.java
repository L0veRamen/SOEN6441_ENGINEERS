package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.Article;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test class for NewsApiClient
 * Tests JSON parsing, caching, and error handling
 * NEVER calls the live NewsAPI - uses mocks only
 *
 * @author Chen Qian
 */
@DisplayName("NewsApiClient Tests")
class NewsApiClientTest {

    @Mock
    private WSClient mockWsClient;

    @Mock
    private WSRequest mockRequest;

    @Mock
    private WSResponse mockResponse;

    private NewsApiClient newsApiClient;
    private Config testConfig;
    private ObjectMapper objectMapper;

    /**
     * Set up test fixtures before each test
     *
     * @author Chen Qian
     */
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();

        // Create test configuration
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("newsapi.key", "test-api-key-12345");
        configMap.put("newsapi.baseUrl", "https://newsapi.org/v2");
        configMap.put("cache.ttl.search", "15 minutes");
        configMap.put("cache.maxSize", 100);
        testConfig = ConfigFactory.parseMap(configMap);

        // Mock WSClient chain
        when(mockWsClient.url(anyString())).thenReturn(mockRequest);
        when(mockRequest.get()).thenReturn(CompletableFuture.completedFuture(mockResponse));

        newsApiClient = new NewsApiClient(mockWsClient, testConfig);
    }

    /**
     * Test successful search with valid response
     *
     * @author Chen Qian
     */
    @Test
    @DisplayName("Search with valid response returns articles")
    void testSearchEverything_ValidResponse() throws Exception {
        // Arrange
        String jsonResponse = createValidJsonResponse();
        JsonNode jsonNode = objectMapper.readTree(jsonResponse);

        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.asJson()).thenReturn(jsonNode);
        when(mockResponse.getBody()).thenReturn(jsonResponse);

        // Act
        NewsApiClient.SearchResponse result = newsApiClient
                .searchEverything("java", "publishedAt", 10)
                .toCompletableFuture()
                .get();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.articles().size());
        assertEquals(100, result.totalResults());

        Article firstArticle = result.articles().get(0);
        assertEquals("Test Article 1", firstArticle.title());
        assertEquals("https://example.com/article1", firstArticle.url());
        assertEquals("Test description 1", firstArticle.description());
        assertEquals("test-source", firstArticle.sourceId());
        assertEquals("Test Source", firstArticle.sourceName());

        // Verify WSClient was called correctly
        verify(mockWsClient, times(1)).url(contains("q=java"));
        verify(mockRequest, times(1)).get();
    }

    /**
     * Test caching functionality
     *
     * @author Chen Qian
     */
    @Test
    @DisplayName("Caching prevents duplicate API calls")
    void testSearchEverything_Caching() throws Exception {
        // Arrange
        String jsonResponse = createValidJsonResponse();
        JsonNode jsonNode = objectMapper.readTree(jsonResponse);

        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.asJson()).thenReturn(jsonNode);
        when(mockResponse.getBody()).thenReturn(jsonResponse);

        // Act - First call
        NewsApiClient.SearchResponse firstResult = newsApiClient
                .searchEverything("java", "publishedAt", 10)
                .toCompletableFuture()
                .get();

        // Act - Second call (should use cache)
        NewsApiClient.SearchResponse secondResult = newsApiClient
                .searchEverything("java", "publishedAt", 10)
                .toCompletableFuture()
                .get();

        // Assert
        assertNotNull(firstResult);
        assertNotNull(secondResult);
        assertEquals(firstResult.articles().size(), secondResult.articles().size());

        // Verify API was only called once (second call used cache)
        verify(mockRequest, times(1)).get();
    }

    /**
     * Test handling of HTTP error response
     *
     * @author Chen Qian
     */
    @Test
    @DisplayName("HTTP error returns empty response")
    void testSearchEverything_HttpError() throws Exception {
        // Arrange
        when(mockResponse.getStatus()).thenReturn(400);
        when(mockResponse.getBody()).thenReturn("Bad Request");

        // Act
        NewsApiClient.SearchResponse result = newsApiClient
                .searchEverything("invalid query", "publishedAt", 10)
                .toCompletableFuture()
                .get();

        // Assert
        assertNotNull(result);
        assertTrue(result.articles().isEmpty());
        assertEquals(0, result.totalResults());
    }

    /**
     * Test handling of API error status
     *
     * @author Chen Qian
     */
    @Test
    @DisplayName("API error status returns empty response")
    void testSearchEverything_ApiError() throws Exception {
        // Arrange
        String errorJson = """
            {
                "status": "error",
                "code": "apiKeyInvalid",
                "message": "Your API key is invalid"
            }
            """;
        JsonNode jsonNode = objectMapper.readTree(errorJson);

        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.asJson()).thenReturn(jsonNode);

        // Act
        NewsApiClient.SearchResponse result = newsApiClient
                .searchEverything("test", "publishedAt", 10)
                .toCompletableFuture()
                .get();

        // Assert
        assertNotNull(result);
        assertTrue(result.articles().isEmpty());
        assertEquals(0, result.totalResults());
    }

    /**
     * Test handling of malformed JSON
     *
     * @author Chen Qian
     */
    @Test
    @DisplayName("Malformed JSON returns empty response")
    void testSearchEverything_MalformedJson() throws Exception {
        // Arrange
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.asJson()).thenReturn(null);

        // Act
        NewsApiClient.SearchResponse result = newsApiClient
                .searchEverything("test", "publishedAt", 10)
                .toCompletableFuture()
                .get();

        // Assert
        assertNotNull(result);
        assertTrue(result.articles().isEmpty());
        assertEquals(0, result.totalResults());
    }

    /**
     * Test handling of exception during API call
     *
     * @author Chen Qian
     */
    @Test
    @DisplayName("Exception during API call returns empty response")
    void testSearchEverything_Exception() throws Exception {
        // Arrange
        when(mockRequest.get()).thenReturn(
                CompletableFuture.failedFuture(new RuntimeException("Network error"))
        );

        // Act
        NewsApiClient.SearchResponse result = newsApiClient
                .searchEverything("test", "publishedAt", 10)
                .toCompletableFuture()
                .get();

        // Assert
        assertNotNull(result);
        assertTrue(result.articles().isEmpty());
        assertEquals(0, result.totalResults());
    }

    /**
     * Test parsing of articles with missing fields
     *
     * @author Chen Qian
     */
    @Test
    @DisplayName("Articles with missing fields are handled gracefully")
    void testSearchEverything_MissingFields() throws Exception {
        // Arrange
        String jsonResponse = createJsonWithMissingFields();
        JsonNode jsonNode = objectMapper.readTree(jsonResponse);

        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.asJson()).thenReturn(jsonNode);
        when(mockResponse.getBody()).thenReturn(jsonResponse);

        // Act
        NewsApiClient.SearchResponse result = newsApiClient
                .searchEverything("test", "publishedAt", 10)
                .toCompletableFuture()
                .get();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.articles().size());

        Article article = result.articles().get(0);
        assertEquals("Article with Missing Fields", article.title());
        assertNull(article.description());
        assertNull(article.sourceId());
    }

    /**
     * Test filtering of articles without titles
     *
     * @author Chen Qian
     */
    @Test
    @DisplayName("Articles without titles are filtered out")
    void testSearchEverything_FilterNoTitle() throws Exception {
        // Arrange
        String jsonResponse = createJsonWithNoTitleArticles();
        JsonNode jsonNode = objectMapper.readTree(jsonResponse);

        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.asJson()).thenReturn(jsonNode);
        when(mockResponse.getBody()).thenReturn(jsonResponse);

        // Act
        NewsApiClient.SearchResponse result = newsApiClient
                .searchEverything("test", "publishedAt", 10)
                .toCompletableFuture()
                .get();

        // Assert
        assertNotNull(result);
        // Should only have 1 article (the one with a valid title)
        assertEquals(1, result.articles().size());
        assertEquals("Valid Article", result.articles().get(0).title());
    }

    /**
     * Helper method to create valid JSON response
     *
     * @return Valid JSON response string
     * @author Chen Qian
     */
    private String createValidJsonResponse() {
        return """
            {
                "status": "ok",
                "totalResults": 100,
                "articles": [
                    {
                        "source": {
                            "id": "test-source",
                            "name": "Test Source"
                        },
                        "title": "Test Article 1",
                        "description": "Test description 1",
                        "url": "https://example.com/article1",
                        "publishedAt": "2024-01-15T10:00:00Z"
                    },
                    {
                        "source": {
                            "id": "test-source-2",
                            "name": "Test Source 2"
                        },
                        "title": "Test Article 2",
                        "description": "Test description 2",
                        "url": "https://example.com/article2",
                        "publishedAt": "2024-01-15T11:00:00Z"
                    }
                ]
            }
            """;
    }

    /**
     * Helper method to create JSON with missing fields
     *
     * @return JSON response with missing fields
     * @author Chen Qian
     */
    private String createJsonWithMissingFields() {
        return """
            {
                "status": "ok",
                "totalResults": 1,
                "articles": [
                    {
                        "source": {
                            "name": "Test Source"
                        },
                        "title": "Article with Missing Fields",
                        "url": "https://example.com/article",
                        "publishedAt": "2024-01-15T10:00:00Z"
                    }
                ]
            }
            """;
    }

    /**
     * Helper method to create JSON with articles that have no titles
     *
     * @return JSON response with no-title articles
     * @author Chen Qian
     */
    private String createJsonWithNoTitleArticles() {
        return """
            {
                "status": "ok",
                "totalResults": 3,
                "articles": [
                    {
                        "source": {"id": "test", "name": "Test"},
                        "title": "",
                        "url": "https://example.com/1"
                    },
                    {
                        "source": {"id": "test", "name": "Test"},
                        "url": "https://example.com/2"
                    },
                    {
                        "source": {"id": "test", "name": "Test"},
                        "title": "Valid Article",
                        "url": "https://example.com/3"
                    }
                ]
            }
            """;
    }
}