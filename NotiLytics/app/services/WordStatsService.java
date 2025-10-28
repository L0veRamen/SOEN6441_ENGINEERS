package services;

// Zi Lun Li

import models.Article;
import models.WordStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for computing word-level statistics from news articles.
 * Analyzes article descriptions to extract word frequencies using Java Streams API.
 * 
 * Processing steps:
 * 1. Fetch articles from NewsAPI (up to 50 latest articles)
 * 2. Extract words from descriptions using regex pattern
 * 3. Convert words to lowercase for case-insensitive counting
 * 4. Count word frequencies across all articles
 * 5. Sort by frequency in descending order
 * 
 * @author Zi Lun Li
 */

@Singleton
public class WordStatsService {
    
    private static final Logger log = LoggerFactory.getLogger(WordStatsService.class);
    
    /**
     * Regular expression pattern for extracting words.
     * Matches sequences of 2 or more alphabetic characters.
     * 
     * Pattern consist of
	 * - Only alphabetic characters (a-z, A-Z)
	 * - Minimum 2 characters long
	 * - Case-insensitive (converted to lowercase)
     */
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z]{2,}");
    
    /**
     * NewsAPI client for fetching articles.
     */
    private final NewsApiClient newsApiClient;
    
    /**
     * Constructor with dependency injection.
     * 
     * @param newsApiClient API client for fetching news articles
     * @author Zi Lun Li
     */
    @Inject
    public WordStatsService(NewsApiClient newsApiClient) {
        this.newsApiClient = newsApiClient;
    }
    
    /**
     * Reactive Programming Method that computes word statistics for a search query.
     * Fetches up to 50 latest articles and analyzes word frequencies in descriptions.
     * Uses Java 8+ Streams API for processing.
     * 
     * Processing pipeline:
     * 1. Fetch articles from NewsAPI sorted by publishedAt
     * 2. Extract descriptions from articles
     * 3. Filter out null/blank descriptions
     * 4. Extract words using regex pattern
     * 5. Convert to lowercase
     * 6. Count frequencies using groupingBy collector
     * 7. Sort by frequency in descending order
     * 
     * @param query Search query string
     * @return CompletionStage with WordStats containing frequency analysis
     * @author Zi Lun Li
     */
    public CompletionStage<WordStats> computeWordStats(String query) {
        return newsApiClient.searchEverything(query, "publishedAt", 50)
            .thenApply(response -> {
                List<Article> articles = response.articles();
                
                if (articles.isEmpty()) {
                    log.warn("No articles found for query: '{}'", query);
                    return new WordStats(query, 0, 0, 0, List.of());
                }
                
                Map<String, Long> wordFrequencies = articles.stream()
                    .map(Article::description)
                    .filter(desc -> desc != null && !desc.isBlank())
                    .flatMap(desc -> extractWords(desc).stream())
                    .map(String::toLowerCase)
                    .collect(Collectors.groupingBy(word -> word, Collectors.counting()));
                
                List<WordStats.WordFrequency> sortedFrequencies = wordFrequencies.entrySet()
                    .stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(entry -> new WordStats.WordFrequency(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
                
                long totalWords = wordFrequencies.values().stream()
                    .mapToLong(Long::longValue)
                    .sum();
                
                WordStats stats = new WordStats(
                    query,
                    articles.size(),
                    (int) totalWords,
                    wordFrequencies.size(),
                    sortedFrequencies
                );
                
                log.info("Word stats computed: {} articles, {} unique words, {} total words",
                    stats.totalArticles(), stats.uniqueWords(), stats.totalWords());
                
                return stats;
            })
            .exceptionally(throwable -> {
                log.error("Failed to compute word stats for query '{}': {}", 
                    query, throwable.getMessage(), throwable);
                return new WordStats(query, 0, 0, 0, List.of());
            });
    }
    
    /**
     * Extract words from text using regex pattern.
     * Only extracts alphabetic words with 2 or more characters.
     * 
     * @param text Text to extract words from
     * @return List of extracted words
     * @author Zi Lun Li
     */
    private List<String> extractWords(String text) {
        return WORD_PATTERN.matcher(text)
            .results()
            .map(match -> match.group())
            .collect(Collectors.toList());
    }
}
