package actors.messages;
import models.Article;
import java.util.List;

/**
 * Task E: Calculate readability scores
 * Sent to ReadabilityActor
 *
 * @param articles List of articles to calculate readability for
 * @author Chen Qian
 */
public record AnalyzeReadability(List<Article> articles) {
    /**
     * Constructor with validation and defensive copy
     */
    public AnalyzeReadability {
        if (articles == null) {
            throw new IllegalArgumentException("Articles list cannot be null");
        }
        articles = List.copyOf(articles);  // Defensive copy
    }
}
