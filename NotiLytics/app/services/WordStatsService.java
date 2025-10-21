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
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Singleton
public class WordStatsService {
    
    private static final Logger log = LoggerFactory.getLogger(WordStatsService.class);
    
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z]{2,}");
    
    private final NewsApiClient newsApiClient;
    
    @Inject
    public WordStatsService(NewsApiClient newsApiClient) {
        this.newsApiClient = newsApiClient;
    }
    
    public CompletionStage<WordStats> computeStats(String query) {
        log.info("Computing word stats for query: '{}'", query);
        
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
    
    private List<String> extractWords(String text) {
        return WORD_PATTERN.matcher(text)
            .results()
            .map(match -> match.group())
            .collect(Collectors.toList());
    }
}
