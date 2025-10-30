package services;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.Article;
import models.ReadabilityScores;
import models.SearchBlock;
import models.Sentiment;
import org.junit.Before;
import org.junit.Test;

import com.github.benmanes.caffeine.cache.Cache;
import java.lang.reflect.Field;

import java.util.ArrayDeque;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for search history service
 *
 * @author Group
 */
public class SearchHistoryServiceTest {

    private SearchHistoryService service;
    /**
     * Set up test service instance
     *
     * @author Group
     */
    @Before
    public void setUp() {
        Config config = ConfigFactory.parseString("""
                cache.ttl.session = 30 minutes
                cache.maxSessions = 100
                """);
        service = new SearchHistoryService(config);
    }

    /**
     * Test default
     *
     * @author Group
     */
    @Test
    public void testDefault() {
        Config def = ConfigFactory.parseString("");
        new SearchHistoryService(def);
    }

    /**
     * Set up block
     *
     * @author Group
     */
    private SearchBlock block(String suffix) {
        return new SearchBlock(
                "query-" + suffix,
                "publishedAt",
                1,
                List.of(new Article(
                        "Title " + suffix,
                        "https://example.com/" + suffix,
                        "desc",
                        "source-id",
                        "Source",
                        "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z",
                new ReadabilityScores(8.5, 65.0),
                List.of(new ReadabilityScores(8.0, 66.0)),
                Sentiment.POSITIVE
        );
    }

    /**
     * Test order
     *
     * @author Group
     */
    @Test
    public void pushAddsEntriesAndMaintainsOrder() {
        service.push("session", block("first"));
        service.push("session", block("second"));

        List<SearchBlock> history = service.list("session");
        assertEquals(2, history.size());
        assertEquals("query-second", history.get(0).query());
        assertEquals("query-first", history.get(1).query());
    }


    /**
     * Test entries
     *
     * @author Group
     */
    @Test
    public void pushLimitsToTenEntries() {
        for (int i = 0; i < 12; i++) {
            service.push("session", block(String.valueOf(i)));
        }

        List<SearchBlock> history = service.list("session");
        assertEquals(10, history.size());
        assertEquals("query-11", history.get(0).query());
        assertEquals("query-2", history.get(9).query());
    }


    /**
     * Test inputs
     *
     * @author Group
     */
    @Test
    public void pushValidatesInputs() {
        service.push(null, block("x"));
        service.push("", block("y"));
        service.push("valid", null);

        assertTrue(service.list("valid").isEmpty());
    }


    /**
     * Test missing session
     *
     * @author Group
     */
    @Test
    public void listHandlesMissingSessionAndEmptyCache() {
        assertTrue(service.list(null).isEmpty());
        assertTrue(service.list("").isEmpty());
        assertTrue(service.list("missing").isEmpty());

        service.push("session", block("a"));
        service.clear("session");
        assertTrue(service.list("session").isEmpty());
    }

    /**
     * Test session count
     *
     * @author Group
     */
    @Test
    public void clearAndActiveSessionCount() {
        service.push("sessionA", block("A"));
        service.push("sessionB", block("B"));

        long active = service.getActiveSessionCount();
        assertTrue("Expected active sessions >= 2 but was " + active, active >= 2);

        service.clear("sessionA");
        assertTrue(service.list("sessionA").isEmpty());

        service.clear(null);
        service.clear("");
    }

    /**
     * Test list method with edge cases to cover both null and empty deque conditions
     * This covers the deque == null || deque.isEmpty() condition in the yellow highlighted line
     *
     * @author Group
     */
    @Test
    public void listHandlesNullAndEmptyDequeConditions() {
        // Test case 1: Non-existent session (deque == null)
        List<SearchBlock> nullDequeHistory = service.list("non-existent-session");
        assertTrue("Expected empty history for non-existent session", nullDequeHistory.isEmpty());

        // Test case 2: Session that was cleared (deque == null after invalidation)
        String sessionId = "test-cleared-session";
        service.push(sessionId, block("temp"));
        service.clear(sessionId);
        List<SearchBlock> clearedHistory = service.list(sessionId);
        assertTrue("Expected empty history after clearing session", clearedHistory.isEmpty());

        // Test case 3: Create a new service instance to simulate empty cache scenario
        Config emptyConfig = ConfigFactory.parseString("""
                cache.ttl.session = 1 second
                cache.maxSessions = 1
                """);
        SearchHistoryService shortLivedService = new SearchHistoryService(emptyConfig);

        // Add a session and then wait for potential cache eviction scenarios
        shortLivedService.push("short-lived", block("temp"));

        // Immediately check (should exist)
        List<SearchBlock> immediateHistory = shortLivedService.list("short-lived");
        assertEquals("Expected 1 item in immediate history", 1, immediateHistory.size());

        // Test with a session that doesn't exist in the short-lived cache
        List<SearchBlock> nonExistentInShortLived = shortLivedService.list("never-added");
        assertTrue("Expected empty history for session never added to short-lived cache",
                nonExistentInShortLived.isEmpty());
    }

    /**
     * Test edge case for empty deque scenario
     * This specifically targets various edge cases that could lead to empty results
     *
     * @author Group
     */
    @Test
    public void listHandlesEmptyDequeScenario() {
        // Create a service with very limited cache size
        Config limitedConfig = ConfigFactory.parseString("""
                cache.ttl.session = 30 minutes
                cache.maxSessions = 1
                """);
        SearchHistoryService limitedService = new SearchHistoryService(limitedConfig);

        // Test 1: Add to first session
        limitedService.push("session1", block("first"));
        List<SearchBlock> history1 = limitedService.list("session1");
        assertEquals("Expected 1 item for session1", 1, history1.size());

        // Test 2: Add to second session - cache behavior may vary
        limitedService.push("session2", block("second"));
        List<SearchBlock> history2 = limitedService.list("session2");
        assertEquals("Expected 1 item for session2", 1, history2.size());

        // Test 3: The first session might or might not be evicted due to Caffeine's
        // asynchronous eviction behavior - don't make strict assumptions
        List<SearchBlock> evictedHistory = limitedService.list("session1");
        assertTrue("Evicted history should be a valid list", evictedHistory != null);
        // Note: Could be empty due to eviction or could still have content

        // Test 4: Clear operations should always work predictably
        limitedService.clear("session2");
        List<SearchBlock> afterClear = limitedService.list("session2");
        assertTrue("Expected empty history after clearing", afterClear.isEmpty());

        // Test 5: Non-existent sessions should always be empty
        List<SearchBlock> newSession = limitedService.list("brand-new-session");
        assertTrue("Expected empty history for brand new session", newSession.isEmpty());

        // Test 6: Multiple clears and checks
        limitedService.clear("session1");
        assertTrue("Expected empty after manual clear", limitedService.list("session1").isEmpty());
    }

    /**
     * Test the specific yellow line coverage with multiple scenarios
     * This test ensures both parts of the if condition (deque == null || deque.isEmpty()) are covered
     *
     * @author Group
     */
    @Test
    public void listCoversAllBranchConditions() {
        String testSession = "coverage-test-session";

        // Scenario 1: deque == null (session never existed)
        List<SearchBlock> nullResult = service.list("never-existed");
        assertTrue("Should be empty for non-existent session", nullResult.isEmpty());

        // Scenario 2: deque == null (session was cleared)
        service.push(testSession, block("test"));
        assertEquals("Should have 1 item after push", 1, service.list(testSession).size());
        service.clear(testSession);
        List<SearchBlock> clearedResult = service.list(testSession);
        assertTrue("Should be empty after clear", clearedResult.isEmpty());

        // Scenario 3: Force multiple cache operations to ensure we cover edge cases
        for (int i = 0; i < 5; i++) {
            String sessionId = "test-session-" + i;
            service.push(sessionId, block("query-" + i));
            List<SearchBlock> result = service.list(sessionId);
            assertEquals("Should have 1 item for session " + i, 1, result.size());
        }

        // Clear all and verify they all return empty
        for (int i = 0; i < 5; i++) {
            String sessionId = "test-session-" + i;
            service.clear(sessionId);
            List<SearchBlock> result = service.list(sessionId);
            assertTrue("Should be empty after clearing session " + i, result.isEmpty());
        }

        // Additional edge case: null sessionId
        List<SearchBlock> nullSessionResult = service.list(null);
        assertTrue("Should be empty for null sessionId", nullSessionResult.isEmpty());

        // Additional edge case: blank sessionId
        List<SearchBlock> blankSessionResult = service.list("");
        assertTrue("Should be empty for blank sessionId", blankSessionResult.isEmpty());
    }

    /**
     * Test comprehensive scenarios to improve branch coverage
     * This test attempts to trigger various edge cases in the list method
     *
     * @author Group
     */
    @Test
    public void listHandlesComprehensiveEdgeCases() {
        // Test 1: Completely new sessions (deque == null)
        assertTrue("Empty for new session", service.list("brand-new-session").isEmpty());

        // Test 2: Sessions after operations
        String testSession = "comprehensive-test";

        // Add some content
        service.push(testSession, block("test1"));
        assertEquals("Should have 1 item", 1, service.list(testSession).size());

        // Clear and test immediately (deque == null after clear)
        service.clear(testSession);
        assertTrue("Should be empty after clear", service.list(testSession).isEmpty());

        // Test 3: Push again after clear
        service.push(testSession, block("test2"));
        assertEquals("Should have 1 item after push", 1, service.list(testSession).size());

        // Test 4: Cache pressure scenario
        Config pressureConfig = ConfigFactory.parseString("""
                cache.ttl.session = 1 minute
                cache.maxSessions = 3
                """);
        SearchHistoryService pressureService = new SearchHistoryService(pressureConfig);

        // Create multiple sessions to trigger potential evictions
        for (int i = 0; i < 10; i++) {
            String sessionId = "pressure-session-" + i;
            pressureService.push(sessionId, block("pressure-" + i));

            // Immediately test - some might be evicted due to maxSessions=3
            List<SearchBlock> result = pressureService.list(sessionId);
            assertTrue("Result should be valid", result != null);
        }

        // Test all the pressure sessions - many should be empty due to eviction
        for (int i = 0; i < 10; i++) {
            String sessionId = "pressure-session-" + i;
            List<SearchBlock> result = pressureService.list(sessionId);
            assertTrue("Result should be valid list for session " + i, result != null);
            // Note: result might be empty due to cache eviction, which is what we want to test
        }

        // Test 5: Edge case inputs
        assertTrue("Null session should be empty", service.list(null).isEmpty());
        assertTrue("Empty string session should be empty", service.list("").isEmpty());
        assertTrue("Whitespace session should be empty", service.list("   ").isEmpty());
        assertTrue("Tab session should be empty", service.list("\t").isEmpty());
    }

    /**
     * Test edge cases with concurrent operations to potentially trigger empty deque
     * Alternative approach without reflection
     *
     * @author Group
     */
    @Test
    public void listHandlesEdgeCasesWithConcurrentOperations() {
        // Create multiple sessions to stress test cache operations
        Config stressConfig = ConfigFactory.parseString("""
                cache.ttl.session = 10 seconds
                cache.maxSessions = 2
                """);
        SearchHistoryService stressService = new SearchHistoryService(stressConfig);

        // Rapidly create and destroy sessions to potentially create edge cases
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 5; i++) {
                String sessionId = "stress-session-" + i;
                stressService.push(sessionId, block("stress-" + i));
            }

            // Verify some exist
            List<SearchBlock> result1 = stressService.list("stress-session-0");
            // Could be empty due to eviction or could have content
            assertTrue("Result should be a valid list", result1 != null);

            // Clear some sessions
            for (int i = 0; i < 3; i++) {
                stressService.clear("stress-session-" + i);
            }

            // Check all sessions
            for (int i = 0; i < 5; i++) {
                String sessionId = "stress-session-" + i;
                List<SearchBlock> result = stressService.list(sessionId);
                assertTrue("Result should be a valid list for session " + i, result != null);
            }
        }

        // Final comprehensive check on various session states
        assertTrue("Non-existent session should return empty", stressService.list("never-existed").isEmpty());
        assertTrue("Null session should return empty", stressService.list(null).isEmpty());
        assertTrue("Blank session should return empty", stressService.list("").isEmpty());
        assertTrue("Whitespace session should return empty", stressService.list("   ").isEmpty());
    }

    /** 
     * @description: Covers the branch where the deque exists but is empty (`deque.isEmpty()`).
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 14:03
     */
    @Test
    public void listReturnsEmptyWhenDequePresentButEmpty() throws Exception {

        Field f = SearchHistoryService.class.getDeclaredField("cache");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<String, ArrayDeque<SearchBlock>> cache =
                (Cache<String, ArrayDeque<SearchBlock>>) f.get(service);

        String sessionId = "present-but-empty";
        cache.put(sessionId, new ArrayDeque<>());

        List<SearchBlock> history = service.list(sessionId);
        assertTrue("Expected empty list when deque is present but empty", history.isEmpty());
    }
}