package actors;

import actors.messages.AnalyzeWords;
import actors.messages.TaskResult;
import models.WordStats;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import services.WordStatsService;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Actor for word statistics analysis (Individual Task B)
 * 
 * Responsibilities:
 * - Receive query from UserActor
 * - Compute word frequency statistics via WordStatsService
 * - Return word frequencies and aggregate counts
 * - Handle computation errors gracefully
 * 
 * Lifecycle: Child of UserActor (supervised)
 * State: Stateless (delegates to WordStatsService)
 * 
 * Integration: Called by UserActor when new search is initiated
 * Message: AnalyzeWords(query)
 * Result: TaskResult("wordStats", data)
 *
 * @author Zi Lun Li
 */
public class WordStatsActor extends AbstractActor {

    private final WordStatsService wordStatsService;

    /**
     * Constructor with dependency injection
     * WordStatsService is injected by Guice
     *
     * @param wordStatsService Service for word statistics computation
     * @author Zi Lun Li
     */
    @Inject
    public WordStatsActor(WordStatsService wordStatsService) {
        this.wordStatsService = wordStatsService;
    }

    /**
     * Factory method for Props
     * Required by Pekko for actor creation
     *
     * @param wordStatsService WordStatsService instance
     * @return Props for creating WordStatsActor
     * @author Zi Lun Li
     */
    public static Props props(WordStatsService wordStatsService) {
        return Props.create(WordStatsActor.class, () -> new WordStatsActor(wordStatsService));
    }

    /**
     * Define message handling behavior
     * Only handles AnalyzeWords messages
     *
     * @return Receive builder with message handlers
     * @author Zi Lun Li
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AnalyzeWords.class, this::handleAnalyzeWords)
                .build();
    }

    /**
     * Handle word statistics analysis request
     * 
     * Flow:
     * 1. Extract query from message
     * 2. Call WordStatsService.computeWordStats(query)
     * 3. On success: Send TaskResult with statistics
     * 4. On error: Log and send fallback with empty statistics
     * 
     * Note: Service returns CompletionStage, so we handle async result
     *
     * @param message AnalyzeWords message from UserActor
     * @author Zi Lun Li
     */
    private void handleAnalyzeWords(AnalyzeWords message) {
        String query = message.query();

        getContext().getSystem().log().debug(
                "WordStatsActor analyzing query: '{}'", query
        );

        try {
            // Call service (asynchronous)
            wordStatsService.computeWordStats(query)
                    .thenAccept(stats -> {
                        // Build result data
                        Map<String, Object> data = new HashMap<>();
                        data.put("query", stats.query());
                        data.put("totalArticles", stats.totalArticles());
                        data.put("totalWords", stats.totalWords());
                        data.put("uniqueWords", stats.uniqueWords());
                        data.put("isValid", stats.totalArticles() > 0);

                        // Convert word frequencies to a serializable format
                        var frequencies = stats.wordFrequencies().stream()
                                .limit(50) // Limit to top 50 words for performance
                                .map(wf -> Map.<String, Object>of(
                                        "word", wf.word(),
                                        "count", wf.count()
                                ))
                                .collect(Collectors.toList());
                        data.put("wordFrequencies", frequencies);

                        // Send results to UserActor
                        getSender().tell(
                                new TaskResult("wordStats", data),
                                getSelf()
                        );

                        getContext().getSystem().log().info(
                                "Word stats analysis completed: {} articles, {} unique words for query '{}'",
                                stats.totalArticles(),
                                stats.uniqueWords(),
                                query
                        );
                    })
                    .exceptionally(error -> {
                        // Handle error gracefully
                        getContext().getSystem().log().error(
                                "Word stats analysis failed for query '{}': {}",
                                query, error.getMessage()
                        );

                        sendEmptyStats(query);
                        return null;
                    });

        } catch (Exception error) {
            // Handle synchronous errors
            getContext().getSystem().log().error(
                    "Word stats analysis threw exception for query '{}': {}",
                    query, error.getMessage()
            );

            sendEmptyStats(query);
        }
    }

    /**
     * Send empty statistics (fallback)
     * Used when computation fails or query is invalid
     *
     * @param query Original query string
     * @author Zi Lun Li
     */
    private void sendEmptyStats(String query) {
        Map<String, Object> fallbackData = new HashMap<>();
        fallbackData.put("query", query);
        fallbackData.put("totalArticles", 0);
        fallbackData.put("totalWords", 0);
        fallbackData.put("uniqueWords", 0);
        fallbackData.put("isValid", false);
        fallbackData.put("wordFrequencies", java.util.Collections.emptyList());
        fallbackData.put("error", "Failed to compute word statistics");

        getSender().tell(
                new TaskResult("wordStats", fallbackData),
                getSelf()
        );
    }
}