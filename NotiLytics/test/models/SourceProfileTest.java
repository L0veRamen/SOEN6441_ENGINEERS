package models;

import org.junit.Test;

import static org.junit.Assert.assertNull;

/**
 *  Test for source profile
 *
 * @author Yuhao Ma
 */
public class SourceProfileTest {
    /**
     * Test source
     *
     * @author Yuhao Ma
     */
    @Test
    public void testSource() {
        SourceProfile sp = new SourceProfile();
        assertNull(sp.id);
    }
}
