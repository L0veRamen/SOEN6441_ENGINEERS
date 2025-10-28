package models;
/*
 * Sentiment.java
 *
 * Defines an enumeration for news sentiment analysis, including POSITIVE, NEGATIVE, and NEUTRAL.
 * Provides a static method fromScores to determine sentiment based on happiness and sadness scores,
 * and a getDescription method to return a symbolic representation of the sentiment.
 *
 * @author Ruochen Qiao
 */

public enum Sentiment {
    POSITIVE,
    NEGATIVE,
    NEUTRAL;

    public static Sentiment fromScores(double happiness, double sadness) {
        if (happiness > 0.6) {
            return POSITIVE;
        } else if (sadness > 0.6) {
            return NEGATIVE;
        } else {
            return NEUTRAL;
        }
    }

    public String getDescription() {
        return switch (this) {
            case POSITIVE -> "😊";
            case NEGATIVE -> "😢";
            case NEUTRAL -> "😐";
        };
    }
}
