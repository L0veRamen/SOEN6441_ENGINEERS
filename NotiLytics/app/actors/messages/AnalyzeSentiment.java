package actors.messages;

import models.Article;
import java.util.List;

/**
 * Task D: Analyze sentiment
 * Sent to SentimentAnalysisActor
 *
 * @param articles List of articles to analyze sentiment for
 * @author Group Members
 */
public record AnalyzeSentiment(List<Article> articles) {
    /**
     * Constructor with validation and defensive copy
     */
    public AnalyzeSentiment {
        if (articles == null) {
            throw new IllegalArgumentException("Articles list cannot be null");
        }
        articles = List.copyOf(articles);  // Defensive copy
    }
}
