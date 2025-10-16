package models;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test class for SearchBlock record
 * Achieves 100% branch coverage
 *
 * @author Chen Qian
 */
public class SearchBlockTest {

    /**
     * Helper method to create article
     *
     * @param title Article title
     * @return Article object
     * @author Chen Qian
     */
    private Article createArticle(String title) {
        return new Article(
                title,                      // 1. title
                "https://example.com",      // 2. url
                "Test Description",         // 3. description
                "test-source",              // 4. sourceId
                "Test Source",              // 5. sourceName
                "2025-01-01T00:00:00Z"      // 6. publishedAt
        );
    }

    /**
     * Test basic constructor and accessors
     * Equivalence class: Valid construction
     *
     * @author Chen Qian
     */
    @Test
    public void testConstructorAndAccessors() {
        List<Article> articles = List.of(createArticle("Test Article"));
        ReadabilityScores avgScores = new ReadabilityScores(8.5, 65.0);
        List<ReadabilityScores> individualScores = List.of(new ReadabilityScores(8.5, 65.0));

        SearchBlock block = new SearchBlock(
                "test query",
                "publishedAt",
                10,
                articles,
                "2025-01-01T12:00:00Z",
                avgScores,
                individualScores
        );

        assertEquals("test query", block.query());
        assertEquals("publishedAt", block.sortBy());
        assertEquals(10, block.totalResults());
        assertEquals(articles, block.articles());
        assertEquals("2025-01-01T12:00:00Z", block.createdAtIso());
        assertEquals(avgScores, block.readability());
        assertEquals(individualScores, block.articleReadability());
    }

    /**
     * Test with empty articles list
     * Boundary case: Empty articles
     *
     * @author Chen Qian
     */
    @Test
    public void testWithEmptyArticles() {
        List<Article> emptyArticles = List.of();
        List<ReadabilityScores> emptyScores = List.of();
        ReadabilityScores avgScores = new ReadabilityScores(0.0, 0.0);

        SearchBlock block = new SearchBlock(
                "empty query",
                "publishedAt",
                0,
                emptyArticles,
                "2025-01-01T12:00:00Z",
                avgScores,
                emptyScores
        );

        assertTrue(block.articles().isEmpty());
        assertTrue(block.articleReadability().isEmpty());
        assertEquals(0, block.totalResults());
    }

    /**
     * Test with multiple articles
     * Equivalence class: Multiple articles
     *
     * @author Chen Qian
     */
    @Test
    public void testWithMultipleArticles() {
        List<Article> articles = List.of(
                createArticle("Article 1"),
                createArticle("Article 2"),
                createArticle("Article 3")
        );

        List<ReadabilityScores> scores = List.of(
                new ReadabilityScores(7.0, 70.0),
                new ReadabilityScores(8.0, 65.0),
                new ReadabilityScores(9.0, 60.0)
        );

        ReadabilityScores avgScores = new ReadabilityScores(8.0, 65.0);

        SearchBlock block = new SearchBlock(
                "multi query",
                "relevancy",
                3,
                articles,
                "2025-01-01T12:00:00Z",
                avgScores,
                scores
        );

        assertEquals(3, block.articles().size());
        assertEquals(3, block.articleReadability().size());
        assertEquals(3, block.totalResults());
    }

    /**
     * Test with different sort options
     * Equivalence class: Sort options
     *
     * @author Chen Qian
     */
    @Test
    public void testWithDifferentSortOptions() {
        List<Article> articles = List.of(createArticle("Test"));
        List<ReadabilityScores> scores = List.of(new ReadabilityScores(5.0, 75.0));
        ReadabilityScores avgScores = new ReadabilityScores(5.0, 75.0);

        // publishedAt
        SearchBlock block1 = new SearchBlock(
                "query1", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );
        assertEquals("publishedAt", block1.sortBy());

        // relevancy
        SearchBlock block2 = new SearchBlock(
                "query2", "relevancy", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );
        assertEquals("relevancy", block2.sortBy());

        // popularity
        SearchBlock block3 = new SearchBlock(
                "query3", "popularity", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );
        assertEquals("popularity", block3.sortBy());
    }

    /**
     * Test equality with same values
     * Branch: equals returns true
     *
     * @author Chen Qian
     */
    @Test
    public void testEqualitySameValues() {
        List<Article> articles = List.of(createArticle("Test"));
        List<ReadabilityScores> scores = List.of(new ReadabilityScores(5.0, 75.0));
        ReadabilityScores avgScores = new ReadabilityScores(5.0, 75.0);

        SearchBlock block1 = new SearchBlock(
                "query", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        SearchBlock block2 = new SearchBlock(
                "query", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        assertEquals(block1, block2);
        assertEquals(block1.hashCode(), block2.hashCode());
    }

    /**
     * Test inequality with different query
     * Branch: equals returns false
     *
     * @author Chen Qian
     */
    @Test
    public void testInequalityDifferentQuery() {
        List<Article> articles = List.of(createArticle("Test"));
        List<ReadabilityScores> scores = List.of(new ReadabilityScores(5.0, 75.0));
        ReadabilityScores avgScores = new ReadabilityScores(5.0, 75.0);

        SearchBlock block1 = new SearchBlock(
                "query1", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        SearchBlock block2 = new SearchBlock(
                "query2", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        assertNotEquals(block1, block2);
    }

    /**
     * Test inequality with different sortBy
     * Branch: equals returns false
     *
     * @author Chen Qian
     */
    @Test
    public void testInequalityDifferentSortBy() {
        List<Article> articles = List.of(createArticle("Test"));
        List<ReadabilityScores> scores = List.of(new ReadabilityScores(5.0, 75.0));
        ReadabilityScores avgScores = new ReadabilityScores(5.0, 75.0);

        SearchBlock block1 = new SearchBlock(
                "query", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        SearchBlock block2 = new SearchBlock(
                "query", "relevancy", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        assertNotEquals(block1, block2);
    }

    /**
     * Test equality with itself
     * Branch: this == o returns true
     *
     * @author Chen Qian
     */
    @Test
    public void testEqualityWithItself() {
        List<Article> articles = List.of(createArticle("Test"));
        List<ReadabilityScores> scores = List.of(new ReadabilityScores(5.0, 75.0));
        ReadabilityScores avgScores = new ReadabilityScores(5.0, 75.0);

        SearchBlock block = new SearchBlock(
                "query", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        assertEquals(block, block);
    }

    /**
     * Test inequality with null
     * Branch: equals with null
     *
     * @author Chen Qian
     */
    @Test
    public void testInequalityWithNull() {
        List<Article> articles = List.of(createArticle("Test"));
        List<ReadabilityScores> scores = List.of(new ReadabilityScores(5.0, 75.0));
        ReadabilityScores avgScores = new ReadabilityScores(5.0, 75.0);

        SearchBlock block = new SearchBlock(
                "query", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        assertNotEquals(block, null);
    }

    /**
     * Test inequality with different type
     * Branch: equals with different class
     *
     * @author Chen Qian
     */
    @Test
    public void testInequalityWithDifferentType() {
        List<Article> articles = List.of(createArticle("Test"));
        List<ReadabilityScores> scores = List.of(new ReadabilityScores(5.0, 75.0));
        ReadabilityScores avgScores = new ReadabilityScores(5.0, 75.0);

        SearchBlock block = new SearchBlock(
                "query", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        assertNotEquals(block, "not a SearchBlock");
    }

    /**
     * Test hashCode consistency
     * Equivalence class: HashCode behavior
     *
     * @author Chen Qian
     */
    @Test
    public void testHashCodeConsistency() {
        List<Article> articles = List.of(createArticle("Test"));
        List<ReadabilityScores> scores = List.of(new ReadabilityScores(5.0, 75.0));
        ReadabilityScores avgScores = new ReadabilityScores(5.0, 75.0);

        SearchBlock block1 = new SearchBlock(
                "query", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        SearchBlock block2 = new SearchBlock(
                "query", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        SearchBlock block3 = new SearchBlock(
                "different", "publishedAt", 1, articles, "2025-01-01T12:00:00Z", avgScores, scores
        );

        // Equal objects must have equal hash codes
        assertEquals(block1.hashCode(), block2.hashCode());
        // Unequal objects should (usually) have different hash codes
        assertNotEquals(block1.hashCode(), block3.hashCode());
    }

    /**
     * Test toString() includes important fields
     * Branch: toString() execution
     *
     * @author Chen Qian
     */
    @Test
    public void testToString() {
        List<Article> articles = List.of(createArticle("Test Article"));
        List<ReadabilityScores> scores = List.of(new ReadabilityScores(8.5, 65.0));
        ReadabilityScores avgScores = new ReadabilityScores(8.5, 65.0);

        SearchBlock block = new SearchBlock(
                "test query", "publishedAt", 10, articles,
                "2025-01-01T12:00:00Z", avgScores, scores
        );

        String str = block.toString();

        assertNotNull(str);
        // Should contain the record name or key field values
        assertTrue(str.contains("SearchBlock") || str.contains("test query") || str.contains("publishedAt"));
    }

    /**
     * Test immutability of articles list
     * Equivalence class: Immutability
     *
     * @author Chen Qian
     */
    @Test
    public void testArticlesListImmutability() {
        List<Article> originalArticles = new ArrayList<>();
        originalArticles.add(createArticle("Article 1"));

        List<ReadabilityScores> scores = List.of(new ReadabilityScores(5.0, 75.0));
        ReadabilityScores avgScores = new ReadabilityScores(5.0, 75.0);

        SearchBlock block = new SearchBlock(
                "query", "publishedAt", 1, originalArticles,
                "2025-01-01T12:00:00Z", avgScores, scores
        );

        // Modify original list
        originalArticles.add(createArticle("Article 2"));

        // SearchBlock should still have only 1 article (if properly defensively copied)
        // Note: This depends on implementation - records don't auto-copy, but we test the behavior
        assertEquals(1, block.articles().size());
    }

    /**
     * Test with maximum realistic article count (10)
     * Boundary case: Maximum articles
     *
     * @author Chen Qian
     */
    @Test
    public void testWithMaximumArticles() {
        List<Article> articles = new ArrayList<>();
        List<ReadabilityScores> scores = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            articles.add(createArticle("Article " + (i + 1)));
            scores.add(new ReadabilityScores(5.0 + i, 75.0 - i * 5));
        }

        ReadabilityScores avgScores = new ReadabilityScores(9.5, 50.0);

        SearchBlock block = new SearchBlock(
                "max query", "publishedAt", 100, articles,
                "2025-01-01T12:00:00Z", avgScores, scores
        );

        assertEquals(10, block.articles().size());
        assertEquals(10, block.articleReadability().size());
    }

    /**
     * Test with valid ISO timestamp
     * Equivalence class: Timestamp format
     *
     * @author Chen Qian
     */
    @Test
    public void testWithValidIsoTimestamp() {
        List<Article> articles = List.of(createArticle("Test"));
        List<ReadabilityScores> scores = List.of(new ReadabilityScores(5.0, 75.0));
        ReadabilityScores avgScores = new ReadabilityScores(5.0, 75.0);

        String[] validTimestamps = {
                "2025-01-01T12:00:00Z",
                "2025-12-31T23:59:59Z",
                "2025-06-15T15:30:45.123Z",
                "2025-01-01T00:00:00+00:00"
        };

        for (String timestamp : validTimestamps) {
            SearchBlock block = new SearchBlock(
                    "query", "publishedAt", 1, articles, timestamp, avgScores, scores
            );
            assertEquals(timestamp, block.createdAtIso());
        }
    }

    /**
     * Test readability scores integration
     * Equivalence class: Readability fields
     *
     * @author Chen Qian
     */
    @Test
    public void testReadabilityScoresIntegration() {
        List<Article> articles = List.of(
                createArticle("Article 1"),
                createArticle("Article 2")
        );

        ReadabilityScores avgScores = new ReadabilityScores(8.5, 65.0);
        List<ReadabilityScores> individualScores = List.of(
                new ReadabilityScores(7.0, 70.0),
                new ReadabilityScores(10.0, 60.0)
        );

        SearchBlock block = new SearchBlock(
                "query", "publishedAt", 2, articles,
                "2025-01-01T12:00:00Z", avgScores, individualScores
        );

        // Verify average scores
        assertNotNull(block.readability());
        assertEquals(8.5, block.readability().gradeLevel(), 0.01);
        assertEquals(65.0, block.readability().readingEase(), 0.01);

        // Verify individual scores
        assertNotNull(block.articleReadability());
        assertEquals(2, block.articleReadability().size());
        assertEquals(7.0, block.articleReadability().get(0).gradeLevel(), 0.01);
        assertEquals(10.0, block.articleReadability().get(1).gradeLevel(), 0.01);
    }

    /**
     * Test with zero total results
     * Boundary case: No results
     *
     * @author Chen Qian
     */
    @Test
    public void testWithZeroTotalResults() {
        List<Article> emptyArticles = List.of();
        List<ReadabilityScores> emptyScores = List.of();
        ReadabilityScores avgScores = new ReadabilityScores(0.0, 0.0);

        SearchBlock block = new SearchBlock(
                "no results", "publishedAt", 0, emptyArticles,
                "2025-01-01T12:00:00Z", avgScores, emptyScores
        );

        assertEquals(0, block.totalResults());
        assertTrue(block.articles().isEmpty());
        assertTrue(block.articleReadability().isEmpty());
    }
}