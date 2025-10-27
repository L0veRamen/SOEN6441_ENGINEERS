package models;

import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Comprehensive test class for Sentiment class.
 * Achieves 100% branch coverage.
 *
 * @author Ruochen Qiao
 */

public class SentimentTest {

    @Test
    public void testFromScores() {
        // POSITIVE branch
        Sentiment sentimentPos = Sentiment.fromScores(0.71, 0.1);
        assertEquals(Sentiment.POSITIVE, sentimentPos);
        // NEGATIVE branch
        Sentiment sentimentNeg = Sentiment.fromScores(0.2, 0.8);
        assertEquals(Sentiment.NEGATIVE, sentimentNeg);
        // NEUTRAL branch
        Sentiment sentimentNeu = Sentiment.fromScores(0.12, 0.5);
        assertEquals(Sentiment.NEUTRAL, sentimentNeu);
    }

    @Test
    public void testGetDescription() {
        assertEquals("😊", Sentiment.POSITIVE.getDescription());
        assertEquals("😢", Sentiment.NEGATIVE.getDescription());
        assertEquals("😐", Sentiment.NEUTRAL.getDescription());
    }
}
