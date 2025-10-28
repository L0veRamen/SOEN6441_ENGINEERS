package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.WordStats;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WordStatsService.
 * Tests word frequency computation, filtering, and error handling.
 * Uses Mockito to mock NewsAPI responses.
 * 
 * @author Zi Lun Li
 */
@RunWith(MockitoJUnitRunner.class)
public class WordStatsServiceTest {

    @Mock
    private WSClient wsClient;

    @Mock
    private WSRequest wsRequest;

    @Mock
    private WSResponse wsResponse;

    private ObjectMapper mapper;
    private Config config;
    private NewsApiClient newsApiClient;
    private WordStatsService wordStatsService;

    /**
     * Set up test fixtures before each test.
     * Creates mock objects and initializes services.
     * 
     * @author Zi Lun Li
     */
    @Before
    public void setUp() {
        mapper = new ObjectMapper();
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("newsapi.key", "test-api-key");
        configMap.put("newsapi.baseUrl", "https://newsapi.org/v2");
        configMap.put("cache.ttl.search", "60 minutes");
        configMap.put("cache.maxSize", 1000);
        config = ConfigFactory.parseMap(configMap);

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));

        newsApiClient = new NewsApiClient(wsClient, config);
        wordStatsService = new WordStatsService(newsApiClient);
    }

    /**
     * Test basic word frequency computation.
     * Verifies that words are counted correctly and sorted in descending order.
     * 
     * @throws Exception if test execution fails
     * @author Zi Lun Li
     */
    @Test
    public void computeWordStatsTest() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 2,
                  "articles": [
                    {
                      "source": {"id": "test", "name": "Test Source"},
                      "author": "Author 1",
                      "title": "Test 1",
                      "description": "this is a test, testing a test",
                      "url": "https://test.com/1",
                      "content": "Test123",
                      "publishedAt": "2024-01-01T00:00:00Z"
                      
                    },
                    {
                      "source": {"id": "test", "name": "Test Source"},
                      "author": null,
                      "title": "Test 2",
                      "description": "this is not a test, testing another test",
                      "url": "https://test.com/2",
                      "content": null,
                      "publishedAt": "2024-01-01T01:00:00Z"
                    }
                  ]
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        WordStats stats = wordStatsService.computeWordStats("test")
                .toCompletableFuture()
                .get();

        assertEquals("test", stats.query());
        assertEquals(2, stats.totalArticles());
        assertTrue(stats.uniqueWords() > 0);
        assertTrue(stats.totalWords() > 0);
        assertNotNull(stats.wordFrequencies());
        assertFalse(stats.wordFrequencies().isEmpty());

        List<WordStats.WordFrequency> frequencies = stats.wordFrequencies();
        
        assertEquals("test", frequencies.get(0).word());
        assertEquals(4, frequencies.get(0).count());
        
        WordStats.WordFrequency second = frequencies.get(1);
        assertEquals(2, second.count());
        assertTrue("Second word should be one of the tied words",
            second.word().equals("this") || 
            second.word().equals("is") || 
            second.word().equals("testing") || 
            second.word().equals("the")
        );
          
        // Verify descending order
        for (int i = 0; i < frequencies.size() - 1; i++) {
            assertTrue(frequencies.get(i).count() >= frequencies.get(i + 1).count());
        }
    }
    
    /**
     * Test lowercase conversion.
     * Verifies that words in different cases are counted as the same word.
     * 
     * @throws Exception if test execution fails
     * @author Zi Lun Li
     */
    @Test
    public void computeWordStatsToLowercase() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 1,
                  "articles": [
                    {
                      "source": {"id": "test", "name": "Test"},
                      "title": "Test",
                      "description": "test TEST tEsT",
                      "url": "https://test.com/1",
                      "publishedAt": "2024-01-01T01:00:00Z"
                    }
                  ]
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        WordStats stats = wordStatsService.computeWordStats("test")
                .toCompletableFuture()
                .get();

        List<WordStats.WordFrequency> frequencies = stats.wordFrequencies();
        
        WordStats.WordFrequency testFrequency = frequencies.stream()
                .filter(wf -> wf.word().equals("test"))
                .findFirst()
                .orElse(null);
        
        assertNotNull("The word 'test' exists", testFrequency);
        assertEquals(3, testFrequency.count());
    }

    /**
     * Test handling of empty articles list.
     * Verifies that service returns empty statistics when no articles found.
     * 
     * @throws Exception if test execution fails
     * @author Zi Lun Li
     */
    @Test
    public void computeWordStatsEmptyArticlesTest() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 0,
                  "articles": []
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        WordStats stats = wordStatsService.computeWordStats("test")
                .toCompletableFuture()
                .get();

        assertEquals("test", stats.query());
        assertEquals(0, stats.totalArticles());
        assertEquals(0, stats.uniqueWords());
        assertEquals(0, stats.totalWords());
        assertTrue(stats.wordFrequencies().isEmpty());
    }

    /**
     * Test handling of null descriptions.
     * Verifies that null descriptions are filtered out without errors.
     * 
     * @throws Exception if test execution fails
     * @author Zi Lun Li
     */
    @Test
    public void computeWordStatsNullDescriptionTest() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 2,
                  "articles": [
                    {
                      "source": {"id": "test", "name": "Test Source"},
                      "title": "Test 1",
                      "description": null,
                      "url": "https://test.com/1",
                      "publishedAt": "2024-01-01T00:00:00Z"
                    },
                    {
                      "source": {"id": "test", "name": "Test Source"},
                      "title": "Test 2",
                      "description": null,
                      "url": "https://test.com/2",
                      "publishedAt": "2024-01-01T01:00:00Z"
                    }
                  ]
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        WordStats stats = wordStatsService.computeWordStats("test")
                .toCompletableFuture()
                .get();

        assertEquals(2, stats.totalArticles());
        assertTrue(stats.wordFrequencies().isEmpty());
    }

    /**
     * Test handling of empty and blank descriptions.
     * Verifies that empty strings and whitespace-only descriptions are filtered out.
     * 
     * @throws Exception if test execution fails
     * @author Zi Lun Li
     */
    @Test
    public void computeWordStatsEmptyDescriptionTest() throws Exception {
    	String json = """
                {
                  "status": "ok",
                  "totalResults": 2,
                  "articles": [
                    {
                      "source": {"id": "test", "name": "Test Source"},
                      "title": "Test 1",
                      "description": "",
                      "url": "https://test.com/1",
                      "publishedAt": "2024-01-01T00:00:00Z"
                    },
                    {
                      "source": {"id": "test", "name": "Test Source"},
                      "title": "Test 2",
                      "description": " ",
                      "url": "https://test.com/2",
                      "publishedAt": "2024-01-01T01:00:00Z"
                    }
                  ]
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        WordStats stats = wordStatsService.computeWordStats("test")
                .toCompletableFuture()
                .get();

        assertEquals(2, stats.totalArticles());
        assertEquals(0, stats.uniqueWords());
        assertEquals(0, stats.totalWords());
        assertTrue(stats.wordFrequencies().isEmpty());
    }
    
    /**
     * Test error handling when API call fails.
     * Verifies that service returns empty statistics on exception.
     * 
     * @throws Exception if test execution fails
     * @author Zi Lun Li
     */
    @Test
    public void computeStatsErrorTest() throws Exception {
        CompletableFuture<WSResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Error"));
        when(wsRequest.get()).thenReturn(failed);

        // This should NOT throw because service handles it
        WordStats stats = wordStatsService.computeWordStats("test")
                .toCompletableFuture()
                .get();

        assertEquals("test", stats.query());
        assertEquals(0, stats.totalArticles());
        assertEquals(0, stats.uniqueWords());
        assertEquals(0, stats.totalWords());
        assertTrue(stats.wordFrequencies().isEmpty());
    }
}