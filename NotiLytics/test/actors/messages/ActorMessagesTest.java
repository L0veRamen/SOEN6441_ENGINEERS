package actors.messages;

import models.Article;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ActorMessagesTest {

    private static Article article(int index) {
        return new Article(
                "Title " + index,
                "https://example.com/" + index,
                "desc",
                "src-" + index,
                "Source " + index,
                "2025-01-01T00:00:00Z"
        );
    }

    @Test
    public void analyzeWordsStoresQuery() {
        AnalyzeWords message = new AnalyzeWords("climate change");
        assertEquals("climate change", message.query());
    }

    @Test(expected = IllegalArgumentException.class)
    public void analyzeWordsRejectsBlankQueries() {
        new AnalyzeWords("  ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void analyzeWordsRejectsNullQueries() {
        new AnalyzeWords(null);
    }

    @Test
    public void analyzeSourceStoresName() {
        AnalyzeSource source = new AnalyzeSource("bbc-news");
        assertEquals("bbc-news", source.sourceName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void analyzeSourceRejectsBlank() {
        new AnalyzeSource("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void analyzeSourceRejectsNull() {
        new AnalyzeSource(null);
    }

    @Test
    public void analyzeSentimentMakesDefensiveCopy() {
        List<Article> articles = new ArrayList<>(List.of(article(1)));
        AnalyzeSentiment message = new AnalyzeSentiment(articles);
        articles.clear();
        assertEquals(1, message.articles().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void analyzeSentimentRejectsNullList() {
        new AnalyzeSentiment(null);
    }

    @Test
    public void analyzeReadabilityCopiesArticles() {
        List<Article> articles = new ArrayList<>(List.of(article(1), article(2)));
        AnalyzeReadability message = new AnalyzeReadability(articles);
        articles.clear();
        assertEquals(2, message.articles().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void analyzeReadabilityRejectsNullArticles() {
        new AnalyzeReadability(null);
    }

    @Test
    public void fetchSourcesAllowsNullFilters() {
        FetchSources sources = new FetchSources("ca", null, "fr");
        assertEquals("ca", sources.country());
        assertEquals(null, sources.category());
        assertEquals("fr", sources.language());
    }

    @Test
    public void taskResultFactoryMethodsSetTypes() {
        Object payload = List.of("data");
        assertEquals("sourceProfile", TaskResult.sourceProfile(payload).taskType());
        assertEquals("wordStats", TaskResult.wordStats(payload).taskType());
        assertEquals("sources", TaskResult.sources(payload).taskType());
        assertEquals("sentiment", TaskResult.sentiment(payload).taskType());
        assertEquals("readability", TaskResult.readability(payload).taskType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void taskResultRejectsBlankType() {
        new TaskResult(" ", List.of());
    }

    @Test(expected = IllegalArgumentException.class)
    public void taskResultRejectsNullData() {
        new TaskResult("task", null);
    }

    @Test
    public void taskResultAcceptsValidInput() {
        TaskResult result = new TaskResult("custom", List.of());
        assertEquals("custom", result.taskType());
        assertEquals(List.of(), result.data());
    }

    @Test(expected = IllegalArgumentException.class)
    public void taskResultRejectsNullType() {
        new TaskResult(null, List.of());
    }
}
