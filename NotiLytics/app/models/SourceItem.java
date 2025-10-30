package models;

import java.util.Objects;

/** 
 * @description: Represents a single news source with details such as ID, name, URL, category, language, and country.
 * @author yang
 * @date: 2025-10-30 12:48
 * @version 1.0
 */
public class SourceItem {
    public final String id;
    public final String name;
    public final String description;
    public final String url;
    public final String category;
    public final String language;
    public final String country;
    
    /** 
     * @description: Initializes a SourceItem with all basic information about a news source.
     * @param: id;name;description;url;category;language;country
     * @return: 
     * @author yang
     * @date: 2025-10-30 12:48
     */
    public SourceItem(String id, String name, String description, String url, String category, String language, String country) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.url = url;
        this.category = category;
        this.language = language;
        this.country = country;
    }

    /**
     * @description:  Compares two SourceItem objects based on their ID or URL for equality.
     * @param: o
     * @return: boolean
     * @author yang
     * @date: 2025-10-30 12:49
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
     * @description:  Generates a hash code based on the ID or URL to ensure consistent behavior with equals().
     * @param:
     * @return: int
     * @author yang
     * @date: 2025-10-30 12:49
     */
    @Override
    public int hashCode() {
        return Objects.hash(id != null ? id : url);
    }
}