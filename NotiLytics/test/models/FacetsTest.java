package models;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** 
 * @description: Unit tests for the Facets model class.
 * @author yang
 * @date: 2025-10-30 12:51
 * @version 1.0
 */
public class FacetsTest {
    
    /** 
     * @description:  Verifies that the constructor correctly stores the provided list references and values.
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:51
     */
    @Test
    public void constructorStoresProvidedLists() {
        List<String> countries = Arrays.asList("us", "ca");
        List<String> categories = Arrays.asList("business", "technology");
        List<String> languages = Arrays.asList("en", "fr");

        Facets f = new Facets(countries, categories, languages);

        assertSame("countries list should be the same reference", countries, f.countries);
        assertSame("categories list should be the same reference", categories, f.categories);
        assertSame("languages list should be the same reference", languages, f.languages);

        assertEquals(Arrays.asList("us", "ca"), f.countries);
        assertEquals(Arrays.asList("business", "technology"), f.categories);
        assertEquals(Arrays.asList("en", "fr"), f.languages);
    }
    
    /** 
     * @description:  Confirms that the class supports empty lists without errors.
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:51
     */
    @Test
    public void supportsEmptyLists() {
        List<String> empty = Collections.emptyList();

        Facets f = new Facets(empty, empty, empty);

        assertNotNull(f.countries);
        assertNotNull(f.categories);
        assertNotNull(f.languages);

        assertTrue(f.countries.isEmpty());
        assertTrue(f.categories.isEmpty());
        assertTrue(f.languages.isEmpty());
    }
    
    /** 
     * @description:  Ensures that null lists are allowed and stored as null (current behavior).
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 12:51
     */
    @Test
    public void allowsNullLists_currentBehavior() {
        Facets f = new Facets(null, null, null);

        assertNull(f.countries);
        assertNull(f.categories);
        assertNull(f.languages);
    }
}