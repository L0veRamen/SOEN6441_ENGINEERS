package models;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for WordStats model.
 * Tests the immutable record and its nested WordFrequency record.
 * 
 * @author Zi Lun Li
 */
public class WordStatsTest {

	/**
     * Test WordStats record creation and field access.
     * Verifies all fields are correctly stored and retrieved.
     * 
     * @author Zi Lun Li
     */
    @Test
    public void wordStatsRecordTest() {
        List<WordStats.WordFrequency> frequencies = List.of(
                new WordStats.WordFrequency("the", 10),
                new WordStats.WordFrequency("is", 7),
                new WordStats.WordFrequency("a", 7)
        );

        WordStats wordStats = new WordStats("test", 10, 100, 20, frequencies);

        assertEquals("test", wordStats.query());
        assertEquals(10, wordStats.totalArticles());
        assertEquals(100, wordStats.totalWords());
        assertEquals(20, wordStats.uniqueWords());
        assertEquals(3, wordStats.wordFrequencies().size());
    }

    /**
     * Test WordFrequency record creation and field access.
     * Verifies word and count are correctly stored.
     * 
     * @author Zi Lun Li
     */
    @Test
    public void wordFrequencyRecordTest() {
        WordStats.WordFrequency frequency = new WordStats.WordFrequency("test", 2);

        assertEquals("test", frequency.word());
        assertEquals(2, frequency.count());
    }

    /**
     * Test WordStats with empty word frequencies list.
     * Verifies handling of zero statistics.
     * 
     * @author Zi Lun Li
     */
    @Test
    public void wordStatsEmptyFrequencyTest() {
        WordStats stats = new WordStats("test", 0, 0, 0, List.of());

        assertEquals("test", stats.query());
        assertTrue(stats.wordFrequencies().isEmpty());
    }
}