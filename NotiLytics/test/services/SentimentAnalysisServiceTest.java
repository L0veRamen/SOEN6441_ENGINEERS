package services;

import models.Article;
import models.Sentiment;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class SentimentAnalysisServiceTest {

    private SentimentAnalysisService sentimentAnalysisService;

    @Before
    public void setUp() {
        sentimentAnalysisService = new SentimentAnalysisService();
    }

    // Word List Analysis Tests
    @Test
    public void testAnalyzeWordListWithStrongPositive() {
        List<String> words = Arrays.asList("happy", "excellent", "wonderful", "amazing", "fantastic");
        assertEquals(Sentiment.POSITIVE, SentimentAnalysisService.analyzeWordList(words));
    }

    @Test
    public void testAnalyzeWordListWithStrongNegative() {
        List<String> words = Arrays.asList("terrible", "horrible", "awful", "disaster", "crisis");
        assertEquals(Sentiment.NEGATIVE, SentimentAnalysisService.analyzeWordList(words));
    }

    @Test
    public void testAnalyzeWordListWithMixedSentimentMoreNegative() {
        List<String> words = Arrays.asList("happy", "terrible", "horrible", "awful", "disaster");
        assertEquals(Sentiment.NEGATIVE, SentimentAnalysisService.analyzeWordList(words));
    }

    @Test
    public void testAnalyzeWordListWithEqualSentiment() {
        List<String> words = Arrays.asList("happy", "good", "sad", "bad");
        assertEquals(Sentiment.NEUTRAL, SentimentAnalysisService.analyzeWordList(words));
    }

    @Test
    public void testAnalyzeWordListWithNeutralWords() {
        List<String> words = Arrays.asList("the", "quick", "brown", "fox", "jumps");
        assertEquals(Sentiment.NEUTRAL, SentimentAnalysisService.analyzeWordList(words));
    }

    @Test
    public void testAnalyzeWordListWithEmptyList() {
        assertEquals(Sentiment.NEUTRAL, SentimentAnalysisService.analyzeWordList(Collections.emptyList()));
    }

    @Test
    public void testAnalyzeWordListWithNull() {
        assertEquals(Sentiment.NEUTRAL, SentimentAnalysisService.analyzeWordList(null));
    }

    // Article Analysis Tests
    @Test
    public void testAnalyzeArticlesWithStrongPositiveSentiment() {

        Article article1 = new Article("Test1", "https://example.com/1", "This is wonderful and amazing news with excellent results",
                "source-1", "Source", "2024-01-01T00:00:00Z");
        Article article2 = new Article("Test2", "https://example.com/1", "The project was a fantastic success with great achievements",
                "source-1", "Source", "2024-01-01T00:00:00Z");
        List<Article> articles = Arrays.asList(article1, article2);
        assertEquals(Sentiment.POSITIVE, SentimentAnalysisService.analyzeArticles(articles));
    }

    @Test
    public void testAnalyzeArticlesWithStrongNegativeSentiment() {
        Article article1 = new Article("Test1", "https://example.com/1", "This is terrible and horrible news with awful outcome","source-1", "Source", "2024-01-01T00:00:00Z");
        Article article2 = new Article("Test2", "https://example.com/1", "The project was a complete disaster with critical failures", "source-1", "Source", "2024-01-01T00:00:00Z");
        List<Article> articles = Arrays.asList(article1, article2);
        assertEquals(Sentiment.NEGATIVE, SentimentAnalysisService.analyzeArticles(articles));
    }

    @Test
    public void testAnalyzeArticlesWithMixedSentiment() {
        Article article1 = new Article("Test1", "url", "This is wonderful and amazing news", "author", "source", "date");
        Article article2 = new Article("Test2", "url", "This is terrible and horrible news", "author", "source", "date");
        List<Article> articles = Arrays.asList(article1, article2);
        assertEquals(Sentiment.NEUTRAL, SentimentAnalysisService.analyzeArticles(articles));
    }

    @Test
    public void testAnalyzeArticlesWithNeutralContent() {
        Article article = new Article("Test", "url", "This is a regular news article about general topics", "author", "source", "date");
        List<Article> articles = Collections.singletonList(article);
        assertEquals(Sentiment.NEUTRAL, SentimentAnalysisService.analyzeArticles(articles));
    }

    @Test
    public void testAnalyzeArticlesWithEmptyContent() {
        Article article = new Article("Test", "url", "", "author", "source", "date");
        List<Article> articles = Collections.singletonList(article);
        assertEquals(Sentiment.NEUTRAL, SentimentAnalysisService.analyzeArticles(articles));
    }

    @Test
    public void testAnalyzeArticlesWithNullContent() {
        Article article = new Article("Test", "url", null, "author", "source", "date");
        List<Article> articles = Collections.singletonList(article);
        assertEquals(Sentiment.NEUTRAL, SentimentAnalysisService.analyzeArticles(articles));
    }

    @Test
    public void testAnalyzeArticlesWithEmptyList() {
        assertEquals(Sentiment.NEUTRAL, SentimentAnalysisService.analyzeArticles(Collections.emptyList()));
    }

    @Test
    public void testAnalyzeArticlesWithNull() {
        assertEquals(Sentiment.NEUTRAL, SentimentAnalysisService.analyzeArticles(null));
    }

    @Test
    public void testAnalyzeArticlesWithMultipleNullDescriptions() {
        List<Article> articles = Arrays.asList(
            new Article("Test1", "url", null, "author", "source", "date"),
            new Article("Test2", "url", null, "author", "source", "date")
        );
        assertEquals(Sentiment.NEUTRAL, SentimentAnalysisService.analyzeArticles(articles));
    }

    @Test
    public void testAnalyzeArticlesWithMixedNullAndContent() {
        List<Article> articles = Arrays.asList(
            new Article(
                    "Test1",
                    "https://example.com/8",
                    null,
                    "source-8",
                    "Source",
                    "2024-01-01T00:00:00Z"
            ),
            new Article(
                    "Test2",
                    "https://example.com/8",
                    "This is wonderful and amazing",
                    "source-8",
                    "Source",
                    "2024-01-01T00:00:00Z"
            )
        );
        assertEquals(Sentiment.POSITIVE, SentimentAnalysisService.analyzeArticles(articles));
    }

    @Test
    public void testAnalyzeWordListWithSpecialCharacters() {
        List<String> words = Arrays.asList("happy!", "good?", "excellent.", ":-)", "😊");
        assertEquals(Sentiment.POSITIVE, SentimentAnalysisService.analyzeWordList(words));
    }

    @Test
    public void testAnalyzeArticlesWithLongContent() {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longContent.append("This is wonderful and amazing. ");
        }
        Article article = new Article("Test", "url", longContent.toString(), "author", "source", "date");
        List<Article> articles = Collections.singletonList(article);
        assertEquals(Sentiment.POSITIVE, SentimentAnalysisService.analyzeArticles(articles));
    }
}
