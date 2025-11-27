package actors;

import actors.messages.AnalyzeSentiment;
import actors.messages.TaskResult;
import models.Article;
import models.Sentiment;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import services.SentimentAnalysisService;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Actor for sentiment analysis (Individual Task D)
 *
 * Responsibilities:
 * - Receive articles from UserActor
 * - Compute sentiment scores via SentimentAnalysisService
 * - Return overall sentiment and per-article sentiment
 * - Handle computation errors gracefully
 *
 * Lifecycle: Child of UserActor (supervised)
 * State: Stateless (delegates to SentimentAnalysisService)
 *
 * Integration: Called by UserActor when new articles arrive
 * Message: AnalyzeSentiment(articles)
 * Result: TaskResult("sentiment", data)
 *
 * @author Group Members
 */
public class SentimentAnalysisActor extends AbstractActor {

    private final SentimentAnalysisService sentimentService;

    /**
     * Constructor with dependency injection
     * SentimentAnalysisService is injected by Guice
     *
     * @param sentimentService Service for sentiment analysis
     */
    @Inject
    public SentimentAnalysisActor(SentimentAnalysisService sentimentService) {
        this.sentimentService = sentimentService;
    }

    /**
     * Factory method for Props
     * Required by Pekko for actor creation
     *
     * @param sentimentService SentimentAnalysisService instance
     * @return Props for creating SentimentAnalysisActor
     */
    public static Props props(SentimentAnalysisService sentimentService) {
        return Props.create(SentimentAnalysisActor.class, () -> new SentimentAnalysisActor(sentimentService));
    }

    /**
     * Define message handling behavior
     * Only handles AnalyzeSentiment messages
     *
     * @return Receive builder with message handlers
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AnalyzeSentiment.class, this::handleAnalyzeSentiment)
                .build();
    }

    /**
     * Handle sentiment analysis request
     *
     * Flow:
     * 1. Extract articles from message
     * 2. Extract words from articles (titles + descriptions)
     * 3. Call SentimentAnalysisService.analyzeWordList(words)
     * 4. On success: Send TaskResult with overall sentiment
     * 5. On error: Send fallback with NEUTRAL sentiment
     *
     * @param message AnalyzeSentiment message from UserActor
     */
    private void handleAnalyzeSentiment(AnalyzeSentiment message) {
        List<Article> articles = message.articles();

        if (articles == null || articles.isEmpty()) {
            getContext().getSystem().log().warning(
                    "SentimentAnalysisActor received empty articles list"
            );
            sendNeutralSentiment();
            return;
        }

        getContext().getSystem().log().debug(
                "SentimentAnalysisActor analyzing {} articles", articles.size()
        );

        try {
            // Extract words from all articles (titles + descriptions)
            List<String> allWords = articles.stream()
                    .flatMap(article -> {
                        List<String> words = new java.util.ArrayList<>();
                        if (article.title() != null) {
                            words.addAll(java.util.Arrays.asList(article.title().split("\\s+")));
                        }
                        if (article.description() != null) {
                            words.addAll(java.util.Arrays.asList(article.description().split("\\s+")));
                        }
                        return words.stream();
                    })
                    .toList();

            // Call D1 service (static method - synchronous)
            Sentiment overallSentiment = sentimentService.analyzeWordList(allWords);
            if (overallSentiment == null) {
                throw new IllegalStateException("Sentiment result cannot be null");
            }

            // Build result data - only overall sentiment
            Map<String, Object> data = new HashMap<>();
            data.put("sentiment", overallSentiment.toString());
            data.put("isValid", true);

            // Send results to UserActor
            getSender().tell(
                    new TaskResult("sentiment", data),
                    getSelf()
            );

            getContext().getSystem().log().info(
                    "Sentiment analysis completed: overall={}", overallSentiment
            );

        } catch (Exception error) {
            // Handle error gracefully
            getContext().getSystem().log().error(
                    "Sentiment analysis failed: {}", error.getMessage()
            );

            sendNeutralSentiment();
        }
    }

    /**
     * Send neutral/error sentiment to UserActor
     */
    private void sendNeutralSentiment() {
        Map<String, Object> data = new HashMap<>();
        data.put("sentiment", Sentiment.NEUTRAL.toString());
        data.put("isValid", false);
        data.put("error", "Failed to compute sentiment");

        getSender().tell(
                new TaskResult("sentiment", data),
                getSelf()
        );
    }
}
