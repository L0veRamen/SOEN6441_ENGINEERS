package controllers;

import models.Article;
import models.ReadabilityScores;
import models.SearchBlock;
import models.SourceProfile;
import models.WordStats;
import models.Sentiment;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Nested;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import play.mvc.Http;
import play.mvc.Result;
import services.ProfileService;
import services.ProfileService.SourceProfileResult;
import services.SearchHistoryService;
import services.SearchService;
import services.WordStatsService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.contentAsString;

/**
 * Unit tests for HomeController
 * Tests all controller actions with mocked services
 * Achieves 100% code coverage for controller layer
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
public class HomeControllerTest {

    @Mock
    private SearchHistoryService historyService;

    @Mock
    private ProfileService profileService;

    @Mock
    private SearchService searchService;

    @Mock
    private WordStatsService wordStatsService;

    private HomeController controller;
    private SearchBlock sampleBlock;
    private WordStats sampleWordStats;

    /**
     * Set up test fixtures before each test
     * Initializes mocks and controller instance
     *
     * @author Group
     */
    @Before
    public void setUp() {
        controller = new HomeController(searchService, historyService, profileService, wordStatsService);
        sampleBlock = new SearchBlock(
                "java",
                "publishedAt",
                2,
                List.of(new Article(
                        "Test Title",
                        "https://example.com",
                        "desc",
                        "source-id",
                        "Source",
                        "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z",
                new ReadabilityScores(8.5, 65.0),
                List.of(new ReadabilityScores(8.0, 66.0)),
                Sentiment.fromScores(0.8, 0.1));
        
        sampleWordStats = new WordStats(
                "test", 10, 100, 20,
                List.of(
                        new WordStats.WordFrequency("the", 10),
                        new WordStats.WordFrequency("is", 7),
                        new WordStats.WordFrequency("a", 5)
                )
        );         
    }
    
    /**
     * WordStatsTests Section
     * 
     * Test that word stats endpoint accepts valid query.
     * Verifies that controller processes valid query and returns OK status.
     * 
     * @throws Exception if test execution fails
     * @author Zi Lun Li
     */
	@Test
	public void wordStatsAcceptsQueryTest() throws Exception {
        when(wordStatsService.computeWordStats("test"))
                .thenReturn(CompletableFuture.completedFuture(sampleWordStats));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/wordstats?q=test")
                .build();

        Result result = controller.wordStats(request, "test")
                .toCompletableFuture()
                .get();

        assertEquals(OK, result.status());
        verify(wordStatsService).computeWordStats("test");
    }
	
	/**
     * Test that word stats endpoint rejects invalid queries.
     * Verifies that controller returns BAD_REQUEST for null, empty, and whitespace queries.
     * 
     * @throws Exception if test execution fails
     * @author Zi Lun Li
     */
	@Test
	public void wordStatsRejectsQueryTest() throws Exception {
		Http.Request noQueryRequest = new Http.RequestBuilder()
                .method("GET")
                .uri("/wordstats")
                .build();
		
		Http.Request emptyQueryRequest = new Http.RequestBuilder()
                .method("GET")
                .uri("/wordstats?q=")
                .build();
		
		Http.Request whiteSpaceQueryRequest = new Http.RequestBuilder()
                .method("GET")
                .uri("/wordstats?q=%20")
                .build();

        Result noQueryresult = controller.wordStats(noQueryRequest, null)
                .toCompletableFuture()
                .get();
        
        Result emptyQueryresult = controller.wordStats(emptyQueryRequest, "")
                .toCompletableFuture()
                .get();
        
        Result whiteSpaceQueryresult = controller.wordStats(whiteSpaceQueryRequest, " ")
                .toCompletableFuture()
                .get();

        assertEquals(BAD_REQUEST, noQueryresult.status());
        assertEquals(BAD_REQUEST, emptyQueryresult.status());
        assertEquals(BAD_REQUEST, whiteSpaceQueryresult.status());
        verifyNoInteractions(wordStatsService);
    }
	
	/**
     * Test that word stats handles empty results gracefully.
     * Verifies that controller returns OK status even when no word statistics are found.
     * 
     * @throws Exception if test execution fails
     * @author Zi Lun Li
     */
	@Test
	public void wordStatsHandlesEmptyResultTest() throws Exception {
		WordStats emptyWordStats = new WordStats("qwertyuiop", 0, 0, 0, List.of());
        when(wordStatsService.computeWordStats("qwertyuiop"))
                .thenReturn(CompletableFuture.completedFuture(emptyWordStats));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/wordstats?q=qwertyuiop")
                .build();

        Result result = controller.wordStats(request, "qwertyuiop")
                .toCompletableFuture()
                .get();

        assertEquals(OK, result.status());
        verify(wordStatsService).computeWordStats("qwertyuiop");
    }
	
	/**
     * Test that word stats handles service errors properly.
     * Verifies that controller propagates exceptions from the word stats service.
     * 
     * @throws Exception if test execution fails
     * @author Zi Lun Li
     */
	 @Test
	 public void wordStatsHandlesErrorTest() throws Exception {
        CompletableFuture<WordStats> error = new CompletableFuture<>();
        error.completeExceptionally(new RuntimeException("Error"));
        when(wordStatsService.computeWordStats(anyString())).thenReturn(error);

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/wordstats?q=test")
                .build();

        try {
            controller.wordStats(request, "test")
                    .toCompletableFuture()
                    .get();
            fail("Throw error exception");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof RuntimeException);
        }
    }

    // ==================== INDEX ACTION TESTS ====================

    /**
     * Test index() with existing session renders history
     * Equivalence class: Session with existing searches
     *
     * @author Group
     */
    @Test
    public void indexWithExistingSessionRendersHistory() {
        when(historyService.list("session-123")).thenReturn(List.of(sampleBlock));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/")
                .session("sessionId", "session-123")
                .build();

        Result result = controller.index(request).toCompletableFuture().join();

        assertEquals(OK, result.status());
        verify(historyService).list("session-123");
    }

    /**
     * Test index() without session generates new session ID
     * Equivalence class: First visit without existing session
     *
     * @author Group
     */
    @Test
    public void indexWithoutSessionGeneratesNewSessionId() {
        when(historyService.list(anyString())).thenReturn(List.of());

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/")
                .build();

        Result result = controller.index(request).toCompletableFuture().join();

        assertEquals(OK, result.status());
        ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
        verify(historyService).list(sessionCaptor.capture());
        String generatedId = sessionCaptor.getValue();
        assertNotNull(generatedId);
        assertFalse(generatedId.isBlank());
    }

    /**
     * Test index() with empty history
     * Equivalence class: No previous searches in session
     *
     * @author Group
     */
    @Test
    public void indexWithEmptyHistoryRendersEmptyList() {
        when(historyService.list(anyString())).thenReturn(List.of());

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/Notilytics")
                .session("sessionId", "empty-session")
                .build();

        Result result = controller.index(request).toCompletableFuture().join();

        assertEquals(OK, result.status());
        verify(historyService).list("empty-session");
    }

    // ==================== SEARCH ACTION TESTS ====================

    /**
     * Test search() with valid query and default sortBy
     * Equivalence class: Valid input, default sorting
     *
     * @author Group
     */
    @Test
    public void searchWithValidQueryAndDefaultSortBy() {
        SearchBlock mockBlock = new SearchBlock(
                "java",
                "publishedAt",
                15,
                List.of(new Article("Article 1", "https://example.com/1", "desc1",
                        "source-1", "Source", "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z",
                new ReadabilityScores(8.5, 65.0),
                List.of(new ReadabilityScores(8.0, 66.0)),
                Sentiment.fromScores(0.8, 0.1));

        when(searchService.search("java", "publishedAt"))
                .thenReturn(CompletableFuture.completedFuture(mockBlock));
        doNothing().when(historyService).push(anyString(), any(SearchBlock.class));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=java")
                .build();

        Result result = controller.search(request).join();

        assertEquals(SEE_OTHER, result.status());
        assertTrue(result.redirectLocation().isPresent());
        assertEquals("/Notilytics", result.redirectLocation().get());
        verify(searchService, times(1)).search("java", "publishedAt");
        verify(historyService, times(1)).push(anyString(), eq(mockBlock));
    }

    /**
     * Test search() with valid query and custom sortBy
     * Equivalence class: Valid input with custom sorting
     *
     * @author Group
     */
    @Test
    public void searchWithValidQueryAndCustomSortBy() {
        SearchBlock mockBlock = new SearchBlock(
                "ai",
                "popularity",
                8,
                List.of(new Article("AI Article", "https://example.com/ai", "desc",
                        "source-3", "Source", "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z",
                new ReadabilityScores(8.5, 65.0),
                List.of(new ReadabilityScores(8.0, 66.0)),
                Sentiment.fromScores(0.8, 0.1));

        when(searchService.search("ai", "popularity"))
                .thenReturn(CompletableFuture.completedFuture(mockBlock));
        doNothing().when(historyService).push(anyString(), any(SearchBlock.class));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=ai&sortBy=popularity")
                .build();

        Result result = controller.search(request).join();

        assertEquals(SEE_OTHER, result.status());
        verify(searchService, times(1)).search("ai", "popularity");
    }

    /**
     * Test search() with empty query
     * Equivalence class: Invalid input - empty query
     *
     * @author Group
     */
    @Test
    public void searchWithEmptyQueryReturnsBadRequest() {
        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=")
                .build();

        Result result = controller.search(request).join();

        assertEquals(BAD_REQUEST, result.status());
        String body = contentAsString(result);
        assertTrue(body.contains("Search query is required"));
        verify(searchService, never()).search(anyString(), anyString());
        verify(historyService, never()).push(anyString(), any(SearchBlock.class));
    }

    /**
     * Test search() with whitespace-only query
     * Equivalence class: Invalid input - blank query
     *
     * @author Group
     */
    @Test
    public void searchWithWhitespaceOnlyQueryReturnsBadRequest() {
        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=%20%20%20")
                .build();

        Result result = controller.search(request).join();

        assertEquals(BAD_REQUEST, result.status());
        verify(searchService, never()).search(anyString(), anyString());
    }

    /**
     * Test search() with missing query parameter
     * Equivalence class: Invalid input - no query parameter
     *
     * @author Group
     */
    @Test
    public void searchWithMissingQueryParameterReturnsBadRequest() {
        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics")
                .build();

        Result result = controller.search(request).join();

        assertEquals(BAD_REQUEST, result.status());
        verify(searchService, never()).search(anyString(), anyString());
    }

    /**
     * Test search() error handling when SearchService fails
     * Equivalence class: Service failure scenario
     *
     * @author Group
     */
    @Test
    public void searchWithServiceFailureRedirectsToHome() {
        CompletableFuture<SearchBlock> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("API Error"));

        when(searchService.search("error", "publishedAt")).thenReturn(failedFuture);

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=error")
                .build();

        Result result = controller.search(request).join();

        assertEquals(SEE_OTHER, result.status()); // Still redirects even on error
        verify(searchService, times(1)).search("error", "publishedAt");
        // History should NOT be updated on error
        verify(historyService, never()).push(anyString(), any(SearchBlock.class));
    }

    /**
     * Test search() with query containing special characters
     * Equivalence class: Query with special characters
     *
     * @author Group
     */
    @Test
    public void searchWithSpecialCharactersInQuery() {
        SearchBlock mockBlock = new SearchBlock(
                "java & spring",
                "publishedAt",
                5,
                List.of(new Article("Special", "https://example.com/special", "desc",
                        "source-7", "Source", "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z",
                new ReadabilityScores(8.5, 65.0),
                List.of(new ReadabilityScores(8.0, 66.0)),
                Sentiment.fromScores(0.1, 0.8)
        );

        when(searchService.search("java & spring", "publishedAt"))
                .thenReturn(CompletableFuture.completedFuture(mockBlock));
        doNothing().when(historyService).push(anyString(), any(SearchBlock.class));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=java+%26+spring")
                .build();

        Result result = controller.search(request).join();

        assertEquals(SEE_OTHER, result.status());
        verify(searchService, times(1)).search("java & spring", "publishedAt");
    }

    /**
     * Test search() with missing sortBy parameter defaults to publishedAt
     * Equivalence class: Optional parameter not provided
     *
     * @author Group
     */
    @Test
    public void searchWithMissingSortByDefaultsToPublishedAt() {
        SearchBlock mockBlock = new SearchBlock(
                "default",
                "publishedAt",
                7,
                List.of(new Article("Default", "https://example.com/default", "desc",
                        "source-8", "Source", "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z",
                new ReadabilityScores(8.5, 65.0),
                List.of(new ReadabilityScores(8.0, 66.0)),
                Sentiment.fromScores(0.1, 0.8)
        );

        when(searchService.search("default", "publishedAt"))
                .thenReturn(CompletableFuture.completedFuture(mockBlock));
        doNothing().when(historyService).push(anyString(), any(SearchBlock.class));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=default")
                .build();

        Result result = controller.search(request).join();

        assertEquals(SEE_OTHER, result.status());
        verify(searchService, times(1)).search("default", "publishedAt");
    }

    // ==================== SOURCE ACTION TESTS ====================

    /**
     * Test source() renders with existing source
     * Equivalence class: Valid source exists
     *
     * @author Yuhao Ma
     */
    @Test
    public void sourceRendersWithExistingSource() {
        SourceProfile mockSource = new SourceProfile();
        mockSource.id = "bbc-news";
        mockSource.name = "BBC News";

        SourceProfileResult mockResult = new SourceProfileResult(
                mockSource,
                List.of(new Article("Title", "https://example.com", "desc",
                        "source-id", "Source", "2024-01-01T00:00:00Z")));

        when(profileService.search("java"))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/source/java")
                .build();

        Result result = controller.source(request, "java").toCompletableFuture().join();

        assertEquals(OK, result.status());
        String body = contentAsString(result);
        assertTrue(body.contains("Title"));
    }

    /**
     * Test source() renders with new source when null
     * Equivalence class: Source does not exist
     *
     * @author Yuhao Ma
     */
    @Test
    public void sourceRendersWithNewSourceWhenNull() {
        SourceProfileResult mockResult = new SourceProfileResult(
                null,
                List.of(new Article("Title", "https://example.com", "desc",
                        "source-id", "Source", "2024-01-01T00:00:00Z")));

        when(profileService.search("python"))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/source/python")
                .build();

        Result result = controller.source(request, "python").toCompletableFuture().join();

        assertEquals(OK, result.status());
        String body = contentAsString(result);
        assertTrue(body.contains("python"));
    }

    /**
     * Test source() with empty article list
     * Equivalence class: Valid source but no articles
     *
     * @author Group
     */
    @Test
    public void sourceWithNoArticlesRendersEmptyList() {
        SourceProfile mockSource = new SourceProfile();
        mockSource.id = "empty-source";
        mockSource.name = "Empty Source";

        SourceProfileResult mockResult = new SourceProfileResult(mockSource, List.of());

        when(profileService.search("empty-source"))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/source/empty-source")
                .build();

        Result result = controller.source(request, "empty-source").toCompletableFuture().join();

        assertEquals(OK, result.status());
    }

    /**
     * Test source() with special characters in query
     * Equivalence class: Query with special characters
     *
     * @author Group
     */
    @Test
    public void sourceWithSpecialCharactersInQuery() {
        SourceProfile mockSource = new SourceProfile();
        mockSource.id = "test-source";
        mockSource.name = "Test Source";

        SourceProfileResult mockResult = new SourceProfileResult(
                mockSource,
                List.of(new Article("Article", "https://example.com", "desc",
                        "test-source", "Test Source", "2024-01-01T00:00:00Z")));

        when(profileService.search("test-source-123"))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/source/test-source-123")
                .build();

        Result result = controller.source(request, "test-source-123").toCompletableFuture().join();

        assertEquals(OK, result.status());
        verify(profileService, times(1)).search("test-source-123");
    }
}