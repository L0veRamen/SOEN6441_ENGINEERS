package services;

import models.Facets;
import models.SourceItem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** 
 * @description: Defines methods for fetching and processing news sources from the News API.   
 * @param: null
 * @return: 
 * @author yang
 * @date: 2025-10-30 13:03
 */
public interface SourcesService {

    CompletionStage<List<SourceItem>> listSources(Optional<String> country,
                                                  Optional<String> category,
                                                  Optional<String> language);

    CompletionStage<Facets> getFacets();
}