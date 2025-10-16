package controllers;

import models.Article;
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
import services.SearchService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.SEE_OTHER;

@RunWith(MockitoJUnitRunner.class)
public class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @Mock
    private SearchHistoryService historyService;

    private SearchController controller;
    private SearchBlock sampleBlock;

    @Before
    public void setUp() {
        controller = new SearchController(searchService, historyService);
        sampleBlock = new SearchBlock(
                "java",
                "relevancy",
                1,
                List.of(new Article(
                        "Sample title",
                        "https://example.com",
                        "desc",
                        "source-id",
                        "Source Name",
                        "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z");
    }

    @Test
    public void searchRejectsMissingQuery() {
        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics")
                .build();

        Result result = controller.search(request).toCompletableFuture().join();

        assertEquals(BAD_REQUEST, result.status());
        verifyNoInteractions(searchService, historyService);
    }

    @Test
    public void searchDefaultsSortWhenInvalid() {
        when(searchService.search(eq("java"), eq("publishedAt")))
                .thenReturn(CompletableFuture.completedFuture(sampleBlock));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=java&sortBy=invalid")
                .build();

        Result result = controller.search(request).toCompletableFuture().join();

        assertEquals(SEE_OTHER, result.status());
        assertEquals("/Notilytics", result.redirectLocation().orElse(null));

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        verify(searchService).search(eq("java"), sortCaptor.capture());
        assertEquals("publishedAt", sortCaptor.getValue());
        verify(historyService).push(anyString(), eq(sampleBlock));
    }

    @Test
    public void searchKeepsExistingSessionOnSuccess() {
        when(searchService.search(eq("java"), eq("relevancy")))
                .thenReturn(CompletableFuture.completedFuture(sampleBlock));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=java&sortBy=relevancy")
                .session("sessionId", "session-123")
                .build();

        Result result = controller.search(request).toCompletableFuture().join();

        assertEquals(SEE_OTHER, result.status());
        assertEquals("/Notilytics", result.redirectLocation().orElse(null));
        verify(historyService).push("session-123", sampleBlock);
        assertEquals("session-123", result.session().get("sessionId").orElse(null));
    }

    @Test
    public void searchGeneratesSessionWhenMissing() {
        when(searchService.search(eq("java"), eq("publishedAt")))
                .thenReturn(CompletableFuture.completedFuture(sampleBlock));

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=java")
                .build();

        Result result = controller.search(request).toCompletableFuture().join();

        assertEquals(SEE_OTHER, result.status());
        ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
        verify(historyService).push(sessionCaptor.capture(), eq(sampleBlock));

        String generatedId = sessionCaptor.getValue();
        assertNotNull(generatedId);
        assertFalse(generatedId.isBlank());
        assertEquals(generatedId, result.session().get("sessionId").orElse(null));
    }

    @Test
    public void searchHandlesServiceFailure() {
        CompletableFuture<SearchBlock> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("API failure"));
        when(searchService.search(eq("java"), eq("publishedAt"))).thenReturn(failed);

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/notilytics?q=java")
                .build();

        Result result = controller.search(request).toCompletableFuture().join();

        assertEquals(SEE_OTHER, result.status());
        assertEquals("/Notilytics", result.redirectLocation().orElse(null));
        verify(historyService, never()).push(anyString(), any());
        assertTrue(result.session().get("sessionId").isPresent());
    }
}
