package actors;

import actors.messages.AnalyzeReadability;
import actors.messages.TaskResult;
import models.Article;
import models.ReadabilityScores;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import services.ReadabilityService;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Actor for readability analysis (Individual Task E)
 * <p>
 * Responsibilities:
 * - Receive articles from UserActor
 * - Compute readability scores via ReadabilityService
 * - Return average grade level and reading ease
 * - Handle computation errors gracefully
 * <p>
 * Lifecycle: Child of UserActor (supervised)
 * State: Stateless (delegates to ReadabilityService)
 * <p>
 * Integration: Called by UserActor when new articles arrive
 * Message: AnalyzeReadability(articles)
 * Result: TaskResult("readability", data)
 *
 * @author Chen Qian
 */
public class ReadabilityActor extends AbstractActor {

    private final ReadabilityService readabilityService;

    /**
     * Constructor with dependency injection
     * ReadabilityService is injected by Guice
     *
     * @param readabilityService Service for readability computation
     * @author Chen Qian
     */
    @Inject
    public ReadabilityActor(ReadabilityService readabilityService) {
        this.readabilityService = readabilityService;
    }

    /**
     * Factory method for Props
     * Required by Pekko for actor creation
     *
     * @return Props for creating ReadabilityActor
     * @author Chen Qian
     */
    public static Props props(ReadabilityService readabilityService) {
        return Props.create(ReadabilityActor.class, () -> new ReadabilityActor(readabilityService));
    }

    /**
     * Define message handling behavior
     * Only handles AnalyzeReadability messages
     *
     * @return Receive builder with message handlers
     * @author Chen Qian
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AnalyzeReadability.class, this::handleAnalyzeReadability)
                .build();
    }

    /**
     * Handle readability analysis request
     * <p>
     * Flow:
     * 1. Extract articles from message
     * 2. Call ReadabilityService.calculateAverageReadability()
     * 3. On success: Send TaskResult with scores
     * 4. On error: Log and send zero scores
     * <p>
     * Note: Service is synchronous, so we wrap in try-catch
     *
     * @param message AnalyzeReadability message from UserActor
     * @author Chen Qian
     */
    private void handleAnalyzeReadability(AnalyzeReadability message) {
        List<Article> articles = message.articles();

        if (articles == null || articles.isEmpty()) {
            getContext().getSystem().log().warning(
                    "ReadabilityActor received empty articles list"
            );
            sendZeroScores();
            return;
        }

        getContext().getSystem().log().debug(
                "ReadabilityActor analyzing {} articles", articles.size()
        );

        try {
            // Call D1 service (synchronous)
            ReadabilityScores averageScores = readabilityService.calculateAverageReadability(articles);

            // each article scores
            List<ReadabilityScores> articleScores = articles.stream()
                    .map(readabilityService::calculateArticleReadability)
                    .toList();

            // Build result data
            Map<String, Object> data = new HashMap<>();
            data.put("gradeLevel", averageScores.gradeLevel());
            data.put("readingEase", averageScores.readingEase());
            data.put("interpretation", averageScores.getReadingEaseInterpretation());
            data.put("articleCount", articles.size());
            data.put("isValid", averageScores.isValid());

            // pack individual scores in a serializable way
            List<Map<String, Object>> articleScoresJson = articleScores.stream()
                    .map(s -> Map.<String, Object>of(
                            "gradeLevel", s.gradeLevel(),
                            "readingEase", s.readingEase(),
                            "interpretation", s.getReadingEaseInterpretation(),
                            "isValid", s.isValid()
                    ))
                    .toList();
            data.put("articleScores", articleScoresJson);

            // Send results to UserActor
            getSender().tell(
                    new TaskResult("readability", data),
                    getSelf()
            );

            getContext().getSystem().log().info(
                    "Readability analysis completed: Grade={}, Ease={} for {} articles",
                    averageScores.gradeLevel(),
                    averageScores.readingEase(),
                    articles.size()
            );

        } catch (Exception error) {
            // Handle error gracefully
            getContext().getSystem().log().error(
                    "Readability analysis failed: {}", error.getMessage()
            );

            sendZeroScores();
        }
    }

    /**
     * Send zero scores (fallback)
     * Used when computation fails or articles are empty
     *
     * @author Chen Qian
     */
    private void sendZeroScores() {
        Map<String, Object> fallbackData = new HashMap<>();
        fallbackData.put("gradeLevel", 0.0);
        fallbackData.put("readingEase", 0.0);
        fallbackData.put("interpretation", "Unknown");
        fallbackData.put("articleCount", 0);
        fallbackData.put("isValid", false);
        fallbackData.put("error", "Failed to compute readability");

        getSender().tell(
                new TaskResult("readability", fallbackData),
                getSelf()
        );
    }
}