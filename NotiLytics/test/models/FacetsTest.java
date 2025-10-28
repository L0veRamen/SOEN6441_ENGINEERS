package models;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class FacetsTest {

    /**
     * Constructor stores the three lists as-is.
     *
     * @author Yang
     */
    @Test
    public void constructorStoresProvidedLists() {
        List<String> countries  = Arrays.asList("us", "ca");
        List<String> categories = Arrays.asList("business", "technology");
        List<String> languages  = Arrays.asList("en", "fr");

        Facets f = new Facets(countries, categories, languages);

        assertSame("countries list should be the same reference", countries, f.countries);
        assertSame("categories list should be the same reference", categories, f.categories);
        assertSame("languages list should be the same reference", languages, f.languages);

        assertEquals(Arrays.asList("us", "ca"), f.countries);
        assertEquals(Arrays.asList("business", "technology"), f.categories);
        assertEquals(Arrays.asList("en", "fr"), f.languages);
    }

    /**
     * Works with empty lists.
     *
     * @author Yang
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
     * Tolerates null lists (current behavior: keeps null references).
     *
     * @author Yang
     */
    @Test
    public void allowsNullLists_currentBehavior() {
        // Current class does not guard against null; verify it simply stores nulls.
        Facets f = new Facets(null, null, null);

        assertNull(f.countries);
        assertNull(f.categories);
        assertNull(f.languages);
    }
}