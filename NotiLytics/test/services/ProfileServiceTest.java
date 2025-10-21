package services;

import models.Article;
import models.SourceProfile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ProfileServiceTest {

    @Mock
    private NewsApiClient newsApiClient;

    private ProfileService service;

    @Before
    public void setUp() {
        service = new ProfileService(newsApiClient);
    }

    @Test
    public void profileTest() throws Exception {
        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                List.of(new Article(
                        "Title",
                        "https://example.com",
                        "desc",
                        "source",
                        "Source Name",
                        "2024-01-01T00:00:00Z")),
                10);
        when(newsApiClient.searchSourceProfile("source"))
                .thenReturn(CompletableFuture.completedFuture(new SourceProfile()));
        when(newsApiClient.searchEverythingBySource("source"))
                .thenReturn(CompletableFuture.completedFuture(response));
        var result = service.search("source")
                .toCompletableFuture()
                .get();

        assertNotNull(result.source());
    }
}
