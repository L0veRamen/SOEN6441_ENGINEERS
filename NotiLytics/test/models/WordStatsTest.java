package models;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class WordStatsTest {

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

    @Test
    public void wordFrequencyRecordTest() {
        WordStats.WordFrequency frequency = new WordStats.WordFrequency("test", 2);

        assertEquals("test", frequency.word());
        assertEquals(2, frequency.count());
    }

    @Test
    public void wordStatsEmptyFrequencyTest() {
        WordStats stats = new WordStats("test", 0, 0, 0, List.of());

        assertEquals("test", stats.query());
        assertTrue(stats.wordFrequencies().isEmpty());
    }
}