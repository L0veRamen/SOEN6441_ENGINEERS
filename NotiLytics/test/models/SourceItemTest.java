package models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/** 
 * @description: Unit tests for the SourceItem model,Verifies correct equality and hashCode behavior based on id and url.
 * @author yang
 * @date: 2025-10-30 12:51
 * @version 1.0
 */
public class SourceItemTest {
    
    /** 
     * @description:  Confirms that a SourceItem equals itself and its hashCode is stable.
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:51
     */
    @Test
    public void sameObjectIsEqual() {
        SourceItem item = new SourceItem("id1", "Name1", "desc", "https://a.com", "business", "en", "us");
        assertEquals(item, item);
        assertEquals(item.hashCode(), item.hashCode());
    }
    
    /** 
     * @description: Checks that two items with the same id are equal even if their URLs differ.
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:52
     */
    @Test
    public void equalWhenIdsMatchEvenIfUrlsDiffer() {
        SourceItem a = new SourceItem("id123", "A", "desc", "https://a.com", "tech", "en", "us");
        SourceItem b = new SourceItem("id123", "B", "desc", "https://b.com", "tech", "en", "us");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
    
    /** 
     * @description:  Ensures that two items without IDs are equal if their URLs match.
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:52
     */
    @Test
    public void equalWhenIdsNullButUrlsMatch() {
        SourceItem a = new SourceItem(null, "A", "desc", "https://same.com", "sports", "en", "ca");
        SourceItem b = new SourceItem(null, "B", "desc", "https://same.com", "sports", "en", "ca");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
    
    /** 
     * @description:  Verifies that items with different ids and URLs are not equal.
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:52
     */
    @Test
    public void notEqualWhenIdAndUrlBothDifferent() {
        SourceItem a = new SourceItem("id1", "A", "desc", "https://a.com", "science", "en", "us");
        SourceItem b = new SourceItem("id2", "B", "desc", "https://b.com", "science", "en", "us");

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }
    
    /** 
     * @description:  Checks that equality returns false when compared with null or a different class.
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:52
     */
    @Test
    public void notEqualToNullOrDifferentClass() {
        SourceItem a = new SourceItem("id1", "A", "desc", "https://a.com", "tech", "en", "us");

        assertNotEquals(a, null);
        assertNotEquals(a, "not a SourceItem");
    }
    
    /** 
     * @description:  Checks that equality returns false when compared with null or a different class.
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:52
     */
    @Test
    public void equalityFallsBackToUrlWhenOneIdMissing() {
        SourceItem a = new SourceItem(null, "A", "desc", "https://same.com", "tech", "en", "us");
        SourceItem b = new SourceItem("idB", "B", "desc", "https://same.com", "tech", "en", "us");

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    /**
     * @description:  Verifies that hashCode produces consistent results across multiple calls.
     * @param:
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:52
     */
    @Test
    public void hashCodeConsistentAcrossMultipleCalls() {
        SourceItem a = new SourceItem("idX", "Name", "desc", "https://x.com", "tech", "en", "us");
        int initial = a.hashCode();
        assertEquals(initial, a.hashCode());
        assertEquals(initial, a.hashCode());
    }
}