package controllers;

import play.mvc.*;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
//public class HomeController extends Controller {
//
//    /**
//     * An action that renders an HTML page with a welcome message.
//     * The configuration in the <code>routes</code> file means that
//     * this method will be called when the application receives a
//     * <code>GET</code> request with a path of <code>/</code>.
//     */
//    public Result index() {
//        return ok(views.html.index.render());
//    }
//
//}

import models.SearchBlock;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
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
 * @author Chen Qian
 */
@Singleton
public class HomeController extends Controller {

    private final SearchHistoryService historyService;

    /**
     * Constructor with dependency injection
     *
     * @param historyService Service for session history
     * @author Chen Qian
     */
    @Inject
    public HomeController(SearchHistoryService historyService) {
        this.historyService = historyService;
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
}
