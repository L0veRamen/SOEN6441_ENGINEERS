package services;

import models.Article;
import models.ReadabilityScores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;

/**
 * Service for calculating Flesch-Kincaid readability scores
 * Uses Java 8+ Streams API for processing
 *
 * Formulas:
 * - Flesch-Kincaid Grade Level = 0.39 * (words/sentences) + 11.8 * (syllables/words) - 15.59
 * - Flesch Reading Ease = 206.835 - 1.015 * (words/sentences) - 84.6 * (syllables/words)
 *
 * @author Chen Qian
 */
@Singleton
public class ReadabilityService {
    private static final Logger logger = LoggerFactory.getLogger(ReadabilityService.class);

    /**
     * Constructor for dependency injection
     *
     * @author Chen Qian
     */
    public ReadabilityService() {
        // Package-private for testing
    }

    /**
     * Calculate average readability scores from a list of articles
     * Uses Java 8 Streams API to process articles
     *
     * @param articles List of articles (up to 50)
     * @return Average readability scores, or (0, 0) if no valid articles
     * @author Chen Qian
     */
    public ReadabilityScores calculateAverageReadability(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            logger.debug("No articles provided for readability calculation");
            return new ReadabilityScores(0, 0);
        }

        // Limit to 50 articles as per requirements
        List<Article> limitedArticles = articles.stream()
                .limit(50)
                .toList();

        logger.debug("Calculating readability for {} articles", limitedArticles.size());

        // Calculate individual scores and filter out invalid ones
        List<ReadabilityScores> validScores = limitedArticles.stream()
                .map(this::calculateArticleReadability)
                .filter(ReadabilityScores::isValid)
                .toList();

        if (validScores.isEmpty()) {
            logger.debug("No valid descriptions found for readability calculation");
            return new ReadabilityScores(0, 0);
        }

        // Calculate averages using Streams API
        double avgGradeLevel = validScores.stream()
                .mapToDouble(ReadabilityScores::gradeLevel)
                .average()
                .orElse(0.0);

        double avgReadingEase = validScores.stream()
                .mapToDouble(ReadabilityScores::readingEase)
                .average()
                .orElse(0.0);

        logger.debug("Average readability: Grade={}, Ease={}", avgGradeLevel, avgReadingEase);

        return new ReadabilityScores(avgGradeLevel, avgReadingEase);
    }

    /**
     * Calculate readability scores for a single article's description
     *
     * @param article Article to analyze
     * @return Readability scores for the description
     * @author Chen Qian
     */
    public ReadabilityScores calculateArticleReadability(Article article) {
        if (article == null || article.description() == null || article.description().trim().isEmpty()) {
            return new ReadabilityScores(0, 0);
        }

        String description = article.description();

        int totalWords = countWords(description);
        int totalSentences = countSentences(description);
        int totalSyllables = countSyllables(description);

        if (totalWords == 0 || totalSentences == 0) {
            return new ReadabilityScores(0, 0);
        }

        double wordsPerSentence = (double) totalWords / totalSentences;
        double syllablesPerWord = (double) totalSyllables / totalWords;

        // Flesch-Kincaid Grade Level
        double gradeLevel = 0.39 * wordsPerSentence + 11.8 * syllablesPerWord - 15.59;

        // Flesch Reading Ease
        double readingEase = 206.835 - 1.015 * wordsPerSentence - 84.6 * syllablesPerWord;

        return new ReadabilityScores(gradeLevel, readingEase);
    }

    /**
     * Count words in text (must contain at least one letter)
     *
     * @param text Input text
     * @return Word count
     * @author Chen Qian
     */
    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }

        return (int) Arrays.stream(text.trim().split("\\s+"))
                .filter(word -> !word.trim().isEmpty())
                .filter(word -> word.matches(".*[a-zA-Z].*"))
                .count();
    }

    /**
     * Count sentences in text (delimited by . ! ? or newline)
     *
     * @param text Input text
     * @return Sentence count (minimum 1 if text is non-empty)
     * @author Chen Qian
     */
    private int countSentences(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }

        long count = Arrays.stream(text.split("[.!?\\n]+"))
                .filter(sentence -> !sentence.trim().isEmpty())
                .count();

        return (int) Math.max(1, count);
    }

    /**
     * Count total syllables in text using Streams API
     *
     * @param text Input text
     * @return Total syllable count
     * @author Chen Qian
     */
    private int countSyllables(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }

        return Arrays.stream(text.toLowerCase().split("\\s+"))
                .filter(word -> word.matches(".*[a-zA-Z].*"))
                .mapToInt(this::countSyllablesInWord)
                .sum();
    }

    /**
     * Count syllables in a single word using improved algorithm
     *
     * Algorithm rules:
     * 1. Count vowel groups (consecutive vowels = 1 syllable)
     * 2. Subtract 1 for silent 'e' at end (but not if it's the only vowel group)
     * 3. Add 1 for 'le' at end after consonant (e.g., "table")
     * 4. Minimum 1 syllable per word
     *
     * @param word Input word (will be normalized to lowercase)
     * @return Syllable count (minimum 1)
     * @author Chen Qian
     */
    private int countSyllablesInWord(String word) {
        word = word.toLowerCase().replaceAll("[^a-z]", "");

        if (word.length() == 0) {
            return 0;
        }

        if (word.length() <= 2) {
            return 1;
        }

        // Count vowel groups
        int count = 0;
        boolean previousWasVowel = false;

        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            boolean isVowel = "aeiouy".indexOf(c) >= 0;

            if (isVowel && !previousWasVowel) {
                count++;
            }

            previousWasVowel = isVowel;
        }

        // Adjust for silent 'e' at end (but not if it's the only vowel group)
        if (word.endsWith("e") && count > 1) {
            count--;
        }

        // Adjust for 'le' ending after consonant (e.g., "table", "simple")
        if (word.length() >= 3 && word.endsWith("le") &&
                "aeiouy".indexOf(word.charAt(word.length() - 3)) < 0) {
            count++;
        }

        return Math.max(1, count);
    }
}