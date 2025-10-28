package services;

import models.SourceItem;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Service interface for retrieving news sources from the News API.
 * Used in Task C to fetch and filter available sources asynchronously.
 *
 * @author Yang Zhang
 */
public interface SourcesService {

    /**
     * Fetches the list of available news sources from the API.
     *
     * @param country  optional country filter (e.g., "us")
     * @param category optional category filter (e.g., "business")
     * @param language optional language filter (e.g., "en")
     * @return a CompletionStage with a list of matching SourceItem objects
     */
    CompletionStage<List<SourceItem>> listSources(Optional<String> country,
                                                  Optional<String> category,
                                                  Optional<String> language);
}