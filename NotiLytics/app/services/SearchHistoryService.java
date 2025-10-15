package services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.Config;
import models.SearchBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;

/**
 * Manages per-session search history
 * Uses Caffeine cache for automatic session expiration
 *
 * Storage:
 * - Key: sessionId (String)
 * - Value: ArrayDeque&lt;SearchBlock&gt; (max 10, FIFO)
 *
 * Configuration:
 * - TTL: 2 hours (idle sessions auto-expire)
 * - Max sessions: 10,000 (configurable)
 *
 * Thread-safety: Caffeine cache is thread-safe
 *
 * @author Chen Qian
 */
@Singleton
public class SearchHistoryService {

    private static final Logger log = LoggerFactory.getLogger(SearchHistoryService.class);
    private static final int MAX_SEARCHES_PER_SESSION = 10;

    private final Cache<String, ArrayDeque<SearchBlock>> cache;

    /**
     * Constructor with dependency injection
     *
     * @param config Application configuration
     * @author Chen Qian
     */
    @Inject
    public SearchHistoryService(Config config) {
        // Configure cache with TTL from config or default to 2 hours
        Duration sessionTTL = config.hasPath("cache.ttl.session")
                ? config.getDuration("cache.ttl.session")
                : Duration.ofHours(2);

        int maxSessions = config.hasPath("cache.maxSessions")
                ? config.getInt("cache.maxSessions")
                : 10000;

        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(sessionTTL)
                .maximumSize(maxSessions)
                .build();

        log.info("SearchHistoryService configured sessionTTL={} maxSessions={}",
                sessionTTL, maxSessions);
    }

    /**
     * Add a search block to session history
     * Maintains max 10 searches (FIFO - oldest removed first)
     * Thread-safe operation using Caffeine's atomic compute
     *
     * @param sessionId   Session identifier
     * @param searchBlock Search result to add
     * @author Chen Qian
     */
    public void push(String sessionId, SearchBlock searchBlock) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Attempted to push to null/empty sessionId");
            return;
        }

        if (searchBlock == null) {
            log.warn("Attempted to push null searchBlock");
            return;
        }

        cache.asMap().compute(sessionId, (key, deque) -> {
            if (deque == null) {
                deque = new ArrayDeque<>();
            }

            // Add to front (newest first)
            deque.addFirst(searchBlock);

            // Remove oldest if exceeds max
            if (deque.size() > MAX_SEARCHES_PER_SESSION) {
                deque.removeLast();
                log.debug("Removed oldest search for session '{}' (max {} reached)",
                        sessionId, MAX_SEARCHES_PER_SESSION);
            }

            return deque;
        });

        log.info("Pushed search to session '{}': query='{}' totalResults={}",
                sessionId, searchBlock.query(), searchBlock.totalResults());
    }

    /**
     * Get all search blocks for a session
     * Returns list ordered by recency (newest first)
     *
     * @param sessionId Session identifier
     * @return Immutable list of search blocks (empty if none)
     * @author Chen Qian
     */
    public List<SearchBlock> list(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }

        ArrayDeque<SearchBlock> deque = cache.getIfPresent(sessionId);

        if (deque == null || deque.isEmpty()) {
            log.debug("No history for session '{}'", sessionId);
            return Collections.emptyList();
        }

        // Return immutable copy
        return List.copyOf(deque);
    }

    /**
     * Clear history for a session
     * Useful for testing or explicit user action
     *
     * @param sessionId Session identifier
     * @author Chen Qian
     */
    public void clear(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Attempted to clear null/empty sessionId");
            return;
        }

        cache.invalidate(sessionId);
        log.info("Cleared history for session '{}'", sessionId);
    }

    /**
     * Get the number of active sessions
     * Useful for monitoring and testing
     *
     * @return Number of cached sessions
     * @author Chen Qian
     */
    public long getActiveSessionCount() {
        return cache.estimatedSize();
    }
}
