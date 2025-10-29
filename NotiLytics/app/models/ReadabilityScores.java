package models;

/**
 * Represents readability scores for article descriptions
 * Uses Flesch-Kincaid Grade Level and Flesch Reading Ease formulas
 *
 * @author Chen Qian
 */
public record ReadabilityScores(
        double gradeLevel,
        double readingEase
) {
    /**
     * Constructor with validation and rounding
     *
     * @param gradeLevel Flesch-Kincaid Grade Level (0-18+)
     * @param readingEase Flesch Reading Ease (0-100)
     * @author Chen Qian
     */
    public ReadabilityScores {
        // Round to 1 decimal place and ensure non-negative
        gradeLevel = Math.max(0, Math.round(gradeLevel * 10.0) / 10.0);
        // Clamp reading ease to 0-100 range
        readingEase = Math.max(0, Math.min(100, Math.round(readingEase * 10.0) / 10.0));
    }

    /**
     * Get interpretation of reading ease score
     *
     * @return Difficulty level description
     * @author Chen Qian
     */
    public String getReadingEaseInterpretation() {
        if (readingEase >= 90) return "Very Easy";
        if (readingEase >= 80) return "Easy";
        if (readingEase >= 70) return "Fairly Easy";
        if (readingEase >= 60) return "Standard";
        if (readingEase >= 50) return "Fairly Difficult";
        if (readingEase >= 30) return "Difficult";
        return "Very Difficult";
    }

    /**
     * Check if scores are valid (not default/error values)
     *
     * @return true if valid, false otherwise
     * @author Chen Qian
     */
    public boolean isValid() {
        return gradeLevel > 0;
    }
}
