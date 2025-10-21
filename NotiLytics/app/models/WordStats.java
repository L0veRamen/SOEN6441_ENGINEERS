package models;

// Zi Lun Li

import java.util.List;

public record WordStats(
    String query,
    int totalArticles,
    int totalWords,
    int uniqueWords,
    List<WordFrequency> wordFrequencies
) {
    public record WordFrequency(String word, long count) {}
}