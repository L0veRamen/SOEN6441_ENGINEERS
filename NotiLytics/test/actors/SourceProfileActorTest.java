package actors;

import actors.messages.AnalyzeSource;
import models.Article;
import models.SourceProfile;
import org.mockito.Mockito;
import actors.messages.TaskResult;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import services.ProfileService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for source profile actor.
 * Use Pekko {@link TestKit} to validate actor messaging behavior.
 */
@RunWith(MockitoJUnitRunner.class)
public class SourceProfileActorTest {

    private static ActorSystem system;

    @Mock
    private ProfileService profileService;

    @BeforeClass
    public static void setupSystem() {
        system = ActorSystem.create("profile-actor-test");
    }

    @AfterClass
    public static void tearDownSystem() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @After
    public void resetMocks() {
        reset(profileService);
    }

    @Test
    public void handleSuccess() {
        SourceProfile profile1 = new SourceProfile();
        profile1.id = "src-1";
        profile1.name = "Source One";

        List<Article> articles = List.of(
                new Article("Title 1", "https://example.com/1", "desc one", "src-1", "Source One", "2025-01-01T00:00:00Z"),
                new Article("Title 2", "https://example.com/2", "desc two", "src-2", "Source Two", "2025-01-01T00:05:00Z")
        );

        when(profileService.search(anyString()))
                .thenReturn(CompletableFuture.completedFuture(new ProfileService.SourceProfileResult(profile1, new ArrayList<>())));

        ActorRef actor = system.actorOf(SourceProfileActor.props(profileService));
        TestKit probe = new TestKit(system);

        actor.tell(new AnalyzeSource("src-1"), probe.getRef());

        TaskResult result = probe.expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
        assertEquals("sourceProfile", result.taskType());

        @SuppressWarnings("unchecked")
        ProfileService.SourceProfileResult data = (ProfileService.SourceProfileResult) result.data();
        SourceProfile source = data.source();
        assertEquals("src-1", source.id);

    }

    @Test
    public void handleFailure() {
        SourceProfile profile1 = new SourceProfile();
        profile1.id = "src-1";
        profile1.name = "Source One";

        List<Article> articles = List.of(
                new Article("Title 1", "https://example.com/1", "desc one", "src-1", "Source One", "2025-01-01T00:00:00Z"),
                new Article("Title 2", "https://example.com/2", "desc two", "src-2", "Source Two", "2025-01-01T00:05:00Z")
        );

        when(profileService.search(anyString()))
                .thenReturn(null);

        ActorRef actor = system.actorOf(SourceProfileActor.props(profileService));
        TestKit probe = new TestKit(system);

        actor.tell(new AnalyzeSource("unknown"), probe.getRef());

        TaskResult result = probe.expectMsgClass(Duration.ofSeconds(3), TaskResult.class);
        assertEquals("sourceProfile", result.taskType());

        @SuppressWarnings("unchecked")
        ProfileService.SourceProfileResult data = (ProfileService.SourceProfileResult) result.data();
        SourceProfile source = data.source();
        assertEquals("unknown", source.name);

    }
}

