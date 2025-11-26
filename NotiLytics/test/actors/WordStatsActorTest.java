package actors;

import actors.messages.AnalyzeWords;
import actors.messages.TaskResult;
import models.WordStats;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import services.WordStatsService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WordStatsActor using Pekko TestKit
 * Tests actor message handling and error scenarios
 * 
 * Test Strategy:
 * - Use TestKit for actor testing
 * - Mock WordStatsService to avoid real API calls
 * - Test success and failure paths
 * - Verify message content and structure
 * - Ensure proper error handling
 * 
 * @author Zi Lun Li
 */
public class WordStatsActorTest {

    private static ActorSystem system;

    /**
     * Setup actor system before all tests
     * @author Zi Lun Li
     */
    @BeforeClass
    public static void setup() {
        system = ActorSystem.create("WordStatsActorTest");
    }

    /**
     * Cleanup actor system after all tests
     * @author Zi Lun Li
     */
    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    /**
     * Test that actor successfully processes valid query
     * Verifies that TaskResult is sent with correct structure
     * @author Zi Lun Li
     */
    @Test
    public void testAnalyzeWordsWithValidQuery() {
        new TestKit(system) {{
            // Setup mock service
            WordStatsService mockService = Mockito.mock(WordStatsService.class);
            WordStats mockStats = new WordStats(
                    "test query",
                    10,
                    100,
                    25,
                    List.of(
                            new WordStats.WordFrequency("the", 15),
                            new WordStats.WordFrequency("test", 10),
                            new WordStats.WordFrequency("query", 8)
                    )
            );
            
            when(mockService.computeWordStats("test query"))
                    .thenReturn(CompletableFuture.completedFuture(mockStats));

            // Create actor
            ActorRef actorRef = system.actorOf(
                    WordStatsActor.props(mockService),
                    "wordstats-test-1"
            );

            // Send message
            actorRef.tell(new AnalyzeWords("test query"), getRef());

            // Expect TaskResult
            TaskResult result = expectMsgClass(Duration.ofSeconds(3), TaskResult.class);

            // Verify result
            assertEquals("wordStats", result.taskType());
            assertNotNull(result.data());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.data();
            
            assertEquals("test query", data.get("query"));
            assertEquals(10, data.get("totalArticles"));
            assertEquals(100, data.get("totalWords"));
            assertEquals(25, data.get("uniqueWords"));
            assertEquals(true, data.get("isValid"));
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> frequencies = 
                    (List<Map<String, Object>>) data.get("wordFrequencies");
            assertNotNull(frequencies);
            assertEquals(3, frequencies.size());
            assertEquals("the", frequencies.get(0).get("word"));
            assertEquals(15L, frequencies.get(0).get("count"));

            // Verify service was called
            verify(mockService, times(1)).computeWordStats("test query");
        }};
    }

    /**
     * Test that actor handles service failure gracefully
     * Verifies that fallback data is sent when service throws exception
     * @author Zi Lun Li
     */
    @Test
    public void testAnalyzeWordsWithServiceFailure() {
        new TestKit(system) {{
            // Setup mock service that fails
            WordStatsService mockService = Mockito.mock(WordStatsService.class);
            CompletableFuture<WordStats> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("API Error"));
            
            when(mockService.computeWordStats(anyString()))
                    .thenReturn(failedFuture);

            // Create actor
            ActorRef actorRef = system.actorOf(
                    WordStatsActor.props(mockService),
                    "wordstats-test-2"
            );

            // Send message
            actorRef.tell(new AnalyzeWords("failing query"), getRef());

            // Expect TaskResult with error data
            TaskResult result = expectMsgClass(Duration.ofSeconds(3), TaskResult.class);

            // Verify error result
            assertEquals("wordStats", result.taskType());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.data();
            
            assertEquals("failing query", data.get("query"));
            assertEquals(0, data.get("totalArticles"));
            assertEquals(0, data.get("totalWords"));
            assertEquals(0, data.get("uniqueWords"));
            assertEquals(false, data.get("isValid"));
            assertTrue(data.containsKey("error"));
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> frequencies = 
                    (List<Map<String, Object>>) data.get("wordFrequencies");
            assertTrue(frequencies.isEmpty());
        }};
    }

    /**
     * Test that actor handles empty query
     * Verifies that actor sends empty stats for blank queries
     * @author Zi Lun Li
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAnalyzeWordsRejectsBlankQuery() {
        new AnalyzeWords("   ");  // Should throw
    }

    /**
     * Test that actor handles null query
     * Verifies that actor sends empty stats for null queries
     * @author Zi Lun Li
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAnalyzeWordsRejectsNullQuery() {
        new AnalyzeWords(null);  // Should throw
    }

    /**
     * Test that actor handles empty results from service
     * Verifies correct handling when no articles are found
     * @author Zi Lun Li
     */
    @Test
    public void testAnalyzeWordsWithNoResults() {
        new TestKit(system) {{
            // Setup mock service with empty results
            WordStatsService mockService = Mockito.mock(WordStatsService.class);
            WordStats emptyStats = new WordStats(
                    "no results query",
                    0,
                    0,
                    0,
                    List.of()
            );
            
            when(mockService.computeWordStats("no results query"))
                    .thenReturn(CompletableFuture.completedFuture(emptyStats));

            // Create actor
            ActorRef actorRef = system.actorOf(
                    WordStatsActor.props(mockService),
                    "wordstats-test-4"
            );

            // Send message
            actorRef.tell(new AnalyzeWords("no results query"), getRef());

            // Expect TaskResult
            TaskResult result = expectMsgClass(Duration.ofSeconds(3), TaskResult.class);

            // Verify result indicates no results
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.data();
            
            assertEquals("no results query", data.get("query"));
            assertEquals(0, data.get("totalArticles"));
            assertEquals(false, data.get("isValid"));
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> frequencies = 
                    (List<Map<String, Object>>) data.get("wordFrequencies");
            assertTrue(frequencies.isEmpty());
        }};
    }

    /**
     * Test that actor limits word frequencies to top 50
     * Verifies performance optimization for large result sets
     * @author Zi Lun Li
     */
    @Test
    public void testAnalyzeWordsLimitsFrequencies() {
        new TestKit(system) {{
            // Setup mock service with many results
            WordStatsService mockService = Mockito.mock(WordStatsService.class);
            
            // Create 100 word frequencies
            List<WordStats.WordFrequency> manyFrequencies = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) {
                manyFrequencies.add(new WordStats.WordFrequency("word" + i, 100 - i));
            }
            
            WordStats largeStats = new WordStats(
                    "large query",
                    50,
                    1000,
                    100,
                    manyFrequencies
            );
            
            when(mockService.computeWordStats("large query"))
                    .thenReturn(CompletableFuture.completedFuture(largeStats));

            // Create actor
            ActorRef actorRef = system.actorOf(
                    WordStatsActor.props(mockService),
                    "wordstats-test-5"
            );

            // Send message
            actorRef.tell(new AnalyzeWords("large query"), getRef());

            // Expect TaskResult
            TaskResult result = expectMsgClass(Duration.ofSeconds(3), TaskResult.class);

            // Verify that frequencies are limited to 50
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.data();
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> frequencies = 
                    (List<Map<String, Object>>) data.get("wordFrequencies");
            
            // Should be limited to 50
            assertEquals(50, frequencies.size());
            
            // Verify full stats are still reported
            assertEquals(50, data.get("totalArticles"));
            assertEquals(1000, data.get("totalWords"));
            assertEquals(100, data.get("uniqueWords"));
        }};
    }

    /**
     * Test that actor handles synchronous exceptions
     * Verifies error handling for exceptions thrown during processing
     * @author Zi Lun Li
     */
    @Test
    public void testAnalyzeWordsWithSynchronousException() {
        new TestKit(system) {{
            // Setup mock service that throws exception
            WordStatsService mockService = Mockito.mock(WordStatsService.class);
            when(mockService.computeWordStats(anyString()))
                    .thenThrow(new RuntimeException("Sync error"));

            // Create actor
            ActorRef actorRef = system.actorOf(
                    WordStatsActor.props(mockService),
                    "wordstats-test-6"
            );

            // Send message
            actorRef.tell(new AnalyzeWords("error query"), getRef());

            // Expect TaskResult with error data
            TaskResult result = expectMsgClass(Duration.ofSeconds(3), TaskResult.class);

            // Verify error result
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.data();
            
            assertEquals(false, data.get("isValid"));
            assertTrue(data.containsKey("error"));
        }};
    }

    /**
     * Test that actor can handle multiple messages sequentially
     * Verifies actor state management across multiple requests
     * @author Zi Lun Li
     */
    @Test
    public void testAnalyzeWordsMultipleMessages() {
        new TestKit(system) {{
            // Setup mock service
            WordStatsService mockService = Mockito.mock(WordStatsService.class);
            
            WordStats stats1 = new WordStats("query1", 5, 50, 10, List.of());
            WordStats stats2 = new WordStats("query2", 8, 80, 15, List.of());
            
            when(mockService.computeWordStats("query1"))
                    .thenReturn(CompletableFuture.completedFuture(stats1));
            when(mockService.computeWordStats("query2"))
                    .thenReturn(CompletableFuture.completedFuture(stats2));

            // Create actor
            ActorRef actorRef = system.actorOf(
                    WordStatsActor.props(mockService),
                    "wordstats-test-7"
            );

            // Send first message
            actorRef.tell(new AnalyzeWords("query1"), getRef());
            TaskResult result1 = expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data1 = (Map<String, Object>) result1.data();
            assertEquals("query1", data1.get("query"));
            assertEquals(5, data1.get("totalArticles"));

            // Send second message
            actorRef.tell(new AnalyzeWords("query2"), getRef());
            TaskResult result2 = expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data2 = (Map<String, Object>) result2.data();
            assertEquals("query2", data2.get("query"));
            assertEquals(8, data2.get("totalArticles"));

            // Verify both service calls
            verify(mockService, times(1)).computeWordStats("query1");
            verify(mockService, times(1)).computeWordStats("query2");
        }};
    }
}