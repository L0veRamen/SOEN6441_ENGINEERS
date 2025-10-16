package controllers;

import models.Article;
import models.ReadabilityScores;
import models.SearchBlock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import play.mvc.Http;
import play.mvc.Result;
import services.SearchHistoryService;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static play.mvc.Http.Status.OK;

@RunWith(MockitoJUnitRunner.class)
public class HomeControllerTest {

    @Mock
    private SearchHistoryService historyService;

    private HomeController controller;
    private SearchBlock sampleBlock;

    @Before
    public void setUp() {
        controller = new HomeController(historyService);
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
                List.of(new ReadabilityScores(8.0, 66.0)));
                
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
}
