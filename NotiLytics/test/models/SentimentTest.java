package models;

import org.junit.Test;

import static org.junit.Assert.*;


public class SentimentTest {

    public void testConstructor() {
        Sentiment sentimentPos = Sentiment.fromScores(0.71, 0.1);
        assertEquals(Sentiment.POSITIVE, sentimentPos);
        Sentiment sentimentNeg = Sentiment.fromScores(0.2, 0.8);
        assertEquals(Sentiment.NEGATIVE, sentimentPos);
        Sentiment sentimentNeu = Sentiment.fromScores(0.12, 0.69);
        assertEquals(Sentiment.NEUTRAL, sentimentPos);
    }
}
