package controllers;

//import models.Article;
//import models.SearchBlock;
//import models.SourceProfile;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Mock;
//import org.mockito.junit.MockitoJUnitRunner;
//import play.mvc.Http;
//import play.mvc.Result;
//import services.ProfileService;
//import services.ProfileService.SourceProfileResult;
//import services.SearchHistoryService;
//
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//
//import static org.junit.Assert.*;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.*;
//import static play.mvc.Http.Status.OK;
//import static play.test.Helpers.contentAsString;
//
//@RunWith(MockitoJUnitRunner.class)
//public class HomeControllerTest {
//
//    @Mock
//    private SearchHistoryService historyService;
//    @Mock
//    private ProfileService profileService;
//
//    private HomeController controller;
//    private SearchBlock sampleBlock;
//
//    @Before
//    public void setUp() {
//        controller = new HomeController(historyService, profileService);
//        sampleBlock = new SearchBlock(
//                "java",
//                "publishedAt",
//                2,
//                List.of(new Article(
//                        "Test Title",
//                        "https://example.com",
//                        "desc",
//                        "source-id",
//                        "Source",
//                        "2024-01-01T00:00:00Z")),
//                "2024-01-01T00:00:00Z");
//    }
//
//    @Test
//    public void indexWithExistingSessionRendersHistory() {
//        when(historyService.list("session-123")).thenReturn(List.of(sampleBlock));
//
//        Http.Request request = new Http.RequestBuilder()
//                .method("GET")
//                .uri("/")
//                .session("sessionId", "session-123")
//                .build();
//
//        Result result = controller.index(request).toCompletableFuture().join();
//
//        assertEquals(OK, result.status());
//        verify(historyService).list("session-123");
//    }
//
//    @Test
//    public void indexWithoutSessionGeneratesNewSessionId() {
//        when(historyService.list(anyString())).thenReturn(List.of());
//
//        Http.Request request = new Http.RequestBuilder()
//                .method("GET")
//                .uri("/")
//                .build();
//
//        Result result = controller.index(request).toCompletableFuture().join();
//
//        assertEquals(OK, result.status());
//        ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
//        verify(historyService).list(sessionCaptor.capture());
//        String generatedId = sessionCaptor.getValue();
//        assertNotNull(generatedId);
//        assertFalse(generatedId.isBlank());
//    }
//
//    @Test
//    public void sourceRendersWithExistingSource() {
//        SourceProfileResult mockResult = new SourceProfileResult(new SourceProfile(),
//                List.of(new Article("Title", "https://example.com", "desc",
//                        "source-id", "Source", "2024-01-01T00:00:00Z")));
//
//        when(profileService.search("java"))
//                .thenReturn(CompletableFuture.completedFuture(mockResult));
//
//        Http.Request request = new Http.RequestBuilder()
//                .method("GET")
//                .uri("/source/java")
//                .build();
//
//        // Act
//        Result result = controller.source(request, "java").toCompletableFuture().join();
//
//        // Assert
//        assertEquals(OK, result.status());
//        String body = contentAsString(result);
//        assertTrue(body.contains("Title"));
//    }
//
//    @Test
//    public void sourceRendersWithNewSourceWhenNull() {
//        SourceProfileResult mockResult = new SourceProfileResult(null,
//                List.of(new Article("Title", "https://example.com", "desc",
//                        "source-id", "Source", "2024-01-01T00:00:00Z")));
//
//        when(profileService.search("python"))
//                .thenReturn(CompletableFuture.completedFuture(mockResult));
//
//        Http.Request request = new Http.RequestBuilder()
//                .method("GET")
//                .uri("/source/python")
//                .build();
//
//        // Act
//        Result result = controller.source(request, "python").toCompletableFuture().join();
//
//        // Assert
//        assertEquals(OK, result.status());
//        String body = contentAsString(result);
//        assertTrue(body.contains("python"));
//    }
//}
import models.Article;
import models.SearchBlock;
import models.SourceProfile;
import org.junit.Before;
import org.junit.Test;
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
 *
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

    private HomeController controller;
    private SearchBlock sampleBlock;

    /**
     * Set up test fixtures before each test
     * Initializes mocks and controller instance
     *
     * @author Group
     */
    @Before
    public void setUp() {
        controller = new HomeController(searchService, historyService, profileService);
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
                "2024-01-01T00:00:00Z");
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
                "2024-01-01T00:00:00Z");

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
     * Test search() with valid query and custom sortBy (relevancy)
     * Equivalence class: Valid input, custom sorting
     *
     * @author Group
     */
    @Test
    public void searchWithCustomSortByRelevancy() {
        SearchBlock mockBlock = new SearchBlock(
                "spring",
                "relevancy",
                20,
                List.of(new Article("Spring Article", "https://example.com/spring", "desc",
                        "source-2", "Source", "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z");

        when(searchService.search("spring", "relevancy"))
                .thenReturn(CompletableFuture.completedFuture(mockBlock));
        doNothing().when(historyService).push(anyString(), any(SearchBlock.class));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=spring&sortBy=relevancy")
                .build();

        Result result = controller.search(request).join();

        assertEquals(SEE_OTHER, result.status());
        verify(searchService, times(1)).search("spring", "relevancy");
        verify(historyService, times(1)).push(anyString(), eq(mockBlock));
    }

    /**
     * Test search() with valid query and popularity sort
     * Equivalence class: Valid input, popularity sorting
     *
     * @author Group
     */
    @Test
    public void searchWithSortByPopularity() {
        SearchBlock mockBlock = new SearchBlock(
                "ai",
                "popularity",
                25,
                List.of(new Article("AI News", "https://example.com/ai", "desc",
                        "source-3", "Source", "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z");

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
     * Test search() with invalid sortBy parameter
     * Equivalence class: Invalid sortBy, should default to publishedAt
     *
     * @author Group
     */
    @Test
    public void searchWithInvalidSortByDefaultsToPublishedAt() {
        SearchBlock mockBlock = new SearchBlock(
                "testing",
                "publishedAt",
                8,
                List.of(new Article("Test", "https://example.com/test", "desc",
                        "source-4", "Source", "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z");

        when(searchService.search("testing", "publishedAt"))
                .thenReturn(CompletableFuture.completedFuture(mockBlock));
        doNothing().when(historyService).push(anyString(), any(SearchBlock.class));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=testing&sortBy=invalid")
                .build();

        Result result = controller.search(request).join();

        assertEquals(SEE_OTHER, result.status());
        // Should default to "publishedAt"
        verify(searchService, times(1)).search("testing", "publishedAt");
    }

    /**
     * Test search() with existing session ID
     * Equivalence class: Returning user with session
     *
     * @author Group
     */
    @Test
    public void searchWithExistingSessionIdUsesExistingSession() {
        SearchBlock mockBlock = new SearchBlock(
                "session",
                "publishedAt",
                12,
                List.of(new Article("Session Test", "https://example.com/session", "desc",
                        "source-5", "Source", "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z");

        when(searchService.search("session", "publishedAt"))
                .thenReturn(CompletableFuture.completedFuture(mockBlock));
        doNothing().when(historyService).push(eq("existing-session-456"), any(SearchBlock.class));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=session")
                .session("sessionId", "existing-session-456")
                .build();

        Result result = controller.search(request).join();

        assertEquals(SEE_OTHER, result.status());
        verify(historyService, times(1)).push(eq("existing-session-456"), eq(mockBlock));
    }

    /**
     * Test search() without existing session creates new session
     * Equivalence class: New user without session
     *
     * @author Group
     */
    @Test
    public void searchWithoutSessionCreatesNewSession() {
        SearchBlock mockBlock = new SearchBlock(
                "newsession",
                "publishedAt",
                10,
                List.of(new Article("New Session", "https://example.com/new", "desc",
                        "source-6", "Source", "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z");

        when(searchService.search("newsession", "publishedAt"))
                .thenReturn(CompletableFuture.completedFuture(mockBlock));
        doNothing().when(historyService).push(anyString(), any(SearchBlock.class));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=newsession")
                .build();

        Result result = controller.search(request).join();

        assertEquals(SEE_OTHER, result.status());
        ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
        verify(historyService, times(1)).push(sessionCaptor.capture(), eq(mockBlock));

        String capturedSessionId = sessionCaptor.getValue();
        assertNotNull(capturedSessionId);
        assertFalse(capturedSessionId.isBlank());
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
                "2024-01-01T00:00:00Z");

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
                "2024-01-01T00:00:00Z");

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