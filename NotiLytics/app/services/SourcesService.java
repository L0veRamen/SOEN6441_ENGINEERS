package services;

import models.Facets;
import models.SourceItem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/*
 * @Author Yang
 * @Description
 * Service interface for retrieving and processing news sources data.
 * Provides methods to list filtered sources and to fetch all unique filter options (facets)
 * such as countries, categories, and languages for dropdown menus.
 *
 * @Date 10:46 2025-10-28
 * @Param country   optional country filter (e.g., "us")
 * @Param category  optional category filter (e.g., "business")
 * @Param language  optional language filter (e.g., "en")
 * @return          asynchronous results containing news sources or facet data
 **/
public interface SourcesService {

    CompletionStage<List<SourceItem>> listSources(Optional<String> country,
                                                  Optional<String> category,
                                                  Optional<String> language);

    // returns all distinct filter options from all sources
    CompletionStage<Facets> getFacets();
}