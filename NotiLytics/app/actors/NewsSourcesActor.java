package actors;

import actors.messages.FetchSources;
import actors.messages.TaskResult;
import models.SourceItem;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.SourcesService;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Task C actor: fetch news sources with optional filters.
 *
 * <p>Receives {@link FetchSources} from {@link actors.UserActor} and responds with
 * a {@link TaskResult} containing the sources list and count suitable for pushing
 * over WebSocket.</p>
 *
 * @author yang
 * @since 2025-11-21 15:15
 * @version 1.0
 */
public class NewsSourcesActor extends AbstractActor {

    private static final Logger log = LoggerFactory.getLogger(NewsSourcesActor.class);

    private final SourcesService sourcesService;

    @Inject
    public NewsSourcesActor(SourcesService sourcesService) {
        this.sourcesService = sourcesService;
    }

    /**
     * Props factory to allow DI-friendly construction.
     *
     * @param sourcesService injected sources service
     * @return Props configured for NewsSourcesActor
     * @author yang
     * @since 2025-11-21 15:15
     */
    public static Props props(SourcesService sourcesService) {
        return Props.create(NewsSourcesActor.class, () -> new NewsSourcesActor(sourcesService));
    }

    /**
     * Handle fetch requests from UserActor.
     *
     * @param message filters for country/category/language
     * @return Receive behavior for this actor
     * @author yang
     * @since 2025-11-21 15:15
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(FetchSources.class, this::onFetchSources)
                .build();
    }

    /**
     * Fetch sources via service, normalize filters, and return TaskResult.
     *
     * @param message filters from client request
     * @author yang
     * @since 2025-11-21 15:15
     */
    private void onFetchSources(FetchSources message) {
        Optional<String> country = optionalLower(message.country());
        Optional<String> category = optionalLower(message.category());
        Optional<String> language = optionalLower(message.language());

        sourcesService.listSources(country, category, language)
                .thenAccept(list -> {
                    log.info("Fetched {} sources (country={}, category={}, language={})",
                            list.size(),
                            message.country(),
                            message.category(),
                            message.language());
                    getSender().tell(TaskResult.sources(payload(list, message)), getSelf());
                })
                .exceptionally(error -> {
                    log.error("Failed to fetch sources: {}", error.getMessage(), error);
                    getSender().tell(TaskResult.sources(errorPayload(message)), getSelf());
                    return null;
                });
    }

    /**
     * Normalize an optional filter value to lower-case.
     *
     * @param value input value (nullable)
     * @return optional lower-cased value or empty
     * @author yang
     * @since 2025-11-21 15:15
     */
    private Optional<String> optionalLower(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.toLowerCase().trim());
    }

    /**
     * Build success payload expected by WebSocket client.
     *
     * @param list    source items
     * @param message original fetch request
     * @return response payload map
     * @author yang
     * @since 2025-11-21 15:15
     */
    private Map<String, Object> payload(List<SourceItem> list, FetchSources message) {
        return Map.of(
                "sources", list,
                "count", list.size(),
                "country", message.country() == null ? "" : message.country(),
                "category", message.category() == null ? "" : message.category(),
                "language", message.language() == null ? "" : message.language()
        );
    }

    /**
     * Build error payload when service call fails.
     *
     * @param message original fetch request
     * @return error payload map
     * @author yang
     * @since 2025-11-21 15:15
     */
    private Map<String, Object> errorPayload(FetchSources message) {
        return Map.of(
                "sources", Collections.emptyList(),
                "count", 0,
                "error", "Unable to load sources",
                "country", message.country() == null ? "" : message.country(),
                "category", message.category() == null ? "" : message.category(),
                "language", message.language() == null ? "" : message.language()
        );
    }
}
