package actors.messages;

/**
 * Generic result from any task actor
 * All child actors send this message type back to UserActor
 *
 * @param taskType Task identifier (e.g., "sourceProfile", "wordStats")
 * @param data Result data (type depends on task)
 * @author Group Members
 */
public record TaskResult(
        String taskType,
        Object data
) {
    /**
     * Constructor with validation
     */
    public TaskResult {
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("Task type cannot be null or blank");
        }
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
    }

    /**
     * Create a result for source profile task
     */
    public static TaskResult sourceProfile(Object data) {
        return new TaskResult("sourceProfile", data);
    }

    /**
     * Create a result for word stats task
     */
    public static TaskResult wordStats(Object data) {
        return new TaskResult("wordStats", data);
    }

    /**
     * Create a result for news sources task
     */
    public static TaskResult sources(Object data) {
        return new TaskResult("sources", data);
    }

    /**
     * Create a result for sentiment task
     */
    public static TaskResult sentiment(Object data) {
        return new TaskResult("sentiment", data);
    }

    /**
     * Create a result for readability task
     */
    public static TaskResult readability(Object data) {
        return new TaskResult("readability", data);
    }
}
