package models;

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
