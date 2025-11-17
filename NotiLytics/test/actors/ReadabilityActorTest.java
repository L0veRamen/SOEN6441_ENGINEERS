package actors;

import actors.messages.AnalyzeReadability;
import org.mockito.Mockito;
import actors.messages.TaskResult;
import models.Article;
import models.ReadabilityScores;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import services.ReadabilityService;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReadabilityActor}.
 * Use Pekko {@link TestKit} to validate actor messaging behavior.
 */
@RunWith(MockitoJUnitRunner.class)
public class ReadabilityActorTest {

    private static ActorSystem system;

    @Mock
    private ReadabilityService readabilityService;

    @BeforeClass
    public static void setupSystem() {
        system = ActorSystem.create("readability-actor-test");
    }

    @AfterClass
    public static void tearDownSystem() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @After
    public void resetMocks() {
        reset(readabilityService);
    }

    @Test
    public void handleAnalyzeReadability_emitsScoresFromService() {
        List<Article> articles = List.of(
                new Article("Title 1", "https://example.com/1", "desc one", "src-1", "Source One", "2025-01-01T00:00:00Z"),
                new Article("Title 2", "https://example.com/2", "desc two", "src-2", "Source Two", "2025-01-01T00:05:00Z")
        );
        ReadabilityScores average = new ReadabilityScores(8.4, 66.0);
        ReadabilityScores perArticleScore = new ReadabilityScores(6.0, 70.0);

        when(readabilityService.calculateAverageReadability(anyList())).thenReturn(average);
        when(readabilityService.calculateArticleReadability(any(Article.class))).thenReturn(perArticleScore);

        ActorRef actor = system.actorOf(ReadabilityActor.props(readabilityService));
        TestKit probe = new TestKit(system);

        actor.tell(new AnalyzeReadability(articles), probe.getRef());

        TaskResult result = probe.expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
        assertEquals("readability", result.taskType());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();

        assertEquals(average.gradeLevel(), ((Number) data.get("gradeLevel")).doubleValue(), 0.0);
        assertEquals(average.readingEase(), ((Number) data.get("readingEase")).doubleValue(), 0.0);
        assertEquals(average.getReadingEaseInterpretation(), data.get("interpretation"));
        assertEquals(articles.size(), ((Number) data.get("articleCount")).intValue());
        assertTrue((Boolean) data.get("isValid"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> articleScores =
                (List<Map<String, Object>>) data.get("articleScores");
        assertEquals(articles.size(), articleScores.size());
        assertEquals(perArticleScore.readingEase(),
                ((Number) articleScores.get(0).get("readingEase")).doubleValue(), 0.0);

        verify(readabilityService).calculateAverageReadability(anyList());
        verify(readabilityService, times(articles.size())).calculateArticleReadability(any(Article.class));
    }

    @Test
    public void handleAnalyzeReadability_emptyArticles_returnsZeroScores() {
        ActorRef actor = system.actorOf(ReadabilityActor.props(readabilityService));
        TestKit probe = new TestKit(system);

        actor.tell(new AnalyzeReadability(List.of()), probe.getRef());

        TaskResult result = probe.expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
        assertEquals("readability", result.taskType());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals(0.0, ((Number) data.get("gradeLevel")).doubleValue(), 0.0);
        assertEquals(0.0, ((Number) data.get("readingEase")).doubleValue(), 0.0);
        assertEquals(0, ((Number) data.get("articleCount")).intValue());
        assertEquals("Failed to compute readability", data.get("error"));
        assertFalse((Boolean) data.get("isValid"));

        verifyNoInteractions(readabilityService);
    }

    @Test
    public void handleAnalyzeReadability_serviceFailure_returnsFallback() {
        List<Article> articles = List.of(
                new Article("Title 1", "https://example.com/1", "desc one", "src-1", "Source One", "2025-01-01T00:00:00Z")
        );

        when(readabilityService.calculateAverageReadability(anyList()))
                .thenThrow(new RuntimeException("boom"));

        ActorRef actor = system.actorOf(ReadabilityActor.props(readabilityService));
        TestKit probe = new TestKit(system);

        actor.tell(new AnalyzeReadability(articles), probe.getRef());

        TaskResult result = probe.expectMsgClass(Duration.ofSeconds(3), TaskResult.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals(0.0, ((Number) data.get("gradeLevel")).doubleValue(), 0.0);
        assertEquals(0.0, ((Number) data.get("readingEase")).doubleValue(), 0.0);
        assertEquals("Failed to compute readability", data.get("error"));
        assertFalse((Boolean) data.get("isValid"));

        verify(readabilityService).calculateAverageReadability(anyList());
        verify(readabilityService, never()).calculateArticleReadability(any(Article.class));
    }

    @Test
    public void handleAnalyzeReadability_handlesNullList() {
        ActorRef actor = system.actorOf(ReadabilityActor.props(readabilityService));
        TestKit probe = new TestKit(system);

        AnalyzeReadability message = Mockito.mock(AnalyzeReadability.class);
        when(message.articles()).thenReturn(null);

        actor.tell(message, probe.getRef());

        TaskResult result = probe.expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals(0.0, ((Number) data.get("gradeLevel")).doubleValue(), 0.0);
    }
}
