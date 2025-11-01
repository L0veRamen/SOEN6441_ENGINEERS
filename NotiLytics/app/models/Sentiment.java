package models;

/**
 * Represents the sentiment classification for news analysis.
 *
 * <p>This enumeration defines three types of sentiments:
 * <ul>
 *     <li>{@link #POSITIVE} — content expresses positive tone.</li>
 *     <li>{@link #NEGATIVE} — content expresses negative tone.</li>
 *     <li>{@link #NEUTRAL} — content expresses neutral or mixed tone.</li>
 * </ul>
 * Includes helper methods to determine sentiment from numeric scores
 * and to retrieve an emoji representation.</p>
 *
 * @author Ruochen Qiao
 * @version 1.0
 * @since 2025-11-01
 */
public enum Sentiment {

    /** Represents a positive sentiment. */
    POSITIVE,

    /** Represents a negative sentiment. */
    NEGATIVE,

    /** Represents a neutral sentiment. */
    NEUTRAL;

    /**
     * Determines the sentiment based on given happiness and sadness scores.
     *
     * @param happiness happiness score (range 0–1)
     * @param sadness sadness score (range 0–1)
     * @return the corresponding {@code Sentiment} value
     */
    public static Sentiment fromScores(double happiness, double sadness) {
        if (happiness > 0.6) {
            return POSITIVE;
        } else if (sadness > 0.6) {
            return NEGATIVE;
        } else {
            return NEUTRAL;
        }
    }

    /**
     * Returns an emoji representing this sentiment.
     *
     * @return an emoji string corresponding to the sentiment
     */
    public String getDescription() {
        return switch (this) {
            case POSITIVE -> "😊";
            case NEGATIVE -> "😢";
            case NEUTRAL -> "😐";
        };
    }
}