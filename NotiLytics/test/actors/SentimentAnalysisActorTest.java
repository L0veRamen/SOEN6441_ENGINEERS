package actors;

import actors.messages.AnalyzeSentiment;
import actors.messages.TaskResult;
import models.Article;
import models.Sentiment;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import services.SentimentAnalysisService;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SentimentAnalysisActor}.
 * Use Pekko {@link TestKit} to validate actor messaging behavior.
 */
@RunWith(MockitoJUnitRunner.class)
public class SentimentAnalysisActorTest {

    private static ActorSystem system;

    @Mock
    private SentimentAnalysisService sentimentService;

    @BeforeClass
    public static void setupSystem() {
        system = ActorSystem.create("sentiment-analysis-actor-test");
    }

    @AfterClass
    public static void tearDownSystem() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @After
    public void resetMocks() {
        reset(sentimentService);
    }

    @Test
    public void handleAnalyzeSentiment_emitsSentimentFromService() {
        List<Article> articles = List.of(
                new Article("Title 1", "https://example.com/1", "Positive content",
                        "src-1", "Source One", "2025-01-01T00:00:00Z"),
                new Article("Title 2", "https://example.com/2", "Neutral content",
                        "src-2", "Source Two", "2025-01-01T00:05:00Z")
        );
        when(sentimentService.analyzeWordList(anyList())).thenReturn(Sentiment.POSITIVE);

        ActorRef actor = system.actorOf(SentimentAnalysisActor.props(sentimentService));
        TestKit probe = new TestKit(system);

        actor.tell(new AnalyzeSentiment(articles), probe.getRef());

        TaskResult result = probe.expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
        assertEquals("sentiment", result.taskType());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();

        // Verify overall sentiment is present
        assertNotNull(data.get("sentiment"));
        assertTrue((Boolean) data.get("isValid"));

        // Verify it's one of the valid sentiment values
        String sentiment = (String) data.get("sentiment");
        assertTrue(sentiment.equals("POSITIVE") || sentiment.equals("NEGATIVE") || sentiment.equals("NEUTRAL"));
    }

    @Test
    public void handleAnalyzeSentiment_emptyArticles_returnsFallback() {
        ActorRef actor = system.actorOf(SentimentAnalysisActor.props(sentimentService));
        TestKit probe = new TestKit(system);

        actor.tell(new AnalyzeSentiment(List.of()), probe.getRef());

        TaskResult result = probe.expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();

        assertEquals(Sentiment.NEUTRAL.toString(), data.get("sentiment"));
        assertFalse((Boolean) data.get("isValid"));
        assertEquals("Failed to compute sentiment", data.get("error"));
    }

    @Test
    public void handleAnalyzeSentiment_nullArticles_returnsFallback() {
        ActorRef actor = system.actorOf(SentimentAnalysisActor.props(sentimentService));
        TestKit probe = new TestKit(system);

        AnalyzeSentiment message = mock(AnalyzeSentiment.class);
        when(message.articles()).thenReturn(null);

        actor.tell(message, probe.getRef());

        TaskResult result = probe.expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();

        assertEquals(Sentiment.NEUTRAL.toString(), data.get("sentiment"));
        assertFalse((Boolean) data.get("isValid"));
        assertEquals("Failed to compute sentiment", data.get("error"));
        verifyNoInteractions(sentimentService);
    }

    @Test
    public void handleAnalyzeSentiment_serviceFailure_returnsFallback() {
        List<Article> articles = List.of(
                new Article("Title 1", "https://example.com/1", "Content",
                        "src-1", "Source One", "2025-01-01T00:00:00Z")
        );
        when(sentimentService.analyzeWordList(anyList()))
                .thenThrow(new RuntimeException("boom"));

        ActorRef actor = system.actorOf(SentimentAnalysisActor.props(sentimentService));
        TestKit probe = new TestKit(system);

        actor.tell(new AnalyzeSentiment(articles), probe.getRef());

        TaskResult result = probe.expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();

        assertEquals(Sentiment.NEUTRAL.toString(), data.get("sentiment"));
        assertFalse((Boolean) data.get("isValid"));
        assertEquals("Failed to compute sentiment", data.get("error"));
    }

    @Test
    public void handleAnalyzeSentiment_nullSentimentFromService_returnsFallback() {
        List<Article> articles = List.of(
                new Article("Title 1", "https://example.com/1", "Content",
                        "src-1", "Source One", "2025-01-01T00:00:00Z")
        );
        when(sentimentService.analyzeWordList(anyList())).thenReturn(null);

        ActorRef actor = system.actorOf(SentimentAnalysisActor.props(sentimentService));
        TestKit probe = new TestKit(system);

        actor.tell(new AnalyzeSentiment(articles), probe.getRef());

        TaskResult result = probe.expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();

        assertEquals(Sentiment.NEUTRAL.toString(), data.get("sentiment"));
        assertFalse((Boolean) data.get("isValid"));
        assertEquals("Failed to compute sentiment", data.get("error"));
    }
}
