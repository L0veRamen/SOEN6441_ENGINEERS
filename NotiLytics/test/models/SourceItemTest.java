package models;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for the SourceItem model class.
 * Verifies equality and hashCode behavior based on ID and URL fields.
 *
 * @author Yang
 */
public class SourceItemTest {

    /**
     * Checks that the same object is always equal to itself,
     * and that hashCode remains stable for the same instance.
     *
     * @author Yang
     */
    @Test
    public void sameObjectIsEqual() {
        SourceItem item = new SourceItem("id1", "Name1", "desc", "https://a.com", "business", "en", "us");
        assertEquals(item, item);
        assertEquals(item.hashCode(), item.hashCode());
    }

    /**
     * Verifies that two items with the same ID are considered equal
     * even if their URLs differ.
     *
     * @author Yang
     */
    @Test
    public void equalWhenIdsMatchEvenIfUrlsDiffer() {
        SourceItem a = new SourceItem("id123", "A", "desc", "https://a.com", "tech", "en", "us");
        SourceItem b = new SourceItem("id123", "B", "desc", "https://b.com", "tech", "en", "us");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    /**
     * Ensures equality falls back to URL when both IDs are null.
     * Two sources with the same URL are considered equal.
     *
     * @author Yang
     */
    @Test
    public void equalWhenIdsNullButUrlsMatch() {
        SourceItem a = new SourceItem(null, "A", "desc", "https://same.com", "sports", "en", "ca");
        SourceItem b = new SourceItem(null, "B", "desc", "https://same.com", "sports", "en", "ca");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    /**
     * Confirms that two different items with different IDs and URLs
     * are not considered equal.
     *
     * @author Yang
     */
    @Test
    public void notEqualWhenIdAndUrlBothDifferent() {
        SourceItem a = new SourceItem("id1", "A", "desc", "https://a.com", "science", "en", "us");
        SourceItem b = new SourceItem("id2", "B", "desc", "https://b.com", "science", "en", "us");

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    /**
     * Ensures that a SourceItem is not equal to null
     * or to an object of a completely different class.
     *
     * @author Yang
     */
    @Test
    public void notEqualToNullOrDifferentClass() {
        SourceItem a = new SourceItem("id1", "A", "desc", "https://a.com", "tech", "en", "us");

        assertNotEquals(a, null);
        assertNotEquals(a, "not a SourceItem");
    }

    /**
     * Tests that equality comparison uses ID first.
     * If one item has an ID but the other doesn’t,
     * even if URLs match, they are not equal.
     *
     * @author Yang
     */
    @Test
    public void equalityFallsBackToUrlWhenOneIdMissing() {
        SourceItem a = new SourceItem(null, "A", "desc", "https://same.com", "tech", "en", "us");
        SourceItem b = new SourceItem("idB", "B", "desc", "https://same.com", "tech", "en", "us");

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    /**
     * Verifies that hashCode returns the same value
     * across multiple invocations on the same object.
     *
     * @author Yang
     */
    @Test
    public void hashCodeConsistentAcrossMultipleCalls() {
        SourceItem a = new SourceItem("idX", "Name", "desc", "https://x.com", "tech", "en", "us");
        int initial = a.hashCode();
        assertEquals(initial, a.hashCode());
        assertEquals(initial, a.hashCode());
    }
}