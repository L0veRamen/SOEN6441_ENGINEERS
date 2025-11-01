package services;

import models.Facets;
import models.SourceItem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Service interface for fetching and processing news sources
 * from the external News API.
 *
 * <p>Provides asynchronous methods for retrieving filtered
 * source lists and facet metadata such as available countries,
 * categories, and languages.</p>
 *
 * @author Yang
 * @version 1.0
 * @since 2025-10-30
 */
public interface SourcesService {

    /**
     * Fetches filtered news sources based on optional parameters.
     *
     * @param country optional country filter (ISO code)
     * @param category optional category filter (e.g., business, sports)
     * @param language optional language filter (ISO code)
     * @return a {@link CompletionStage} that resolves to a list of {@link SourceItem}
     * @author Yang
     */
    CompletionStage<List<SourceItem>> listSources(Optional<String> country,
                                                  Optional<String> category,
                                                  Optional<String> language);

    /**
     * Retrieves distinct available filters (countries, categories, languages)
     * for all news sources.
     *
     * @return a {@link CompletionStage} that resolves to a {@link Facets} object
     * @author Yang
     */
    CompletionStage<Facets> getFacets();
}