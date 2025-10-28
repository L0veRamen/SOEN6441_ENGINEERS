package models;

import java.util.Objects;

/*
 * @Author Yang
 * @Description
 * Represents a single news source returned by the News API.
 * Each SourceItem contains identifying information such as ID, name,
 * description, URL, category, language, and country.
 * Used for rendering and filtering in the News Sources page.
 *
 * @Date 10:40 2025-10-28
 **/
public class SourceItem {
    public final String id;
    public final String name;
    public final String description;
    public final String url;
    public final String category;
    public final String language;
    public final String country;

    /*
     * @Author Yang
     * @Description
     * Creates a new SourceItem object with the given attributes.
     *
     * @Date 10:39 2025-10-28
     * @Param id          unique source identifier (nullable)
     * @Param name        display name of the source
     * @Param description short summary of the source
     * @Param url         website link of the source
     * @Param category    category type (e.g., business, sports)
     * @Param language    two-letter language code (e.g., en, fr)
     * @Param country     two-letter country code (e.g., us, ca)
     **/
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

    /*
     * @Author Yang
     * @Description
     * Compares two SourceItem objects by their ID or URL.
     * Two sources are considered equal if they share the same ID or URL.
     *
     * @Date 10:39 2025-10-28
     * @Param o object to compare
     * @return true if both represent the same source; otherwise false
     **/
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceItem)) return false;
        SourceItem that = (SourceItem) o;
        String k1 = this.id != null ? this.id : this.url;
        String k2 = that.id != null ? that.id : that.url;
        return Objects.equals(k1, k2);
    }

    /*
     * @Author Yang
     * @Description
     * Generates a hash code based on ID or URL to maintain consistency
     * with the equals() method.
     *
     * @Date 10:39 2025-10-28
     * @return hash code for this SourceItem
     **/
    @Override
    public int hashCode() {
        return Objects.hash(id != null ? id : url);
    }
}