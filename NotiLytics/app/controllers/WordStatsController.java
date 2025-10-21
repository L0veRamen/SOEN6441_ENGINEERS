package controllers;

// Zi Lun Li

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.WordStatsService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletionStage;

@Singleton
public class WordStatsController extends Controller {
    
    private static final Logger log = LoggerFactory.getLogger(WordStatsController.class);
    
    private final WordStatsService wordStatsService;
    
    @Inject
    public WordStatsController(WordStatsService wordStatsService) {
        this.wordStatsService = wordStatsService;
    }
    
    public CompletionStage<Result> view(Http.Request request, String query) {
        if (query == null || query.isBlank()) {
            log.warn("Word stats requested with empty query");
            return java.util.concurrent.CompletableFuture.completedFuture(
                badRequest("Search query is required")
            );
        }
        
        log.info("Word stats requested for query: '{}'", query);
        
        return wordStatsService.computeStats(query)
            .thenApply(stats -> ok(views.html.wordstats.render(stats, request)));
    }
}