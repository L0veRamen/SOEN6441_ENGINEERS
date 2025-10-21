package controllers;

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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.contentAsString;

@RunWith(MockitoJUnitRunner.class)
public class HomeControllerTest {

    @Mock
    private SearchHistoryService historyService;
    @Mock
    private ProfileService profileService;

    private HomeController controller;
    private SearchBlock sampleBlock;

    @Before
    public void setUp() {
        controller = new HomeController(historyService, profileService);
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

    @Test
    public void sourceRendersWithExistingSource() {
        SourceProfileResult mockResult = new SourceProfileResult(new SourceProfile(),
                List.of(new Article("Title", "https://example.com", "desc",
                        "source-id", "Source", "2024-01-01T00:00:00Z")));

        when(profileService.search("java"))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/source/java")
                .build();

        // Act
        Result result = controller.source(request, "java").toCompletableFuture().join();

        // Assert
        assertEquals(OK, result.status());
        String body = contentAsString(result);
        assertTrue(body.contains("Title"));
    }

    @Test
    public void sourceRendersWithNewSourceWhenNull() {
        SourceProfileResult mockResult = new SourceProfileResult(null,
                List.of(new Article("Title", "https://example.com", "desc",
                        "source-id", "Source", "2024-01-01T00:00:00Z")));

        when(profileService.search("python"))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/source/python")
                .build();

        // Act
        Result result = controller.source(request, "python").toCompletableFuture().join();

        // Assert
        assertEquals(OK, result.status());
        String body = contentAsString(result);
        assertTrue(body.contains("python"));
    }
}
