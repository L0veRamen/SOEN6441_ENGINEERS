package services;

import models.Article;
import models.SearchBlock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SearchServiceTest {

    @Mock
    private NewsApiClient newsApiClient;

    private SearchService service;

    @Before
    public void setUp() {
        service = new SearchService(newsApiClient);
    }

    @Test
    public void searchBuildsSearchBlockFromApiResponse() throws Exception {
        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                List.of(new Article(
                        "Title",
                        "https://example.com",
                        "desc",
                        "source",
                        "Source Name",
                        "2024-01-01T00:00:00Z")),
                42);

        when(newsApiClient.searchEverything("java", "publishedAt", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        SearchBlock block = service.search("java", "publishedAt")
                .toCompletableFuture()
                .get();

        assertEquals("java", block.query());
        assertEquals("publishedAt", block.sortBy());
        assertEquals(42, block.totalResults());
        assertEquals(response.articles(), block.articles());
        assertNotNull(block.createdAtIso());
        ZonedDateTime.parse(block.createdAtIso());

        verify(newsApiClient).searchEverything("java", "publishedAt", 10);
    }
}
