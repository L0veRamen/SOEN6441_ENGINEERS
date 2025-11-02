package models;

import java.util.Objects;

/**
 * Represents a single news source with details such as
 * ID, name, URL, category, language, and country.
 *
 * <p>This class is immutable and used to hold source metadata
 * retrieved from the News API.</p>
 *
 * @author Yang
 * @version 1.0
 * @since 2025-10-30
 */
public class SourceItem {

    /** Unique identifier of the news source. */
    public final String id;

    /** Name of the news source. */
    public final String name;

    /** Short description of the source. */
    public final String description;

    /** Website URL of the source. */
    public final String url;

    /** Category of the news source (e.g., business, sports). */
    public final String category;

    /** Language code of the news source (ISO 639-1). */
    public final String language;

    /** Country code of the news source (ISO 3166-1 alpha-2). */
    public final String country;

    /**
     * Creates a new {@code SourceItem} with the specified attributes.
     *
     * @param id unique identifier of the source
     * @param name source name
     * @param description short description
     * @param url source website URL
     * @param category source category
     * @param language source language code
     * @param country source country code
     * @author Yang
     */
    public SourceItem(String id, String name, String description, String url,
                      String category, String language, String country) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.url = url;
        this.category = category;
        this.language = language;
        this.country = country;
    }

    /**
     * Checks equality based on ID or URL.
     *
     * @param o other object to compare
     * @return {@code true} if both represent the same source; {@code false} otherwise
     * @author Yang
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceItem)) return false;
        SourceItem that = (SourceItem) o;
        String k1 = this.id != null ? this.id : this.url;
        String k2 = that.id != null ? that.id : that.url;
        return Objects.equals(k1, k2);
    }

    /**
     * Computes hash code consistent with {@link #equals(Object)}.
     *
     * @return hash code derived from ID or URL
     * @author Yang
     */
    @Override
    public int hashCode() {
        return Objects.hash(id != null ? id : url);
    }
}