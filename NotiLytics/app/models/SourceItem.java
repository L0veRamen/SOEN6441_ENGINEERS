package models;

/**
 * Represents a single news source item returned by the News API.
 * Used in Task C to display source details such as name, category, and country.
 *
 * @author Yang Zhang
 */
import java.util.Objects;

public class SourceItem {
    public final String id;
    public final String name;
    public final String description;
    public final String url;
    public final String category;
    public final String language;
    public final String country;

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

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceItem)) return false;
        SourceItem that = (SourceItem) o;
        String k1 = this.id != null ? this.id : this.url;
        String k2 = that.id != null ? that.id : that.url;
        return Objects.equals(k1, k2);
    }
    @Override public int hashCode() {
        return Objects.hash(id != null ? id : url);
    }
}