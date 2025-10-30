package models;

import java.util.List;

/**
 * Model representing word frequency statistics for a search query.
 * 
 * @param query Original search query string
 * @param totalArticles Number of articles analyzed
 * @param totalWords Total word count across all articles (including duplicates)
 * @param uniqueWords Number of unique words found
 * @param wordFrequencies List of word-frequency pairs sorted in descending order
 * 
 * @author Zi Lun Li
 */

public record WordStats(
    String query,
    int totalArticles,
    int totalWords,
    int uniqueWords,
    List<WordFrequency> wordFrequencies
) {
    public record WordFrequency(String word, long count) {}
}