package services;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.Article;
import models.ReadabilityScores;
import models.SearchBlock;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class SearchHistoryServiceTest {

    private SearchHistoryService service;

    @Before
    public void setUp() {
        Config config = ConfigFactory.parseString("""
                cache.ttl.session = 30 minutes
                cache.maxSessions = 100
                """);
        service = new SearchHistoryService(config);
    }

    private SearchBlock block(String suffix) {
        return new SearchBlock(
                "query-" + suffix,
                "publishedAt",
                1,
                List.of(new Article(
                        "Title " + suffix,
                        "https://example.com/" + suffix,
                        "desc",
                        "source-id",
                        "Source",
                        "2024-01-01T00:00:00Z")),
                "2024-01-01T00:00:00Z",
                new ReadabilityScores(8.5, 65.0),
                List.of(new ReadabilityScores(8.0, 66.0)));
    }

    @Test
    public void pushAddsEntriesAndMaintainsOrder() {
        service.push("session", block("first"));
        service.push("session", block("second"));

        List<SearchBlock> history = service.list("session");
        assertEquals(2, history.size());
        assertEquals("query-second", history.get(0).query());
        assertEquals("query-first", history.get(1).query());
    }

    @Test
    public void pushLimitsToTenEntries() {
        for (int i = 0; i < 12; i++) {
            service.push("session", block(String.valueOf(i)));
        }

        List<SearchBlock> history = service.list("session");
        assertEquals(10, history.size());
        assertEquals("query-11", history.get(0).query());
        assertEquals("query-2", history.get(9).query());
    }

    @Test
    public void pushValidatesInputs() {
        service.push(null, block("x"));
        service.push("", block("y"));
        service.push("valid", null);

        assertTrue(service.list("valid").isEmpty());
    }

    @Test
    public void listHandlesMissingSessionAndEmptyCache() {
        assertTrue(service.list(null).isEmpty());
        assertTrue(service.list("").isEmpty());
        assertTrue(service.list("missing").isEmpty());

        service.push("session", block("a"));
        service.clear("session");
        assertTrue(service.list("session").isEmpty());
    }

    @Test
    public void clearAndActiveSessionCount() {
        service.push("sessionA", block("A"));
        service.push("sessionB", block("B"));

        long active = service.getActiveSessionCount();
        assertTrue("Expected active sessions >= 2 but was " + active, active >= 2);

        service.clear("sessionA");
        assertTrue(service.list("sessionA").isEmpty());

        service.clear(null);
        service.clear("");
    }
}
