package models;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for models.SourceItem.
 *
 * Focus:
 * - Equality behavior (by ID or URL)
 * - HashCode consistency
 * - Defensive checks for nulls and different types
 *
 * @author Yang
 */
public class SourceItemTest {

    @Test
    public void sameObjectIsEqual() {
        SourceItem item = new SourceItem("id1", "Name1", "desc", "https://a.com", "business", "en", "us");
        assertEquals(item, item);
        assertEquals(item.hashCode(), item.hashCode());
    }

    @Test
    public void equalWhenIdsMatchEvenIfUrlsDiffer() {
        SourceItem a = new SourceItem("id123", "A", "desc", "https://a.com", "tech", "en", "us");
        SourceItem b = new SourceItem("id123", "B", "desc", "https://b.com", "tech", "en", "us");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalWhenIdsNullButUrlsMatch() {
        SourceItem a = new SourceItem(null, "A", "desc", "https://same.com", "sports", "en", "ca");
        SourceItem b = new SourceItem(null, "B", "desc", "https://same.com", "sports", "en", "ca");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualWhenIdAndUrlBothDifferent() {
        SourceItem a = new SourceItem("id1", "A", "desc", "https://a.com", "science", "en", "us");
        SourceItem b = new SourceItem("id2", "B", "desc", "https://b.com", "science", "en", "us");

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualToNullOrDifferentClass() {
        SourceItem a = new SourceItem("id1", "A", "desc", "https://a.com", "tech", "en", "us");

        assertNotEquals(a, null);
        assertNotEquals(a, "not a SourceItem");
    }

    @Test
    public void equalityFallsBackToUrlWhenOneIdMissing() {
        SourceItem a = new SourceItem(null, "A", "desc", "https://same.com", "tech", "en", "us");
        SourceItem b = new SourceItem("idB", "B", "desc", "https://same.com", "tech", "en", "us");

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCodeConsistentAcrossMultipleCalls() {
        SourceItem a = new SourceItem("idX", "Name", "desc", "https://x.com", "tech", "en", "us");
        int initial = a.hashCode();
        assertEquals(initial, a.hashCode());
        assertEquals(initial, a.hashCode());
    }
}