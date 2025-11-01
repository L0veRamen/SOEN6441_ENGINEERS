package models;

import java.util.List;

/**
 * Represents word frequency statistics for a search query.
 *
 * <p>Contains aggregate data such as total article count, total and unique
 * word counts, and a list of word-frequency pairs sorted by frequency.</p>
 *
 * @param query the original search query string
 * @param totalArticles number of articles analyzed
 * @param totalWords total word count across all analyzed articles (including duplicates)
 * @param uniqueWords number of unique words found
 * @param wordFrequencies list of word-frequency pairs sorted in descending order
 *
 * @author Zi Lun Li
 * @version 1.0
 * @since 2025-11-01
 */
public record WordStats(
        String query,
        int totalArticles,
        int totalWords,
        int uniqueWords,
        List<WordFrequency> wordFrequencies
) {

    /**
     * Represents a single word and its occurrence count.
     *
     * @param word the word itself
     * @param count the number of times the word appears
     */
    public record WordFrequency(String word, long count) {}
}