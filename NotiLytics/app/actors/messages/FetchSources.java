package actors.messages;


/**
 * Task C: Fetch news sources
 * Sent to NewsSourcesActor
 *
 * @param country Country filter (nullable)
 * @param category Category filter (nullable)
 * @param language Language filter (nullable)
 * @author Group Members
 */
public record FetchSources(
        String country,
        String category,
        String language
) {
    // All parameters are optional (nullable)
}