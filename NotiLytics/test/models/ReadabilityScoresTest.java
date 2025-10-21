package models;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test class for ReadabilityScores record
 *
 * @author Chen Qian
 */
public class ReadabilityScoresTest {
    private static final double DELTA = 1e-6;

    /**
     * Test basic constructor and accessors
     *
     * @author Chen Qian
     */
    @Test
    public void testConstructorAndAccessors() {
        ReadabilityScores scores = new ReadabilityScores(8.5, 65.3);

        assertEquals(8.5, scores.gradeLevel(), 0.01);
        assertEquals(65.3, scores.readingEase(), 0.01);
    }

    /**
     * Test rounding to 1 decimal place
     *
     * @author Chen Qian
     */
    @Test
    public void testRounding() {
        ReadabilityScores scores = new ReadabilityScores(8.567, 65.789);

        assertEquals(8.6, scores.gradeLevel(), 0.01);
        assertEquals(65.8, scores.readingEase(), 0.01);
    }

    /**
     * Test clamping of values
     *
     * @author Chen Qian
     */
    @Test
    public void testClamping() {
        // Negative values should be clamped to 0
        ReadabilityScores negative = new ReadabilityScores(-5.0, -10.0);
        assertEquals(0.0, negative.gradeLevel(), DELTA);
        assertEquals(0.0, negative.readingEase(), DELTA);

        // Reading ease above 100 should be clamped
        ReadabilityScores high = new ReadabilityScores(0.5, 150.0);
        assertEquals(100.0, high.readingEase(), DELTA);

        // Grade level has no upper bound
        ReadabilityScores highGrade = new ReadabilityScores(20.0, 50.0);
        assertEquals(20.0, highGrade.gradeLevel(), DELTA);
    }

    /**
     * Test interpretation method
     *
     * @author Chen Qian
     */
    @Test
    public void testInterpretation() {
        assertEquals("Very Easy", new ReadabilityScores(1.0, 95.0).getReadingEaseInterpretation());
        assertEquals("Easy", new ReadabilityScores(3.0, 85.0).getReadingEaseInterpretation());
        assertEquals("Fairly Easy", new ReadabilityScores(5.0, 75.0).getReadingEaseInterpretation());
        assertEquals("Standard", new ReadabilityScores(8.0, 65.0).getReadingEaseInterpretation());
        assertEquals("Fairly Difficult", new ReadabilityScores(11.0, 55.0).getReadingEaseInterpretation());
        assertEquals("Difficult", new ReadabilityScores(14.0, 35.0).getReadingEaseInterpretation());
        assertEquals("Very Difficult", new ReadabilityScores(17.0, 15.0).getReadingEaseInterpretation());
    }

    /**
     * Test isValid method
     *
     * @author Chen Qian
     */
    @Test
    public void testIsValid() {
        assertTrue(new ReadabilityScores(5.0, 70.0).isValid());
        assertFalse(new ReadabilityScores(0.0, 0.0).isValid());
    }

    /**
     * Test equality
     *
     * @author Chen Qian
     */
    @Test
    public void testEquality() {
        ReadabilityScores scores1 = new ReadabilityScores(8.5, 65.0);
        ReadabilityScores scores2 = new ReadabilityScores(8.5, 65.0);
        ReadabilityScores scores3 = new ReadabilityScores(9.0, 65.0);

        assertEquals(scores1, scores2);
        assertNotEquals(scores1, scores3);
    }
}
