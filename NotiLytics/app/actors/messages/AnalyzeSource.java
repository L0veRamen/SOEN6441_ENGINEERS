package actors.messages;


/**
 * Immutable message classes for actor communication
 * All messages are Java records for type safety and immutability
 *
 * Message Flow:
 * - Client → UserActor: WebSocket JSON (handled separately)
 * - UserActor → Child Actors: Task messages (below)
 * - Child Actors → UserActor: TaskResult
 * - UserActor → Self: UpdateCheck
 *
 * @author Group Members
 */

// ========== TASK MESSAGES (UserActor → Child Actors) ==========

/**
 * Task A: Analyze source profile
 * Sent to SourceProfileActor
 *
 * @param sourceName Name or ID of news source to analyze
 * @author Group Members
 */
public record AnalyzeSource(String sourceName) {
    /**
     * Constructor with validation
     */
    public AnalyzeSource {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("Source name cannot be null or blank");
        }
    }
}
