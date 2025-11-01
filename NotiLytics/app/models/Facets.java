package models;

import java.util.List;

/**
 * Represents distinct filter options for news sources,
 * including countries, categories, and languages.
 *
 * <p>This class is an immutable data holder used for
 * building search facets in the application.</p>
 *
 * @author Yang
 * @version 1.0
 * @since 2025-10-30
 */
public class Facets {

    /** List of unique country codes. */
    public final List<String> countries;

    /** List of unique news categories. */
    public final List<String> categories;

    /** List of unique language codes. */
    public final List<String> languages;

    /**
     * Creates a new {@code Facets} instance with given country, category, and language lists.
     *
     * @param countries list of unique country codes
     * @param categories list of unique news categories
     * @param languages list of unique language codes
     * @author Yang
     */
    public Facets(List<String> countries, List<String> categories, List<String> languages) {
        this.countries = countries;
        this.categories = categories;
        this.languages = languages;
    }
}