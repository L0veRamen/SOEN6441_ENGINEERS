package services;

import models.Article;
import models.SearchBlock;
import models.SourceProfile;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Service for search source profile from NewsApi
 *
 * @author Yuhao Ma
 */
@Singleton
public class ProfileService {

    private final NewsApiClient newsApiClient;

    /**
     * Constructor with dependency injection
     *
     * @param newsApiClient API client for news data
     * @author Yuhao Ma
     */
    @Inject
    public ProfileService(NewsApiClient newsApiClient) {
        this.newsApiClient = newsApiClient;
    }

    /**
     * Response container for search results
     *
     * @param source       Source profile information
     * @param articles     List of articles
     * @author Yuhao Ma
     */
    public record SourceProfileResult(SourceProfile source, List<Article> articles) {}

    /**
     * Perform search and build SearchBlock
     *
     * @param query  Search query
     * @return CompletionStage with SourceProfileResult
     * @author Yuhao Ma
     */
    public CompletionStage<SourceProfileResult> search(String query) {
        var sourceResult = newsApiClient.searchSourceProfile(query);
        var articleResult = newsApiClient.searchEverythingBySource(query);
        return sourceResult.thenCombine(articleResult, (src, news) -> new SourceProfileResult(src, news.articles()));
    }
}

