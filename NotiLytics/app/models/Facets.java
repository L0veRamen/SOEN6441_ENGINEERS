package models;

import java.util.List;

/** 
 * @description: Represents distinct filter options (countries, categories, languages) for news sources.
 * @author yang
 * @date: 2025-10-30 12:48
 * @version 1.0
 */
public class Facets {
    public final List<String> countries;
    public final List<String> categories;
    public final List<String> languages;

    /** 
     * @description:  Initializes a Facets object containing lists of unique countries, categories, and languages.
     * @param: countries;categories;languages
     * @return: 
     * @author yang
     * @date: 2025-10-30 12:48
     */
    public Facets(List<String> countries, List<String> categories, List<String> languages) {
        this.countries = countries;
        this.categories = categories;
        this.languages = languages;
    }
}