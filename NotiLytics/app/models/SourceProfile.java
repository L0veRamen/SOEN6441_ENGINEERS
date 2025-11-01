package models;

/**
 * Represents a news source profile retrieved from the News API.
 *
 * <p>This class contains basic metadata about a news source, such as
 * its name, description, category, and location details.</p>
 *
 * @author Yuhao Ma
 * @version 1.0
 * @since 2025-11-01
 */
public class SourceProfile {

    /** Unique identifier of the news source. */
    public String id;

    /** Name of the news source. */
    public String name;

    /** Short description of the news source. */
    public String description;

    /** Website URL of the news source. */
    public String url;

    /** Category of the news source (e.g., business, technology, sports). */
    public String category;

    /** Language code of the news source (ISO 639-1). */
    public String language;

    /** Country code of the news source (ISO 3166-1 alpha-2). */
    public String country;
}