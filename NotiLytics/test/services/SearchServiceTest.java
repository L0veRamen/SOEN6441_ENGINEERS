//package services;
//
//import models.Article;
//import models.SearchBlock;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.Mock;
//import org.mockito.junit.MockitoJUnitRunner;
//
//import java.time.ZonedDateTime;
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//
//import static org.junit.Assert.*;
//import static org.mockito.Mockito.*;
//
//@RunWith(MockitoJUnitRunner.class)
//public class SearchServiceTest {
//
//    @Mock
//    private NewsApiClient newsApiClient;
//
//    private SearchService service;
//
//    @Before
//    public void setUp() {
//        service = new SearchService(newsApiClient);
//    }
//
//    @Test
//    public void searchBuildsSearchBlockFromApiResponse() throws Exception {
//        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
//                List.of(new Article(
//                        "Title",
//                        "https://example.com",
//                        "desc",
//                        "source",
//                        "Source Name",
//                        "2024-01-01T00:00:00Z")),
//                42);
//
//        when(newsApiClient.searchEverything("java", "publishedAt", 10))
//                .thenReturn(CompletableFuture.completedFuture(response));
//
//        SearchBlock block = service.search("java", "publishedAt")
//                .toCompletableFuture()
//                .get();
//
//        assertEquals("java", block.query());
//        assertEquals("publishedAt", block.sortBy());
//        assertEquals(42, block.totalResults());
//        assertEquals(response.articles(), block.articles());
//        assertNotNull(block.createdAtIso());
//        ZonedDateTime.parse(block.createdAtIso());
//
//        verify(newsApiClient).searchEverything("java", "publishedAt", 10);
//    }
//}
package services;

import models.Article;
import models.ReadabilityScores;
import models.Sentiment;
import models.SearchBlock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test class for SearchService with ReadabilityService integration
 * Achieves 100% branch coverage
 *
 * @author Chen Qian
 */
public class SearchServiceTest {

    @Mock
    private NewsApiClient newsApiClient;

    @Mock
    private ReadabilityService readabilityService;

    private SearchService service;
    @Mock
    private SentimentAnalysisService sentimentService;

    /**
     * Set up test fixtures
     *
     * @author Chen Qian
     */
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SearchService(newsApiClient, readabilityService, sentimentService);
        when(sentimentService.analyzeArticles(anyList())).thenReturn(Sentiment.NEUTRAL);
    }

    /**
     * Helper method to create article
     *
     * @param title       Article title
     * @param description Article description
     * @return Article object
     * @author Chen Qian
     */
    private Article createArticle(String title, String description) {
        return new Article(
                title,                      // 1. title
                "https://example.com",      // 2. url
                description,                // 3. description
                "test-source",              // 4. sourceId
                "Test Source",              // 5. sourceName
                "2025-01-01T00:00:00Z"      // 6. publishedAt
        );
    }

    /**
     * Test successful search with valid response
     * Equivalence class: Valid search
     *
     * @author Chen Qian
     */
    @Test
    public void testSearchBuildsSearchBlockSuccessfully() throws Exception {
        // Arrange
        List<Article> articles = List.of(
                createArticle("Title 1", "Description 1."),
                createArticle("Title 2", "Description 2.")
        );

        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                articles,
                42
        );

        ReadabilityScores averageScores = new ReadabilityScores(8.5, 65.0);
        ReadabilityScores individualScore1 = new ReadabilityScores(7.0, 70.0);
        ReadabilityScores individualScore2 = new ReadabilityScores(10.0, 60.0);

        when(newsApiClient.searchEverything("java", "publishedAt", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(articles))
                .thenReturn(averageScores);

        when(readabilityService.calculateArticleReadability(articles.get(0)))
                .thenReturn(individualScore1);

        when(readabilityService.calculateArticleReadability(articles.get(1)))
                .thenReturn(individualScore2);

        // Act
        SearchBlock block = service.search("java", "publishedAt")
                .toCompletableFuture()
                .get();

        // Assert
        assertNotNull(block);
        assertEquals("java", block.query());
        assertEquals("publishedAt", block.sortBy());
        assertEquals(42, block.totalResults());
        assertEquals(articles, block.articles());
        assertNotNull(block.createdAtIso());
        assertEquals(averageScores, block.readability());
        assertEquals(2, block.articleReadability().size());
        assertEquals(individualScore1, block.articleReadability().get(0));
        assertEquals(individualScore2, block.articleReadability().get(1));

        // Verify ISO timestamp format
        ZonedDateTime.parse(block.createdAtIso());

        // Verify interactions
        verify(newsApiClient).searchEverything("java", "publishedAt", 10);
        verify(readabilityService).calculateAverageReadability(articles);
        verify(readabilityService, times(2)).calculateArticleReadability(any(Article.class));
    }

    /**
     * Test search with relevancy sort
     * Equivalence class: Different sort options
     *
     * @author Chen Qian
     */
    @Test
    public void testSearchWithRelevancySort() throws Exception {
        // Arrange
        List<Article> articles = List.of(
                createArticle("Relevant Title", "Relevant description.")
        );

        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                articles,
                1
        );

        ReadabilityScores scores = new ReadabilityScores(5.0, 75.0);

        when(newsApiClient.searchEverything("keyword", "relevancy", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(any()))
                .thenReturn(scores);

        when(readabilityService.calculateArticleReadability(any()))
                .thenReturn(scores);

        // Act
        SearchBlock block = service.search("keyword", "relevancy")
                .toCompletableFuture()
                .get();

        // Assert
        assertEquals("keyword", block.query());
        assertEquals("relevancy", block.sortBy());
        verify(newsApiClient).searchEverything("keyword", "relevancy", 10);
    }

    /**
     * Test search with popularity sort
     * Equivalence class: Different sort options
     *
     * @author Chen Qian
     */
    @Test
    public void testSearchWithPopularitySort() throws Exception {
        // Arrange
        List<Article> articles = List.of(
                createArticle("Popular Title", "Popular description.")
        );

        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                articles,
                1
        );

        ReadabilityScores scores = new ReadabilityScores(5.0, 75.0);

        when(newsApiClient.searchEverything("trending", "popularity", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(any()))
                .thenReturn(scores);

        when(readabilityService.calculateArticleReadability(any()))
                .thenReturn(scores);

        // Act
        SearchBlock block = service.search("trending", "popularity")
                .toCompletableFuture()
                .get();

        // Assert
        assertEquals("trending", block.query());
        assertEquals("popularity", block.sortBy());
        verify(newsApiClient).searchEverything("trending", "popularity", 10);
    }

    /**
     * Test search with empty results
     * Equivalence class: Empty results
     *
     * @author Chen Qian
     */
    @Test
    public void testSearchWithEmptyResults() throws Exception {
        // Arrange
        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                List.of(),
                0
        );

        ReadabilityScores emptyScores = new ReadabilityScores(0.0, 0.0);

        when(newsApiClient.searchEverything("nonexistent", "publishedAt", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(List.of()))
                .thenReturn(emptyScores);

        // Act
        SearchBlock block = service.search("nonexistent", "publishedAt")
                .toCompletableFuture()
                .get();

        // Assert
        assertEquals("nonexistent", block.query());
        assertEquals(0, block.totalResults());
        assertTrue(block.articles().isEmpty());
        assertTrue(block.articleReadability().isEmpty());
        assertEquals(emptyScores, block.readability());

        verify(newsApiClient).searchEverything("nonexistent", "publishedAt", 10);
        verify(readabilityService).calculateAverageReadability(List.of());
        verify(readabilityService, never()).calculateArticleReadability(any());
    }

    /**
     * Test search with 10 articles (maximum)
     * Boundary case: Maximum articles
     *
     * @author Chen Qian
     */
    @Test
    public void testSearchWith10Articles() throws Exception {
        // Arrange
        List<Article> articles = List.of(
                createArticle("Title 1", "Description 1."),
                createArticle("Title 2", "Description 2."),
                createArticle("Title 3", "Description 3."),
                createArticle("Title 4", "Description 4."),
                createArticle("Title 5", "Description 5."),
                createArticle("Title 6", "Description 6."),
                createArticle("Title 7", "Description 7."),
                createArticle("Title 8", "Description 8."),
                createArticle("Title 9", "Description 9."),
                createArticle("Title 10", "Description 10.")
        );

        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                articles,
                100
        );

        ReadabilityScores avgScores = new ReadabilityScores(8.0, 65.0);
        ReadabilityScores individualScore = new ReadabilityScores(8.0, 65.0);

        when(newsApiClient.searchEverything("popular", "publishedAt", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(articles))
                .thenReturn(avgScores);

        when(readabilityService.calculateArticleReadability(any()))
                .thenReturn(individualScore);

        // Act
        SearchBlock block = service.search("popular", "publishedAt")
                .toCompletableFuture()
                .get();

        // Assert
        assertEquals(10, block.articles().size());
        assertEquals(10, block.articleReadability().size());
        verify(readabilityService, times(10)).calculateArticleReadability(any());
    }

    /**
     * Test search with single article
     * Boundary case: Single article
     *
     * @author Chen Qian
     */
    @Test
    public void testSearchWithSingleArticle() throws Exception {
        // Arrange
        List<Article> articles = List.of(
                createArticle("Single Title", "Single description.")
        );

        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                articles,
                1
        );

        ReadabilityScores scores = new ReadabilityScores(5.0, 75.0);

        when(newsApiClient.searchEverything("rare", "publishedAt", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(articles))
                .thenReturn(scores);

        when(readabilityService.calculateArticleReadability(any()))
                .thenReturn(scores);

        // Act
        SearchBlock block = service.search("rare", "publishedAt")
                .toCompletableFuture()
                .get();

        // Assert
        assertEquals(1, block.articles().size());
        assertEquals(1, block.articleReadability().size());
        verify(readabilityService, times(1)).calculateArticleReadability(any());
    }

    /**
     * Test that search is asynchronous
     * Branch: CompletionStage behavior
     *
     * @author Chen Qian
     */
    @Test
    public void testSearchIsAsynchronous() {
        // Arrange
        List<Article> articles = List.of(
                createArticle("Async Title", "Async description.")
        );

        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                articles,
                1
        );

        ReadabilityScores scores = new ReadabilityScores(5.0, 75.0);

        when(newsApiClient.searchEverything("async", "publishedAt", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(any()))
                .thenReturn(scores);

        when(readabilityService.calculateArticleReadability(any()))
                .thenReturn(scores);

        // Act
        CompletionStage<SearchBlock> futureBlock = service.search("async", "publishedAt");

        // Assert
        assertNotNull(futureBlock);
        SearchBlock block = futureBlock.toCompletableFuture().join();
        assertNotNull(block);
    }

    /**
     * Test search with complex query string
     * Equivalence class: Complex queries
     *
     * @author Chen Qian
     */
    @Test
    public void testSearchWithComplexQuery() throws Exception {
        // Arrange
        String complexQuery = "artificial intelligence AND machine learning";

        List<Article> articles = List.of(
                createArticle("AI Title", "AI description.")
        );

        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                articles,
                5
        );

        ReadabilityScores scores = new ReadabilityScores(12.0, 50.0);

        when(newsApiClient.searchEverything(complexQuery, "publishedAt", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(any()))
                .thenReturn(scores);

        when(readabilityService.calculateArticleReadability(any()))
                .thenReturn(scores);

        // Act
        SearchBlock block = service.search(complexQuery, "publishedAt")
                .toCompletableFuture()
                .get();

        // Assert
        assertEquals(complexQuery, block.query());
        verify(newsApiClient).searchEverything(complexQuery, "publishedAt", 10);
    }

    /**
     * Test that individual scores match article count
     * Branch: Stream mapping
     *
     * @author Chen Qian
     */
    @Test
    public void testIndividualScoresMatchArticleCount() throws Exception {
        // Arrange
        List<Article> articles = List.of(
                createArticle("Title 1", "Desc 1."),
                createArticle("Title 2", "Desc 2."),
                createArticle("Title 3", "Desc 3.")
        );

        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                articles,
                3
        );

        ReadabilityScores avgScores = new ReadabilityScores(8.0, 65.0);

        when(newsApiClient.searchEverything("test", "publishedAt", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(articles))
                .thenReturn(avgScores);

        when(readabilityService.calculateArticleReadability(articles.get(0)))
                .thenReturn(new ReadabilityScores(7.0, 70.0));
        when(readabilityService.calculateArticleReadability(articles.get(1)))
                .thenReturn(new ReadabilityScores(8.0, 65.0));
        when(readabilityService.calculateArticleReadability(articles.get(2)))
                .thenReturn(new ReadabilityScores(9.0, 60.0));

        // Act
        SearchBlock block = service.search("test", "publishedAt")
                .toCompletableFuture()
                .get();

        // Assert
        assertEquals(articles.size(), block.articleReadability().size());
        verify(readabilityService, times(articles.size())).calculateArticleReadability(any());
    }

    /**
     * Test that createdAtIso timestamp is valid ISO format
     * Branch: ZonedDateTime formatting
     *
     * @author Chen Qian
     */
    @Test
    public void testCreatedAtIsoIsValidFormat() throws Exception {
        // Arrange
        List<Article> articles = List.of(
                createArticle("Title", "Description.")
        );

        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                articles,
                1
        );

        ReadabilityScores scores = new ReadabilityScores(5.0, 75.0);

        when(newsApiClient.searchEverything("test", "publishedAt", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(any()))
                .thenReturn(scores);

        when(readabilityService.calculateArticleReadability(any()))
                .thenReturn(scores);

        // Act
        SearchBlock block = service.search("test", "publishedAt")
                .toCompletableFuture()
                .get();

        // Assert
        assertNotNull(block.createdAtIso());
        try {
            ZonedDateTime.parse(block.createdAtIso());
        } catch (Exception ex) {
            fail("createdAtIso should be ISO timestamp, but parsing failed: " + ex.getMessage());
        }
    }

    /**
     * Test readability service integration
     * Branch: ReadabilityService method calls
     *
     * @author Chen Qian
     */
    @Test
    public void testReadabilityServiceIntegration() throws Exception {
        // Arrange
        List<Article> articles = List.of(
                createArticle("Test", "Test description.")
        );

        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                articles,
                1
        );

        ReadabilityScores avgScores = new ReadabilityScores(8.5, 65.0);
        ReadabilityScores individualScore = new ReadabilityScores(8.5, 65.0);

        when(newsApiClient.searchEverything(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(articles))
                .thenReturn(avgScores);

        when(readabilityService.calculateArticleReadability(articles.get(0)))
                .thenReturn(individualScore);

        // Act
        SearchBlock block = service.search("test", "publishedAt")
                .toCompletableFuture()
                .get();

        // Assert
        assertEquals(avgScores, block.readability());
        assertEquals(1, block.articleReadability().size());
        assertEquals(individualScore, block.articleReadability().get(0));

        // Verify exact method calls
        verify(readabilityService, times(1)).calculateAverageReadability(articles);
        verify(readabilityService, times(1)).calculateArticleReadability(articles.get(0));
    }

    /**
     * Test newsApiClient integration
     * Branch: NewsApiClient method calls
     *
     * @author Chen Qian
     */
    @Test
    public void testNewsApiClientIntegration() throws Exception {
        // Arrange
        List<Article> articles = List.of(
                createArticle("Test", "Test description.")
        );

        NewsApiClient.SearchResponse response = new NewsApiClient.SearchResponse(
                articles,
                1
        );

        ReadabilityScores scores = new ReadabilityScores(5.0, 75.0);

        when(newsApiClient.searchEverything("query", "sortBy", 10))
                .thenReturn(CompletableFuture.completedFuture(response));

        when(readabilityService.calculateAverageReadability(any()))
                .thenReturn(scores);

        when(readabilityService.calculateArticleReadability(any()))
                .thenReturn(scores);

        // Act
        service.search("query", "sortBy").toCompletableFuture().get();

        // Assert - verify exact parameters
        verify(newsApiClient, times(1)).searchEverything("query", "sortBy", 10);
        verify(newsApiClient, times(1)).searchEverything(anyString(), anyString(), eq(10));
    }
}
