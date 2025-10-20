package controllers;

import models.SourceProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.ProfileService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class SourceProfileController extends Controller {

    private static final Logger log = LoggerFactory.getLogger(SourceProfileController.class);

    private final ProfileService profileService;

    /**
     * Constructor with dependency injection
     *
     * @param searchService  Service for search logic
     * @author Yuhao Ma
     */
    @Inject
    public SourceProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    public CompletionStage<Result> view(Http.Request request, String query) {
        if (query.isEmpty()) {
            log.warn("Search attempted with empty query");
            return java.util.concurrent.CompletableFuture.completedFuture(
                    badRequest("Search query is required")
            );
        }
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
