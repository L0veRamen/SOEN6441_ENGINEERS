package models;

import java.util.List;


/*
 * @Author Yang
 * @Description
 * Represents distinct filter options (facets) for the News Sources page.
 * Each list stores all unique values collected from the available sources,
 * allowing users to choose valid countries, categories, and languages
 * instead of typing manually.
 *
 * @Date 10:45 2025-10-28
 * @Param countries  list of distinct country codes
 * @Param categories list of distinct news categories
 * @Param languages  list of distinct language codes
 * @return Facets object containing all filter options
 **/

public class Facets {
    public final List<String> countries;
    public final List<String> categories;
    public final List<String> languages;

    public Facets(List<String> countries, List<String> categories, List<String> languages) {
        this.countries = countries;
        this.categories = categories;
        this.languages = languages;
    }
}