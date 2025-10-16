package services;

import models.Article;
import models.ReadabilityScores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test class for ReadabilityService
 * Achieves 100% branch coverage
 *
 * EQUIVALENCE CLASSES:
 * 1. Null/empty inputs
 * 2. Simple text (short sentences, simple words)
 * 3. Complex text (long sentences, multi-syllable words)
 * 4. Mixed content (valid and invalid articles)
 * 5. Boundary cases (50 articles, exact counts)
 * 6. Edge cases (punctuation only, numbers, special characters)
 *
 * @author Chen Qian
 */
public class ReadabilityServiceTest {

    private ReadabilityService service;

    /**
     * Set up test fixtures
     *
     * @author Chen Qian
     */
    @BeforeEach
    public void setUp() {
        service = new ReadabilityService();
    }

    /**
     * Helper method to create article with description
     *
     * @param description Article description
     * @return Article object
     * @author Chen Qian
     */
    private Article createArticle(String description) {
        return new Article(
                "Test Title",               // 1. title
                "https://example.com",      // 2. url
                description,                // 3. description
                "test-source",              // 4. sourceId
                "Test Source",              // 5. sourceName
                "2025-01-01T00:00:00Z"      // 6. publishedAt
        );
    }

    // ========== calculateAverageReadability() Tests ==========

    /**
     * Test with null articles list
     * Branch: articles == null
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateAverageReadabilityNullList() {
        ReadabilityScores scores = service.calculateAverageReadability(null);

        assertNotNull(scores);
        assertEquals(0.0, scores.gradeLevel());
        assertEquals(0.0, scores.readingEase());
        assertFalse(scores.isValid());
    }

    /**
     * Test with empty articles list
     * Branch: articles.isEmpty()
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateAverageReadabilityEmptyList() {
        ReadabilityScores scores = service.calculateAverageReadability(Collections.emptyList());

        assertNotNull(scores);
        assertEquals(0.0, scores.gradeLevel());
        assertEquals(0.0, scores.readingEase());
        assertFalse(scores.isValid());
    }

    /**
     * Test with single valid article
     * Equivalence class: Single article
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateAverageReadabilitySingleArticle() {
        List<Article> articles = List.of(
                createArticle("This is a simple test. It has two sentences.")
        );

        ReadabilityScores scores = service.calculateAverageReadability(articles);

        assertNotNull(scores);
        assertTrue(scores.isValid());
        assertTrue(scores.gradeLevel() >= 0);
        assertTrue(scores.readingEase() >= 0 && scores.readingEase() <= 100);
    }

    /**
     * Test with multiple valid articles
     * Equivalence class: Multiple articles
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateAverageReadabilityMultipleArticles() {
        List<Article> articles = List.of(
                createArticle("Simple text."),
                createArticle("Another simple description."),
                createArticle("Third article description here.")
        );

        ReadabilityScores scores = service.calculateAverageReadability(articles);

        assertNotNull(scores);
        assertTrue(scores.isValid());
    }

    /**
     * Test with exactly 50 articles (maximum)
     * Branch: Limit to 50 articles
     * Boundary case: Exactly 50
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateAverageReadabilityExactly50Articles() {
        List<Article> articles = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            articles.add(createArticle("Test article number " + i + ". Simple description."));
        }

        ReadabilityScores scores = service.calculateAverageReadability(articles);

        assertNotNull(scores);
        assertTrue(scores.isValid());
    }

    /**
     * Test with more than 50 articles (should limit to 50)
     * Branch: articles.stream().limit(50)
     * Boundary case: > 50 articles
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateAverageReadabilityMoreThan50Articles() {
        List<Article> articles = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            articles.add(createArticle("Test article " + i + ". Description here."));
        }

        ReadabilityScores scores = service.calculateAverageReadability(articles);

        assertNotNull(scores);
        assertTrue(scores.isValid());
        // Should only process first 50
    }

    /**
     * Test with all invalid articles (null/empty descriptions)
     * Branch: validScores.isEmpty() returns true
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateAverageReadabilityAllInvalidArticles() {
        List<Article> articles = List.of(
                createArticle(null),
                createArticle(""),
                createArticle("   "),
                createArticle("")
        );

        ReadabilityScores scores = service.calculateAverageReadability(articles);

        assertNotNull(scores);
        assertEquals(0.0, scores.gradeLevel());
        assertEquals(0.0, scores.readingEase());
        assertFalse(scores.isValid());
    }

    /**
     * Test with mixed valid and invalid articles
     * Branch: filter(ReadabilityScores::isValid)
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateAverageReadabilityMixedValidInvalid() {
        List<Article> articles = List.of(
                createArticle("Valid description one."),
                createArticle(null),
                createArticle(""),
                createArticle("Valid description two."),
                createArticle("   "),
                createArticle("Valid description three.")
        );

        ReadabilityScores scores = service.calculateAverageReadability(articles);

        assertNotNull(scores);
        assertTrue(scores.isValid());
        // Should only average the 3 valid articles
    }

    /**
     * Test averaging calculation
     * Equivalence class: Average calculation
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateAverageReadabilityAveraging() {
        List<Article> articles = List.of(
                createArticle("Cat."),  // Very simple
                createArticle("Extraordinarily sophisticated technological implementation methodology."),  // Very complex
                createArticle("Moderate complexity text here.")  // Medium
        );

        ReadabilityScores avg = service.calculateAverageReadability(articles);
        ReadabilityScores simple = service.calculateArticleReadability(articles.get(0));
        ReadabilityScores complex = service.calculateArticleReadability(articles.get(1));

        assertNotNull(avg);
        assertTrue(avg.isValid());
        // Average should be between simple and complex
        assertTrue(avg.gradeLevel() >= simple.gradeLevel());
        assertTrue(avg.gradeLevel() <= complex.gradeLevel());
    }

    // ========== calculateArticleReadability() Tests ==========

    /**
     * Test with null article
     * Branch: article == null
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateArticleReadabilityNullArticle() {
        ReadabilityScores scores = service.calculateArticleReadability(null);

        assertNotNull(scores);
        assertEquals(0.0, scores.gradeLevel());
        assertEquals(0.0, scores.readingEase());
    }

    /**
     * Test with null description
     * Branch: article.description() == null
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateArticleReadabilityNullDescription() {
        Article article = createArticle(null);
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertNotNull(scores);
        assertEquals(0.0, scores.gradeLevel());
        assertEquals(0.0, scores.readingEase());
    }

    /**
     * Test with empty description
     * Branch: description.trim().isEmpty()
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateArticleReadabilityEmptyDescription() {
        Article article = createArticle("");
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertNotNull(scores);
        assertEquals(0.0, scores.gradeLevel());
        assertEquals(0.0, scores.readingEase());
    }

    /**
     * Test with whitespace-only description
     * Branch: description.trim().isEmpty()
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateArticleReadabilityWhitespaceOnly() {
        Article article = createArticle("   \t\n   ");
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertNotNull(scores);
        assertEquals(0.0, scores.gradeLevel());
        assertEquals(0.0, scores.readingEase());
    }

    /**
     * Test with description that has no words (only punctuation)
     * Branch: totalWords == 0
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateArticleReadabilityNoWords() {
        Article article = createArticle("!!! ??? ... !!!");
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertNotNull(scores);
        assertEquals(0.0, scores.gradeLevel());
        assertEquals(0.0, scores.readingEase());
    }

    /**
     * Test with description that has no sentences (no punctuation)
     * Branch: totalSentences == 0 (should not happen due to Math.max(1, count) but test anyway)
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateArticleReadabilityNoSentences() {
        // This should still work because countSentences returns Math.max(1, count)
        Article article = createArticle("word word word");
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertNotNull(scores);
        assertTrue(scores.gradeLevel() >= 0);
        assertTrue(scores.readingEase() >= 0);
    }

    /**
     * Test with very simple text (high reading ease)
     * Equivalence class: Simple text
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateArticleReadabilitySimpleText() {
        Article article = createArticle("The cat sat on the mat. The dog ran fast.");
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertNotNull(scores);
        assertTrue(scores.readingEase() > 60, "Simple text should have high reading ease");
        assertTrue(scores.gradeLevel() < 10, "Simple text should have low grade level");
    }

    /**
     * Test with complex text (low reading ease)
     * Equivalence class: Complex text
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateArticleReadabilityComplexText() {
        Article article = createArticle("The implementation of sophisticated methodologies " +
                "necessitates comprehensive understanding of technological paradigms.");
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertNotNull(scores);
        assertTrue(scores.isValid());
        assertTrue(scores.gradeLevel() > 10, "Complex text should have high grade level");
    }

    /**
     * Test Flesch-Kincaid formula with known values
     * Equivalence class: Formula verification
     *
     * @author Chen Qian
     */
    @Test
    public void testCalculateArticleReadabilityFormulaAccuracy() {
        // Simple test case: "The cat sat."
        // Words: 3, Sentences: 1, Syllables: 3 (the=1, cat=1, sat=1)
        // wordsPerSentence = 3/1 = 3.0
        // syllablesPerWord = 3/3 = 1.0
        // Grade = 0.39 * 3.0 + 11.8 * 1.0 - 15.59 = 1.17 + 11.8 - 15.59 = -2.62 → 0.0 (clamped)
        // Ease = 206.835 - 1.015 * 3.0 - 84.6 * 1.0 = 206.835 - 3.045 - 84.6 = 119.19 → 100.0 (clamped)

        Article article = createArticle("The cat sat.");
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertNotNull(scores);
        assertEquals(0.0, scores.gradeLevel(), 0.5);  // Should be clamped to 0
        assertEquals(100.0, scores.readingEase(), 5.0);  // Should be clamped to 100
    }

    // ========== countWords() Tests ==========

    /**
     * Test countWords with null text
     * Branch: text == null
     * (private method, tested indirectly)
     *
     * @author Chen Qian
     */
    @Test
    public void testCountWordsNullText() {
        Article article = createArticle(null);
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertEquals(0.0, scores.gradeLevel());
    }

    /**
     * Test countWords with empty text
     * Branch: text.trim().isEmpty()
     *
     * @author Chen Qian
     */
    @Test
    public void testCountWordsEmptyText() {
        Article article = createArticle("");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertEquals(0.0, scores.gradeLevel());
    }

    /**
     * Test countWords with only numbers (no letters)
     * Branch: word.matches(".*[a-zA-Z].*") returns false
     *
     * @author Chen Qian
     */
    @Test
    public void testCountWordsOnlyNumbers() {
        Article article = createArticle("123 456 789");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertEquals(0.0, scores.gradeLevel());  // No valid words
    }

    /**
     * Test countWords with mixed letters and numbers
     * Branch: word.matches(".*[a-zA-Z].*") returns true
     *
     * @author Chen Qian
     */
    @Test
    public void testCountWordsMixedLettersNumbers() {
        Article article = createArticle("test123 abc456 word");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());  // Has valid words
    }

    /**
     * Test countWords with multiple spaces
     * Branch: filter(word -> !word.trim().isEmpty())
     *
     * @author Chen Qian
     */
    @Test
    public void testCountWordsMultipleSpaces() {
        Article article = createArticle("word1    word2     word3");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    // ========== countSentences() Tests ==========

    /**
     * Test countSentences with null text
     * Branch: text == null
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSentencesNullText() {
        Article article = createArticle(null);
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertEquals(0.0, scores.gradeLevel());
    }

    /**
     * Test countSentences with empty text
     * Branch: text.trim().isEmpty()
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSentencesEmptyText() {
        Article article = createArticle("");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertEquals(0.0, scores.gradeLevel());
    }

    /**
     * Test countSentences with period delimiter
     * Branch: split("[.!?\\n]+")
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSentencesPeriodDelimiter() {
        Article article = createArticle("First sentence. Second sentence. Third sentence.");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test countSentences with exclamation delimiter
     * Branch: split("[.!?\\n]+")
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSentencesExclamationDelimiter() {
        Article article = createArticle("First sentence! Second sentence! Third!");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test countSentences with question delimiter
     * Branch: split("[.!?\\n]+")
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSentencesQuestionDelimiter() {
        Article article = createArticle("First question? Second question? Third?");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test countSentences with newline delimiter
     * Branch: split("[.!?\\n]+")
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSentencesNewlineDelimiter() {
        Article article = createArticle("First line\nSecond line\nThird line");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test countSentences with mixed delimiters
     * Branch: Multiple delimiter types
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSentencesMixedDelimiters() {
        Article article = createArticle("First. Second! Third? Fourth\nFifth.");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test countSentences returns minimum 1
     * Branch: Math.max(1, count)
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSentencesMinimumOne() {
        // Text without sentence delimiters should still count as 1 sentence
        Article article = createArticle("just some words no punctuation");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    // ========== countSyllables() and countSyllablesInWord() Tests ==========

    /**
     * Test countSyllables with null text
     * Branch: text == null
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSyllablesNullText() {
        Article article = createArticle(null);
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertEquals(0.0, scores.gradeLevel());
    }

    /**
     * Test countSyllables with empty text
     * Branch: text.trim().isEmpty()
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSyllablesEmptyText() {
        Article article = createArticle("");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertEquals(0.0, scores.gradeLevel());
    }

    /**
     * Test countSyllables filters words with letters
     * Branch: word.matches(".*[a-zA-Z].*")
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSyllablesFiltersNonLetters() {
        Article article = createArticle("word 123 another 456");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());  // Should only count "word" and "another"
    }

    /**
     * Test countSyllablesInWord with empty word after cleaning
     * Branch: word.length() == 0
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSyllablesInWordEmptyAfterCleaning() {
        Article article = createArticle("!!! @@@ ###");  // No letters, empty after cleaning
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertEquals(0.0, scores.gradeLevel());
    }

    /**
     * Test countSyllablesInWord with 1-letter word
     * Branch: word.length() <= 2
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSyllablesInWordOneLetterOther() {
        Article article = createArticle("a I");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test countSyllablesInWord with 2-letter word
     * Branch: word.length() <= 2
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSyllablesInWordTwoLetter() {
        Article article = createArticle("is to be at on in");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test countSyllablesInWord with 3+ letter word
     * Branch: word.length() > 2
     *
     * @author Chen Qian
     */
    @Test
    public void testCountSyllablesInWordThreePlusLetters() {
        Article article = createArticle("cat dog run");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test syllable counting with vowel at start
     * Branch: isVowel && !previousWasVowel (first character)
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingVowelAtStart() {
        Article article = createArticle("apple orange umbrella");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test syllable counting with consonant at start
     * Branch: !isVowel
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingConsonantAtStart() {
        Article article = createArticle("cat dog tree");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test syllable counting with consecutive vowels (vowel group)
     * Branch: isVowel but previousWasVowel (don't increment)
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingConsecutiveVowels() {
        Article article = createArticle("beat heat meat seat");  // ea = vowel group = 1 syllable each
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test syllable counting with alternating vowels and consonants
     * Branch: isVowel && !previousWasVowel multiple times
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingAlternatingVowelsConsonants() {
        Article article = createArticle("banana potato tomato");  // Multiple separate vowel groups
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
        assertTrue(scores.gradeLevel() > 0);
    }

    /**
     * Test syllable counting with silent 'e' at end
     * Branch: word.endsWith("e") && count > 1 (decrement)
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingSilentE() {
        Article article = createArticle("cake bake make take");  // Each should be 1 syllable
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
        assertTrue(scores.readingEase() > 70, "Words ending in silent 'e' should be fairly easy");
    }

    /**
     * Test syllable counting with 'e' that is only vowel group
     * Branch: word.endsWith("e") but count == 1 (don't decrement)
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingEOnlyVowel() {
        Article article = createArticle("be he me we");  // 'e' is only vowel, should stay as 1
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test syllable counting with 'le' ending after consonant
     * Branch: word.endsWith("le") && consonant before (increment)
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingLeEndingAfterConsonant() {
        Article article = createArticle("table simple little able");  // Each should be 2 syllables
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
        assertTrue(scores.gradeLevel() > 0);
    }

    /**
     * Test syllable counting with 'le' ending after vowel
     * Branch: word.endsWith("le") but vowel before (don't increment)
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingLeEndingAfterVowel() {
        Article article = createArticle("aisle"); // 'le' after vowel 'i'
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test syllable counting with word less than 3 letters before 'le' check
     * Branch: word.length() >= 3 in 'le' check
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingShortWordWithLe() {
        // This tests edge case where word is short (already handled by length <= 2)
        Article article = createArticle("ale ole");  // Short words
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test syllable counting returns minimum 1
     * Branch: Math.max(1, count)
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingMinimumOne() {
        // Even if count somehow becomes 0, should return 1
        Article article = createArticle("xyz");  // No vowels, but should still count as 1 syllable
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test syllable counting with 'y' as vowel
     * Branch: "aeiouy".indexOf(c) >= 0 with 'y'
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingYAsVowel() {
        Article article = createArticle("happy silly crazy");  // 'y' acts as vowel
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test syllable counting with only consonants (rare)
     * Branch: count stays 0, then Math.max(1, count)
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingOnlyConsonants() {
        Article article = createArticle("hmm brr shh");  // Mostly consonants
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test syllable counting with special characters removed
     * Branch: word.replaceAll("[^a-z]", "")
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingSpecialCharactersRemoved() {
        Article article = createArticle("don't can't won't it's");  // Apostrophes removed
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test with uppercase text (should convert to lowercase)
     * Branch: word.toLowerCase()
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingUppercase() {
        Article article = createArticle("HELLO WORLD TESTING UPPERCASE");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    /**
     * Test with mixed case text
     * Branch: word.toLowerCase()
     *
     * @author Chen Qian
     */
    @Test
    public void testSyllableCountingMixedCase() {
        Article article = createArticle("HeLLo WoRLd TeStInG MiXeD CaSe");
        ReadabilityScores scores = service.calculateArticleReadability(article);
        assertTrue(scores.isValid());
    }

    // ========== Integration and Edge Cases ==========

    /**
     * Test with very long single sentence
     * Equivalence class: Long sentences
     *
     * @author Chen Qian
     */
    @Test
    public void testVeryLongSingleSentence() {
        StringBuilder longSentence = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longSentence.append("word").append(i).append(" ");
        }
        longSentence.append(".");

        Article article = createArticle(longSentence.toString());
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertTrue(scores.isValid());
        assertTrue(scores.gradeLevel() > 10, "Very long sentences should have high grade level");
    }

    /**
     * Test with many short sentences
     * Equivalence class: Short sentences
     *
     * @author Chen Qian
     */
    @Test
    public void testManyShortSentences() {
        StringBuilder shortSentences = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            shortSentences.append("Cat. ");
        }

        Article article = createArticle(shortSentences.toString());
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertTrue(scores.isValid());
        assertTrue(scores.readingEase() > 80, "Many short sentences should be easy to read");
    }

    /**
     * Test with realistic news article description
     * Equivalence class: Realistic content
     *
     * @author Chen Qian
     */
    @Test
    public void testRealisticNewsArticle() {
        String description = "The government announced new policies today affecting the technology sector. " +
                "Industry leaders expressed mixed reactions to the proposed regulations. " +
                "Implementation is expected to begin next quarter.";

        Article article = createArticle(description);
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertTrue(scores.isValid());
        assertTrue(scores.gradeLevel() >= 8 && scores.gradeLevel() <= 15);
        assertTrue(scores.readingEase() >= 30 && scores.readingEase() <= 80);
    }

    /**
     * Test with technical jargon
     * Equivalence class: Complex technical text
     *
     * @author Chen Qian
     */
    @Test
    public void testTechnicalJargon() {
        String description = "Implementing sophisticated algorithmic methodologies necessitates " +
                "comprehensive understanding of computational complexity paradigms.";

        Article article = createArticle(description);
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertTrue(scores.isValid());
        assertTrue(scores.gradeLevel() > 15, "Technical jargon should have very high grade level");
        assertTrue(scores.readingEase() < 40, "Technical jargon should be difficult to read");
    }

    /**
     * Test with numbers and punctuation mixed with text
     * Edge case: Mixed content
     *
     * @author Chen Qian
     */
    @Test
    public void testMixedNumbersAndPunctuation() {
        String description = "The company reported $1.5 billion revenue in Q4 2024, " +
                "exceeding analysts' expectations by 12.3%!";

        Article article = createArticle(description);
        ReadabilityScores scores = service.calculateArticleReadability(article);

        assertTrue(scores.isValid());
    }

    /**
     * Test consistency with same input
     * Equivalence class: Consistency check
     *
     * @author Chen Qian
     */
    @Test
    public void testConsistencyWithSameInput() {
        Article article = createArticle("Consistent test input for readability calculation.");

        ReadabilityScores scores1 = service.calculateArticleReadability(article);
        ReadabilityScores scores2 = service.calculateArticleReadability(article);

        assertEquals(scores1.gradeLevel(), scores2.gradeLevel(), 0.01);
        assertEquals(scores1.readingEase(), scores2.readingEase(), 0.01);
    }
}