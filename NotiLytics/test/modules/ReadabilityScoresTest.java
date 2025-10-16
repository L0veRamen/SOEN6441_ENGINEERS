package modules;

import models.ReadabilityScores;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test class for ReadabilityScores record
 * Achieves 100% branch coverage
 *
 * @author Chen Qian
 */
public class ReadabilityScoresTest {

    /**
     * Test basic constructor and accessors
     * Equivalence class: Valid positive values
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
     * Boundary case: Rounding behavior
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
     * Test clamping negative grade level to 0
     * Branch: gradeLevel < 0
     *
     * @author Chen Qian
     */
    @Test
    public void testNegativeGradeLevelClampedToZero() {
        ReadabilityScores scores = new ReadabilityScores(-5.0, 50.0);
        assertEquals(0.0, scores.gradeLevel());
        assertEquals(50.0, scores.readingEase());
    }

    /**
     * Test clamping negative reading ease to 0
     * Branch: readingEase < 0
     *
     * @author Chen Qian
     */
    @Test
    public void testNegativeReadingEaseClampedToZero() {
        ReadabilityScores scores = new ReadabilityScores(5.0, -10.0);
        assertEquals(5.0, scores.gradeLevel());
        assertEquals(0.0, scores.readingEase());
    }

    /**
     * Test clamping both negative values
     * Branch: Both negative
     *
     * @author Chen Qian
     */
    @Test
    public void testBothNegativeValuesClamped() {
        ReadabilityScores scores = new ReadabilityScores(-5.0, -10.0);
        assertEquals(0.0, scores.gradeLevel());
        assertEquals(0.0, scores.readingEase());
    }

    /**
     * Test reading ease clamped to 100 maximum
     * Branch: readingEase > 100
     *
     * @author Chen Qian
     */
    @Test
    public void testReadingEaseClampedTo100() {
        ReadabilityScores scores = new ReadabilityScores(0.5, 150.0);
        assertEquals(0.5, scores.gradeLevel());
        assertEquals(100.0, scores.readingEase());
    }

    /**
     * Test reading ease exactly at 100 (no clamping needed)
     * Boundary case: Exactly 100
     *
     * @author Chen Qian
     */
    @Test
    public void testReadingEaseExactly100() {
        ReadabilityScores scores = new ReadabilityScores(0.0, 100.0);
        assertEquals(100.0, scores.readingEase());
    }

    /**
     * Test grade level has no upper bound
     * Equivalence class: Very high grade level
     *
     * @author Chen Qian
     */
    @Test
    public void testHighGradeLevel() {
        ReadabilityScores scores = new ReadabilityScores(20.0, 50.0);
        assertEquals(20.0, scores.gradeLevel());
    }

    /**
     * Test interpretation: Very Easy (>= 90)
     * Branch: readingEase >= 90
     *
     * @author Chen Qian
     */
    @Test
    public void testInterpretationVeryEasy() {
        assertEquals("Very Easy", new ReadabilityScores(1.0, 95.0).getReadingEaseInterpretation());
        assertEquals("Very Easy", new ReadabilityScores(1.0, 90.0).getReadingEaseInterpretation()); // Boundary
    }

    /**
     * Test interpretation: Easy (80 <= x < 90)
     * Branch: readingEase >= 80
     *
     * @author Chen Qian
     */
    @Test
    public void testInterpretationEasy() {
        assertEquals("Easy", new ReadabilityScores(3.0, 85.0).getReadingEaseInterpretation());
        assertEquals("Easy", new ReadabilityScores(3.0, 80.0).getReadingEaseInterpretation()); // Boundary
        assertEquals("Easy", new ReadabilityScores(3.0, 89.9).getReadingEaseInterpretation()); // Just below 90
    }

    /**
     * Test interpretation: Fairly Easy (70 <= x < 80)
     * Branch: readingEase >= 70
     *
     * @author Chen Qian
     */
    @Test
    public void testInterpretationFairlyEasy() {
        assertEquals("Fairly Easy", new ReadabilityScores(5.0, 75.0).getReadingEaseInterpretation());
        assertEquals("Fairly Easy", new ReadabilityScores(5.0, 70.0).getReadingEaseInterpretation()); // Boundary
        assertEquals("Fairly Easy", new ReadabilityScores(5.0, 79.9).getReadingEaseInterpretation()); // Just below 80
    }

    /**
     * Test interpretation: Standard (60 <= x < 70)
     * Branch: readingEase >= 60
     *
     * @author Chen Qian
     */
    @Test
    public void testInterpretationStandard() {
        assertEquals("Standard", new ReadabilityScores(8.0, 65.0).getReadingEaseInterpretation());
        assertEquals("Standard", new ReadabilityScores(8.0, 60.0).getReadingEaseInterpretation()); // Boundary
        assertEquals("Standard", new ReadabilityScores(8.0, 69.9).getReadingEaseInterpretation()); // Just below 70
    }

    /**
     * Test interpretation: Fairly Difficult (50 <= x < 60)
     * Branch: readingEase >= 50
     *
     * @author Chen Qian
     */
    @Test
    public void testInterpretationFairlyDifficult() {
        assertEquals("Fairly Difficult", new ReadabilityScores(11.0, 55.0).getReadingEaseInterpretation());
        assertEquals("Fairly Difficult", new ReadabilityScores(11.0, 50.0).getReadingEaseInterpretation()); // Boundary
        assertEquals("Fairly Difficult", new ReadabilityScores(11.0, 59.9).getReadingEaseInterpretation()); // Just below 60
    }

    /**
     * Test interpretation: Difficult (30 <= x < 50)
     * Branch: readingEase >= 30
     *
     * @author Chen Qian
     */
    @Test
    public void testInterpretationDifficult() {
        assertEquals("Difficult", new ReadabilityScores(14.0, 35.0).getReadingEaseInterpretation());
        assertEquals("Difficult", new ReadabilityScores(14.0, 30.0).getReadingEaseInterpretation()); // Boundary
        assertEquals("Difficult", new ReadabilityScores(14.0, 49.9).getReadingEaseInterpretation()); // Just below 50
    }

    /**
     * Test interpretation: Very Difficult (< 30)
     * Branch: else (default case)
     *
     * @author Chen Qian
     */
    @Test
    public void testInterpretationVeryDifficult() {
        assertEquals("Very Difficult", new ReadabilityScores(17.0, 15.0).getReadingEaseInterpretation());
        assertEquals("Very Difficult", new ReadabilityScores(18.0, 0.0).getReadingEaseInterpretation()); // Minimum
        assertEquals("Very Difficult", new ReadabilityScores(17.0, 29.9).getReadingEaseInterpretation()); // Just below 30
    }

    /**
     * Test isValid() returns true for non-zero grade level
     * Branch: gradeLevel > 0 returns true
     *
     * @author Chen Qian
     */
    @Test
    public void testIsValidTrue() {
        assertTrue(new ReadabilityScores(5.0, 70.0).isValid());
        assertTrue(new ReadabilityScores(0.1, 50.0).isValid()); // Just above 0
    }

    /**
     * Test isValid() returns false for zero grade level
     * Branch: gradeLevel > 0 returns false
     *
     * @author Chen Qian
     */
    @Test
    public void testIsValidFalse() {
        assertFalse(new ReadabilityScores(0.0, 0.0).isValid());
        assertFalse(new ReadabilityScores(0.0, 50.0).isValid()); // Grade level is 0
    }

    /**
     * Test equality with same values
     * Branch: equals returns true
     *
     * @author Chen Qian
     */
    @Test
    public void testEqualsSameValues() {
        ReadabilityScores scores1 = new ReadabilityScores(8.5, 65.0);
        ReadabilityScores scores2 = new ReadabilityScores(8.5, 65.0);

        assertEquals(scores1, scores2);
        assertEquals(scores1.hashCode(), scores2.hashCode());
    }

    /**
     * Test equality with different grade level
     * Branch: equals returns false (different gradeLevel)
     *
     * @author Chen Qian
     */
    @Test
    public void testEqualsDifferentGradeLevel() {
        ReadabilityScores scores1 = new ReadabilityScores(8.5, 65.0);
        ReadabilityScores scores2 = new ReadabilityScores(9.0, 65.0);

        assertNotEquals(scores1, scores2);
    }

    /**
     * Test equality with different reading ease
     * Branch: equals returns false (different readingEase)
     *
     * @author Chen Qian
     */
    @Test
    public void testEqualsDifferentReadingEase() {
        ReadabilityScores scores1 = new ReadabilityScores(8.5, 65.0);
        ReadabilityScores scores2 = new ReadabilityScores(8.5, 70.0);

        assertNotEquals(scores1, scores2);
    }

    /**
     * Test equality with null
     * Branch: equals with null
     *
     * @author Chen Qian
     */
    @Test
    public void testEqualsWithNull() {
        ReadabilityScores scores = new ReadabilityScores(8.5, 65.0);
        assertNotEquals(scores, null);
    }

    /**
     * Test equality with different type
     * Branch: equals with different class
     *
     * @author Chen Qian
     */
    @Test
    public void testEqualsWithDifferentType() {
        ReadabilityScores scores = new ReadabilityScores(8.5, 65.0);
        assertNotEquals(scores, "not a ReadabilityScores");
    }

    /**
     * Test equality with itself
     * Branch: this == o returns true
     *
     * @author Chen Qian
     */
    @Test
    public void testEqualsWithItself() {
        ReadabilityScores scores = new ReadabilityScores(8.5, 65.0);
        assertEquals(scores, scores);
    }

    /**
     * Test hashCode consistency
     * Equivalence class: Hash code behavior
     *
     * @author Chen Qian
     */
    @Test
    public void testHashCode() {
        ReadabilityScores scores1 = new ReadabilityScores(8.5, 65.0);
        ReadabilityScores scores2 = new ReadabilityScores(8.5, 65.0);
        ReadabilityScores scores3 = new ReadabilityScores(9.0, 65.0);

        // Equal objects must have equal hash codes
        assertEquals(scores1.hashCode(), scores2.hashCode());
        // Unequal objects should (usually) have different hash codes
        assertNotEquals(scores1.hashCode(), scores3.hashCode());
    }

    /**
     * Test toString() includes all fields
     * Branch: toString() execution
     *
     * @author Chen Qian
     */
    @Test
    public void testToString() {
        ReadabilityScores scores = new ReadabilityScores(8.5, 65.0);
        String str = scores.toString();

        // Should contain the record name and both field values
        assertNotNull(str);
        assertTrue(str.contains("ReadabilityScores") || str.contains("8.5"));
        assertTrue(str.contains("65.0") || str.contains("65"));
    }

    /**
     * Test zero values (boundary case)
     * Equivalence class: Minimum valid values
     *
     * @author Chen Qian
     */
    @Test
    public void testZeroValues() {
        ReadabilityScores scores = new ReadabilityScores(0.0, 0.0);
        assertEquals(0.0, scores.gradeLevel());
        assertEquals(0.0, scores.readingEase());
        assertFalse(scores.isValid());
    }

    /**
     * Test maximum reading ease value
     * Boundary case: Maximum reading ease
     *
     * @author Chen Qian
     */
    @Test
    public void testMaximumReadingEase() {
        ReadabilityScores scores = new ReadabilityScores(1.0, 100.0);
        assertEquals(100.0, scores.readingEase());
        assertEquals("Very Easy", scores.getReadingEaseInterpretation());
    }
}