package actors;

import actors.messages.AnalyzeSource;
import actors.messages.TaskResult;
import models.SourceProfile;
import org.apache.pekko.actor.*;
import services.ProfileService;

import javax.inject.Inject;
import java.util.ArrayList;

/**
 * Actor for source profile
 *
 * @author Yuhao Ma
 */
public class SourceProfileActor extends AbstractActor {

    private final ProfileService profileService;

    /**
     * Constructor with dependency injection
     *
     * @author Yuhao Ma
     */
    @Inject
    public SourceProfileActor(ProfileService service) {
        this.profileService = service;
    }

    /**
     * Props method
     *
     * @author Yuhao Ma
     */
    public static Props props(ProfileService service) {
        return Props.create(SourceProfileActor.class, () -> new SourceProfileActor(service));
    }

    /**
     * Define message handling behavior
     *
     * @return Receive builder with message handlers
     * @author Yuhao Ma
     */
    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
                .match(AnalyzeSource.class, this::handleAnalyzeSource)
                .build();
    }

    /**
     * Handle source profile request
     *
     * @author Yuhao Ma
     */
    private void handleAnalyzeSource(AnalyzeSource message) {
        String id = message.sourceName();

        try {
            // Call D1 service (synchronous)
            var profile = profileService.search(id);


            // Send results to UserActor
            profile.thenAccept( p -> {
                var source = p.source();
                if (source == null) {
                    source = new SourceProfile();
                    source.name = id;
                }

                getSender().tell(
                        new TaskResult("sourceProfile", new ProfileService.SourceProfileResult(source, p.articles())),
                        getSelf()
                );
            });

        } catch (Exception error) {
            // Handle error gracefully
            getContext().getSystem().log().error(
                    "Source profile failed: {}", error.getMessage()
            );

            fallback(id);
        }
    }

    /**
     * Send zero scores (fallback)
     * Used when computation fails or articles are empty
     *
     * @author Yuhao Ma
     */
    private void fallback(String id) {
        var data = new SourceProfile();
        data.name = id;

        getSender().tell(
                new TaskResult("sourceProfile", new ProfileService.SourceProfileResult(data, new ArrayList<>())),
                getSelf()
        );
    }
}