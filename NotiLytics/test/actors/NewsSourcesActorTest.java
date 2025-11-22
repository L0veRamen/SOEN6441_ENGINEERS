package actors;

import actors.messages.FetchSources;
import actors.messages.TaskResult;
import models.SourceItem;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.testkit.TestActorRef;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import services.SourcesService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Method;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class NewsSourcesActorTest {

    private static ActorSystem system;

    @Mock
    private SourcesService sourcesService;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create("news-sources-actor-test");
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void fetchSourcesReturnsListAndPreservesFilters() {
        SourceItem s1 = new SourceItem("id1", "Name1", "d1", "https://n1", "Business", "EN", "US");
        SourceItem s2 = new SourceItem("id2", "Name2", "d2", "https://n2", "Business", "EN", "CA");

        when(sourcesService.listSources(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(s1, s2)));

        new TestKit(system) {{
            ActorRef actor = system.actorOf(NewsSourcesActor.props(sourcesService));

            actor.tell(new FetchSources("US", "BUSINESS", "En"), getRef());

            TaskResult result = expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
            assertEquals("sources", result.taskType());

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.data();
            assertEquals(2, data.get("count"));
            assertEquals("US", data.get("country"));
            assertEquals("BUSINESS", data.get("category"));
            assertEquals("En", data.get("language"));

            @SuppressWarnings("unchecked")
            List<SourceItem> returned = (List<SourceItem>) data.get("sources");
            assertEquals(2, returned.size());

            ArgumentCaptor<Optional<String>> countryCaptor = ArgumentCaptor.forClass((Class) Optional.class);
            ArgumentCaptor<Optional<String>> categoryCaptor = ArgumentCaptor.forClass((Class) Optional.class);
            ArgumentCaptor<Optional<String>> languageCaptor = ArgumentCaptor.forClass((Class) Optional.class);

            verify(sourcesService).listSources(countryCaptor.capture(), categoryCaptor.capture(), languageCaptor.capture());
            assertEquals(Optional.of("us"), countryCaptor.getValue());
            assertEquals(Optional.of("business"), categoryCaptor.getValue());
            assertEquals(Optional.of("en"), languageCaptor.getValue());
        }};
    }

    @Test
    public void fetchSourcesHandlesFailuresGracefully() {
        when(sourcesService.listSources(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        new TestKit(system) {{
            ActorRef actor = system.actorOf(NewsSourcesActor.props(sourcesService));

            actor.tell(new FetchSources(null, null, null), getRef());

            TaskResult result = expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
            assertEquals("sources", result.taskType());

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.data();
            assertEquals(0, data.get("count"));
            assertTrue("should include error key", data.containsKey("error"));
            assertTrue(((List<?>) data.get("sources")).isEmpty());
            assertEquals("", data.get("country"));
            assertEquals("", data.get("category"));
            assertEquals("", data.get("language"));
        }};
    }

    @Test
    public void optionalLowerHandlesNullAndBlank() throws Exception {
        Method m = NewsSourcesActor.class.getDeclaredMethod("optionalLower", String.class);
        m.setAccessible(true);
        TestActorRef<NewsSourcesActor> ref = TestActorRef.create(system, NewsSourcesActor.props(sourcesService));
        NewsSourcesActor actor = ref.underlyingActor();

        Optional<?> nullResult = (Optional<?>) m.invoke(actor, (Object) null);
        Optional<?> blankResult = (Optional<?>) m.invoke(actor, " ");
        Optional<?> normalResult = (Optional<?>) m.invoke(actor, "En");

        assertTrue(nullResult.isEmpty());
        assertTrue(blankResult.isEmpty());
        assertEquals("en", normalResult.orElseThrow());
    }

    @Test
    public void payloadAndErrorPayloadNormalizeFields() throws Exception {
        Method payload = NewsSourcesActor.class.getDeclaredMethod("payload", List.class, actors.messages.FetchSources.class);
        Method errorPayload = NewsSourcesActor.class.getDeclaredMethod("errorPayload", actors.messages.FetchSources.class);
        payload.setAccessible(true);
        errorPayload.setAccessible(true);

        FetchSources request = new FetchSources("", null, "EN");
        FetchSources request2 = new FetchSources(null, "Sports", null);
        SourceItem s = new SourceItem(null, "Name", null, null, null, null, null);
        TestActorRef<NewsSourcesActor> ref = TestActorRef.create(system, NewsSourcesActor.props(sourcesService));
        NewsSourcesActor actor = ref.underlyingActor();

        @SuppressWarnings("unchecked")
        Map<String, Object> ok = (Map<String, Object>) payload.invoke(actor, List.of(s), request);
        assertEquals(1, ok.get("count"));
        assertEquals("", ok.get("country"));
        assertEquals("", ok.get("category"));
        assertEquals("EN", ok.get("language"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ok2 = (Map<String, Object>) payload.invoke(actor, List.of(), request2);
        assertEquals(0, ok2.get("count"));
        assertEquals("", ok2.get("country"));
        assertEquals("Sports", ok2.get("category"));
        assertEquals("", ok2.get("language"));

        @SuppressWarnings("unchecked")
        Map<String, Object> err = (Map<String, Object>) errorPayload.invoke(actor, request);
        assertEquals(0, err.get("count"));
        assertTrue(((List<?>) err.get("sources")).isEmpty());
        assertEquals("", err.get("country"));
        assertEquals("", err.get("category"));
        assertEquals("EN", err.get("language"));

        @SuppressWarnings("unchecked")
        Map<String, Object> err2 = (Map<String, Object>) errorPayload.invoke(actor, request2);
        assertEquals(0, err2.get("count"));
        assertTrue(((List<?>) err2.get("sources")).isEmpty());
        assertEquals("", err2.get("country"));
        assertEquals("Sports", err2.get("category"));
        assertEquals("", err2.get("language"));
    }
}
