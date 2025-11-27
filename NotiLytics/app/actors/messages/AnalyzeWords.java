package actors.messages;


/**
 * Task B: Compute word statistics
 * Sent to WordStatsActor
 *
 * @param query Search query to analyze word frequencies for
 * @author Group Members
 */
public record AnalyzeWords(String query) {
    /**
     * Constructor with validation
     */
    public AnalyzeWords {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be null or blank");
        }
    }
}
