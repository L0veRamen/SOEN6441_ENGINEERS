package models;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for Article
 *
 * @author Group
 */
public class ArticleTest {

    /**
     * Test article with url
     *
     * @author Group
     */
    @Test
    public void hasUrlReturnsTrueWhenPresent() {
        Article article = new Article(
                "Title",
                "https://example.com",
                "desc",
                "src",
                "Source",
                "2024-01-01T00:00:00Z");

        assertTrue(article.hasUrl());
    }

    /**
     * Test article without url
     *
     * @author Group
     */
    @Test
    public void hasUrlReturnsFalseWhenMissing() {
        Article article = new Article("Title", null, "desc", null, null, null);
        assertFalse(article.hasUrl());
    }

    /**
     * Test article with source
     *
     * @author Group
     */
    @Test
    public void hasSourceChecksNameOrId() {
        Article withName = new Article("Title", null, null, null, "Source Name", null);
        Article withId = new Article("Title", null, null, "source-id", null, null);
        Article without = new Article("Title", null, null, null, null, null);

        assertTrue(withName.hasSource());
        assertTrue(withId.hasSource());
        assertFalse(without.hasSource());
    }

    /**
     * Test article with name and id
     *
     * @author Group
     */
    @Test
    public void getSourceDisplayNamePrefersNameThenId() {
        Article withName = new Article("Title", null, null, "source-id", "Source Name", null);
        Article withoutName = new Article("Title", null, null, "source-id", null, null);
        Article fallback = new Article("Title", null, null, null, null, null);

        assertEquals("Source Name", withName.getSourceDisplayName());
        assertEquals("source-id", withoutName.getSourceDisplayName());
        assertEquals("Unknown Source", fallback.getSourceDisplayName());
    }

    /**
     * Test article with id
     *
     * @author Group
     */
    @Test
    public void testGetSourceId() {
        Article withName = new Article("Title", null, null, null, "Source Name", null);
        Article withoutName = new Article("Title", null, null, "source-id", null, null);

        assertEquals("Source Name", withName.getSourceId());
        assertEquals("source-id", withoutName.getSourceId());
    }
}
