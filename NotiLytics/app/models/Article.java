package models;

/**
 * Represents a news article from NewsAPI
 * Immutable record for thread safety
 *
 * @param title         Article headline
 * @param url           External article link
 * @param description   Article summary/description
 * @param sourceId      Source identifier (e.g., "bbc-news")
 * @param sourceName    Human-readable source name
 * @param publishedAt   ISO 8601 timestamp
 *
 * @author Chen Qian
 */
public record Article(
        String title,
        String url,
        String description,
        String sourceId,
        String sourceName,
        String publishedAt
) {
    /**
     * Check if article has a valid URL
     *
     * @return true if URL is non-null and non-empty
     * @author Chen Qian
     */
    public boolean hasUrl() {
        return url != null && !url.isBlank();
    }

    /**
     * Check if article has a valid source
     *
     * @return true if source name or ID is present
     * @author Chen Qian
     */
    public boolean hasSource() {
        return (sourceName != null && !sourceName.isBlank())
                || (sourceId != null && !sourceId.isBlank());
    }

    /**
     * Get display name for source
     * Returns source name if available, otherwise source ID
     *
     * @return Display name for source
     * @author Chen Qian
     */
    public String getSourceDisplayName() {
        if (sourceName != null && !sourceName.isBlank()) {
            return sourceName;
        }
        if (sourceId != null && !sourceId.isBlank()) {
            return sourceId;
        }
        return "Unknown Source";
    }

    /**
     * Get search key for source profile
     * Use id first or name if id is not provided
     *
     * @return Source id or name
     * @author Yuhao Ma
     */
    public String getSourceId() {
        return (sourceId != null)? sourceId : getSourceDisplayName();
    }
}
