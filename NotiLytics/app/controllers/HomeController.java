package controllers;


/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */

import models.SearchBlock;
import models.SourceProfile;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.ProfileService;
import services.SearchHistoryService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Controller for home page
 * Displays search form and history blocks
 * Responsibilities:
 * - Render home page with search history
 * - Extract session ID from Play session
 * - NO business logic (thin controller)
 *
 * @author Group
 */
@Singleton
public class HomeController extends Controller {

    private final SearchHistoryService historyService;
    private final ProfileService profileService;

    /**
     * Constructor with dependency injection
     *
     * @param historyService Service for session history
     * @author Chen Qian
     */
    @Inject
    public HomeController(SearchHistoryService historyService, ProfileService profileService) {
        this.historyService = historyService;
        this.profileService = profileService;
    }

    /**
     * Display home page with search form and history
     *
     * @param request HTTP request with session
     * @return Async result with rendered view
     * @author Chen Qian
     */
    public CompletionStage<Result> index(Http.Request request) {
        String sessionId = getSessionId(request);
        List<SearchBlock> history = historyService.list(sessionId);

        // Pass both searchHistory and request to the template
        return CompletableFuture.completedFuture(
                ok(views.html.home.render(history, request))
        );
    }

    /**
     * Extract or create session ID from Play session
     *
     * @param request HTTP request
     * @return Session ID string
     * @author Chen Qian
     */
    private String getSessionId(Http.Request request) {
        return request.session().get("sessionId")
                .orElse(java.util.UUID.randomUUID().toString());
    }

    /**
     * Display source profile page
     *
     * @param request HTTP request with session
     * @param query   Name or id of the source
     * @return Async result with rendered view
     * @author Yuhao Ma
     */
    public CompletionStage<Result> source(Http.Request request, String query) {
        var result = profileService.search(query);
        return result.thenApply( res -> {
            if (res.source() == null) {
                var source = new SourceProfile();
                source.name = query;
                return ok(views.html.profile.render(source, res.articles()));
            }
            return ok(views.html.profile.render(res.source(), res.articles()));
        });
    }
}
