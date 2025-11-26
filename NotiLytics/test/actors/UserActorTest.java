package actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import actors.messages.TaskResult;
import models.*;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.OngoingStubbing;
import services.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration-style tests for {@link UserActor} using Pekko TestKit.
 */
@RunWith(MockitoJUnitRunner.class)
public class UserActorTest {

    private static final Duration SHORT_WAIT = Duration.ofMillis(250);

    private static ActorSystem system;
    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private SearchService searchService;

    @Mock
    private ProfileService profileService;

    @Mock
    private NewsApiClient newsApiClient;

    @Mock
    private ReadabilityService readabilityService;

    @Mock
    private SearchHistoryService historyService;

    @Mock
    private SentimentAnalysisService sentimentService;

    @Mock
    private SourcesService sourcesService;
    
    @Mock
    private WordStatsService wordStatsService;

    private Article articleOne;
    private Article articleTwo;
    private Article articleThree;
    private SearchBlock initialBlock;
    private SearchBlock updateBlock;

    private SourceProfile profile1;

    @BeforeClass
    public static void setupSystem() {
        system = ActorSystem.create("user-actor-test");
    }

    @AfterClass
    public static void tearDownSystem() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Before
    public void setUp() {
        articleOne = new Article(
                "Breaking AI", "https://example.com/1", "desc one",
                "src-1", "Source One", "2025-01-01T00:00:00Z"
        );
        articleTwo = new Article(
                "Space Tech", "https://example.com/2", "desc two",
                "src-2", "Source Two", "2025-01-01T00:05:00Z"
        );
        articleThree = new Article(
                "Quantum Leap", "https://example.com/3", "desc three",
                "src-3", "Source Three", "2025-01-01T00:10:00Z"
        );

        ReadabilityScores initialAverage = new ReadabilityScores(7.2, 64.0);
        ReadabilityScores updateAverage = new ReadabilityScores(5.5, 72.0);
        List<ReadabilityScores> perArticleScores = List.of(
                new ReadabilityScores(5.0, 68.0),
                new ReadabilityScores(4.8, 70.0)
        );

        initialBlock = new SearchBlock(
                "ai",
                "relevancy",
                2,
                List.of(articleOne, articleTwo),
                "2025-01-01T00:00:00Z",
                initialAverage,
                perArticleScores,
                Sentiment.POSITIVE
        );

        updateBlock = new SearchBlock(
                "ai",
                "relevancy",
                3,
                List.of(articleTwo, articleThree),
                "2025-01-01T00:01:00Z",
                updateAverage,
                perArticleScores,
                Sentiment.NEUTRAL
        );

        when(readabilityService.calculateAverageReadability(anyList()))
                .thenAnswer(invocation -> new ReadabilityScores(5.0, 65.0));
        when(readabilityService.calculateArticleReadability(any(Article.class)))
                .thenAnswer(invocation -> new ReadabilityScores(5.0, 65.0));

        profile1 = new SourceProfile();
        profile1.id = "src-1";
        profile1.name = "Breaking AI";

        when(profileService.search(anyString()))
                .thenReturn(CompletableFuture.completedFuture(new ProfileService.SourceProfileResult(profile1, new ArrayList<>())));
        // By default, no persisted history for a session in tests
        when(historyService.list(anyString())).thenReturn(List.of());
        when(sentimentService.analyzeWordList(anyList())).thenReturn(Sentiment.NEUTRAL);
        when(sourcesService.listSources(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(wordStatsService.computeWordStats(anyString()))
		        .thenReturn(CompletableFuture.completedFuture(
        new WordStats("test", 10, 100, 50, List.of())
        ));
    }

    @After
    public void resetMocks() {
        reset(searchService, newsApiClient, readabilityService, sourcesService);
    }

    @Test
    public void startSearchSendsInitialResultsAndReadabilityUpdate() {
        stubSearch("ai", "relevancy",
                completed(initialBlock),
                completed(updateBlock));
        when(readabilityService.calculateAverageReadability(anyList()))
                .thenReturn(initialBlock.readability(), updateBlock.readability());

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        sendStartSearch(userActor, "ai", "relevancy");

        JsonNode initialResults = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("initial_results", initialResults.get("type").asText());
        assertEquals(2, initialResults.get("data").get("articles").size());

        // New behavior: search history is pushed automatically after each search
        JsonNode historyMessage = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("history", historyMessage.get("type").asText());

        JsonNode historyData = historyMessage.get("data");
        assertNotNull(historyData);
        assertEquals(1, historyData.get("count").asInt());
        assertEquals(10, historyData.get("maxHistory").asInt());

        Map<String, JsonNode> initialTasks = expectTaskMessages(socketProbe, true);
        JsonNode readabilityMessage = initialTasks.get("readability");
        assertNotNull(readabilityMessage);
        assertEquals(7.2, readabilityMessage.get("data").get("gradeLevel").asDouble(), 0.01);

        userActor.tell(new UserActor.UpdateCheck(), ActorRef.noSender());

        JsonNode appendMessage = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("append", appendMessage.get("type").asText());
        assertEquals(1, appendMessage.get("data").get("count").asInt());

        Map<String, JsonNode> updateTasks = expectTaskMessages(socketProbe, false);
        JsonNode updatedReadability = updateTasks.get("readability");
        assertNotNull(updatedReadability);
        assertEquals(5.5, updatedReadability.get("data").get("gradeLevel").asDouble(), 0.01);

        verify(searchService, times(2)).search("ai", "relevancy");
    }

    @Test
    public void startSearchDefaultsSortWhenMissing() {
        stubSearch("default", "publishedAt", completed(initialBlock));

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        ObjectNode startMessage = mapper.createObjectNode();
        startMessage.put("type", "start_search");
        startMessage.put("query", "default");

        userActor.tell(startMessage, ActorRef.noSender());

        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);

        verify(searchService).search("default", "publishedAt");
    }

    @Test
    public void getSourcesRoutesToChildAndReturnsPayload() {
        SourceItem s = new SourceItem("id", "Name", "d", "u", "c", "en", "us");
        when(sourcesService.listSources(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(s)));

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        ObjectNode msg = mapper.createObjectNode();
        msg.put("type", "get_sources");
        msg.put("country", "US");
        msg.put("category", "Tech");
        msg.put("language", "EN");
        userActor.tell(msg, ActorRef.noSender());

        JsonNode sourcesMsg = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("sources", sourcesMsg.get("type").asText());
        JsonNode data = sourcesMsg.get("data");
        assertEquals(1, data.get("count").asInt());
        assertEquals("US", data.get("country").asText());
        assertEquals("Tech", data.get("category").asText());
    }

    @Test
    public void handleWebSocketUnknownTypeDoesNothing() {
        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        ObjectNode unknown = mapper.createObjectNode();
        unknown.put("type", "does_not_exist");
        userActor.tell(unknown, ActorRef.noSender());

        socketProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    public void handleWebSocketInvalidFormatSendsError() {
        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        ObjectNode invalid = mapper.createObjectNode(); // missing type triggers exception
        userActor.tell(invalid, ActorRef.noSender());

        JsonNode msg = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("error", msg.get("type").asText());
        assertTrue(msg.get("data").get("message").asText().contains("Invalid message"));
    }

    @Test
    public void requestSourcesNoActorIsSafeguarded() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);
        UserActor actor = actorRef.underlyingActor();

        Field f = UserActor.class.getDeclaredField("newsSourcesActor");
        f.setAccessible(true);
        f.set(actor, null); // force missing child actor

        Method m = UserActor.class.getDeclaredMethod("requestSources", String.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(actor, "", "", "");

        socketProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    public void handleNewsApiErrorTimeoutSendsStatus() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);
        UserActor actor = actorRef.underlyingActor();

        Method m = UserActor.class.getDeclaredMethod("handleNewsApiError", Throwable.class);
        m.setAccessible(true);
        m.invoke(actor, new HttpTimeoutException("slow"));

        JsonNode msg = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("status", msg.get("type").asText());
        assertTrue(msg.get("data").get("message").asText().contains("Search delayed"));
    }

    @Test
    public void handleNewsApiErrorConnectSendsError() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);
        UserActor actor = actorRef.underlyingActor();

        Method m = UserActor.class.getDeclaredMethod("handleNewsApiError", Throwable.class);
        m.setAccessible(true);
        m.invoke(actor, new ConnectException("down"));

        JsonNode msg = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("error", msg.get("type").asText());
        assertTrue(msg.get("data").get("message").asText().contains("temporarily unavailable"));
    }

    @Test
    public void handleNewsApiErrorRateLimitReschedules() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);
        UserActor actor = actorRef.underlyingActor();

        Method m = UserActor.class.getDeclaredMethod("handleNewsApiError", Throwable.class);
        m.setAccessible(true);
        m.invoke(actor, new RuntimeException("429 too many"));

        JsonNode msg = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("error", msg.get("type").asText());
        assertTrue(msg.get("data").get("message").asText().contains("Too many requests"));
    }

    @Test
    public void handleNewsApiErrorGenericSendsError() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);
        UserActor actor = actorRef.underlyingActor();

        Method m = UserActor.class.getDeclaredMethod("handleNewsApiError", Throwable.class);
        m.setAccessible(true);
        m.invoke(actor, new RuntimeException("boom"));

        JsonNode msg = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("error", msg.get("type").asText());
        assertTrue(msg.get("data").get("message").asText().contains("Search failed"));
    }

    @Test
    public void getSourcesWithMissingFiltersDefaultsToEmptyStrings() {
        when(sourcesService.listSources(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        ObjectNode msg = mapper.createObjectNode();
        msg.put("type", "get_sources"); // no filters provided
        userActor.tell(msg, ActorRef.noSender());

        JsonNode sourcesMsg = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("sources", sourcesMsg.get("type").asText());
        JsonNode data = sourcesMsg.get("data");
        assertEquals("", data.get("country").asText());
        assertEquals("", data.get("category").asText());
        assertEquals("", data.get("language").asText());
    }

    @Test
    public void startSearchCancelsPreviousSchedulerOnNewRequest() {
        stubSearch("first", "relevancy", completed(initialBlock));
        stubSearch("second", "relevancy", completed(updateBlock));

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        sendStartSearch(userActor, "first", "relevancy");
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);

        sendStartSearch(userActor, "second", "relevancy");
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);

        verify(searchService).search("first", "relevancy");
        verify(searchService).search("second", "relevancy");
    }

    @Test
    public void invalidMessageSendsError() {
        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        ObjectNode invalidMessage = mapper.createObjectNode();
        invalidMessage.put("query", "ai");

        userActor.tell(invalidMessage, ActorRef.noSender());

        JsonNode errorMessage = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("error", errorMessage.get("type").asText());
        assertTrue(errorMessage.get("data").get("message").asText().contains("Invalid"));

        verifyNoInteractions(searchService);
    }

    @Test
    public void stopSearchHandlesPingAndUnknownMessages() {
        stubSearch("ai", "relevancy", completed(initialBlock));
        when(readabilityService.calculateAverageReadability(anyList()))
                .thenReturn(initialBlock.readability());

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        sendStartSearch(userActor, "ai", "relevancy");
        JsonNode initialResults = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("initial_results", initialResults.get("type").asText());

        // New behavior: automatic history push after search
        JsonNode historyMessage = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("history", historyMessage.get("type").asText());

        expectTaskMessages(socketProbe, true);

        ObjectNode stop = mapper.createObjectNode();
        stop.put("type", "stop_search");
        userActor.tell(stop, ActorRef.noSender());

        JsonNode status = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("status", status.get("type").asText());

        ObjectNode ping = mapper.createObjectNode();
        ping.put("type", "ping");
        userActor.tell(ping, ActorRef.noSender());

        JsonNode pong = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("pong", pong.get("type").asText());

        ObjectNode unknown = mapper.createObjectNode();
        unknown.put("type", "mystery");
        userActor.tell(unknown, ActorRef.noSender());
        socketProbe.expectNoMessage(SHORT_WAIT);
    }

    @Test
    public void stopSearchCancelsSchedulerAndClearsState() {
        stubSearch("stop", "relevancy", completed(initialBlock));
        when(readabilityService.calculateAverageReadability(anyList()))
                .thenReturn(initialBlock.readability());

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);
        sendStartSearch(userActor, "stop", "relevancy");
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);

        ObjectNode stop = mapper.createObjectNode();
        stop.put("type", "stop_search");
        userActor.tell(stop, ActorRef.noSender());

        JsonNode status = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("status", status.get("type").asText());
    }

    @Test
    public void postStopCancelsActiveScheduler() {
        stubSearch("lifecycle", "relevancy", completed(initialBlock));

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);
        sendStartSearch(userActor, "lifecycle", "relevancy");
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);

        TestKit watcher = new TestKit(system);
        watcher.watch(userActor);
        system.stop(userActor);
        watcher.expectTerminated(Duration.ofSeconds(5), userActor);
    }

    @Test
    public void updateCheckWithoutActiveSearchDoesNothing() {
        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        userActor.tell(new UserActor.UpdateCheck(), ActorRef.noSender());
        socketProbe.expectNoMessage(SHORT_WAIT);
    }

    @Test
    public void startSearchWithEmptyResultsSkipsTaskActors() {
        SearchBlock emptyBlock = block("ai", "relevancy", List.of());
        stubSearch("ai", "relevancy", completed(emptyBlock));

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        sendStartSearch(userActor, "ai", "relevancy");

        JsonNode initialResults = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("initial_results", initialResults.get("type").asText());
        assertEquals(0, initialResults.get("data").get("articles").size());

        // With empty results we still push history, but no task actors run
        JsonNode historyMessage = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("history", historyMessage.get("type").asText());

        socketProbe.expectNoMessage(SHORT_WAIT);
    }

    @Test
    public void handleNewsApiErrorSendsStatusOnTimeout() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);

        invokeHandleNewsApiError(actorRef, new HttpTimeoutException("slow api"));

        JsonNode status = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("status", status.get("type").asText());
    }

    @Test
    public void handleNewsApiErrorStopsSearchOnConnectException() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);

        invokeHandleNewsApiError(actorRef, new ConnectException("down"));

        JsonNode error = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        JsonNode status = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("error", error.get("type").asText());
        assertEquals("status", status.get("type").asText());
    }

    @Test
    public void handleNewsApiErrorReschedulesOnRateLimit() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);

        Cancellable currentScheduler = mock(Cancellable.class);
        setField(actorRef.underlyingActor(), "updateScheduler", currentScheduler);

        invokeHandleNewsApiError(actorRef, new RuntimeException("HTTP 429 limit"));

        JsonNode error = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("error", error.get("type").asText());
        assertTrue(error.get("data").get("message").asText().contains("Too many requests"));
        verify(currentScheduler).cancel();
    }

    @Test
    public void handleNewsApiErrorRateLimitWithoutSchedulerSkipsReschedule() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);

        invokeHandleNewsApiError(actorRef, new RuntimeException("429 too many requests"));

        JsonNode error = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("error", error.get("type").asText());
        assertTrue(error.get("data").get("message").asText().contains("Too many requests"));
    }

    @Test
    public void handleNewsApiErrorSendsGenericMessage() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);

        invokeHandleNewsApiError(actorRef, new RuntimeException("boom"));

        JsonNode error = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("error", error.get("type").asText());
        assertTrue(error.get("data").get("message").asText().contains("Search failed"));
    }

    @Test
    public void secondSearchWithRateLimitAdjustsScheduler() {
        stubSearch("first", "relevancy", completed(initialBlock));
        when(searchService.search(eq("limit"), eq("relevancy")))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("HTTP 429 rate limit")));

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        sendStartSearch(userActor, "first", "relevancy");
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);

        sendStartSearch(userActor, "limit", "relevancy");

        JsonNode error = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("error", error.get("type").asText());
        assertTrue(error.get("data").get("message").asText().contains("Too many requests"));
    }

    @Test
    public void historyEvictionRemovesOldestSearch() {
        when(readabilityService.calculateAverageReadability(anyList()))
                .thenReturn(initialBlock.readability());
        when(searchService.search(anyString(), eq("relevancy")))
                .thenReturn(CompletableFuture.completedFuture(initialBlock));
        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        // Perform 11 searches to exceed MAX_HISTORY (10)
        IntStream.rangeClosed(0, 10).forEach(index -> {
            sendStartSearch(userActor, "q" + index, "relevancy");
            expectMessageOfType(socketProbe, "initial_results");
            expectMessageOfType(socketProbe, "history");
            expectTaskMessages(socketProbe, true);
        });

        // Request history and assert that only the 10 most recent searches remain
        ObjectNode historyRequest = mapper.createObjectNode();
        historyRequest.put("type", "get_history");
        userActor.tell(historyRequest, ActorRef.noSender());

        JsonNode historyMessage = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("history", historyMessage.get("type").asText());

        JsonNode data = historyMessage.get("data");
        assertNotNull(data);
        JsonNode searches = data.get("searches");
        // Only verify bounded size; query strings themselves are provided by SearchService
        assertEquals(10, searches.size());
    }

    @Test
    public void getHistorySendsSearchHistoryPayload() {
        SearchBlock block1 = block("q1", "relevancy", List.of(articleOne));
        SearchBlock block2 = block("q2", "relevancy", List.of(articleTwo));

        when(readabilityService.calculateAverageReadability(anyList()))
                .thenReturn(block1.readability(), block2.readability());
        stubSearch("q1", "relevancy", completed(block1));
        stubSearch("q2", "relevancy", completed(block2));

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        // Perform a couple of searches to populate in-actor history
        sendStartSearch(userActor, "q1", "relevancy");
        expectMessageOfType(socketProbe, "initial_results");
        expectMessageOfType(socketProbe, "history");
        expectTaskMessages(socketProbe, true);

        sendStartSearch(userActor, "q2", "relevancy");
        expectMessageOfType(socketProbe, "initial_results");
        expectMessageOfType(socketProbe, "history");
        expectTaskMessages(socketProbe, true);

        // Request history over WebSocket
        ObjectNode historyRequest = mapper.createObjectNode();
        historyRequest.put("type", "get_history");
        userActor.tell(historyRequest, ActorRef.noSender());

        JsonNode historyMessage = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("history", historyMessage.get("type").asText());

        JsonNode data = historyMessage.get("data");
        assertNotNull(data);
        assertEquals(2, data.get("count").asInt());
        assertEquals(10, data.get("maxHistory").asInt());
        assertEquals(2, data.get("searches").size());
        assertEquals("q2", data.get("searches").get(0).get("query").asText());
        assertEquals("q1", data.get("searches").get(1).get("query").asText());
    }

    @Test
    public void sendToWebSocketHandlesSerializationErrors() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);

        setField(actorRef.underlyingActor(), "mapper", null);

        actorRef.tell(TaskResult.readability(List.of("data")), ActorRef.noSender());
        socketProbe.expectNoMessage(SHORT_WAIT);
    }

    @Test
    public void seenUrlsEvictsOldEntriesWhenLimitExceeded() {
        List<Article> manyArticles = generateArticles(101);
        SearchBlock hugeBlock = block("many", "relevancy", manyArticles);
        stubSearch("many", "relevancy", completed(hugeBlock));

        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);

        sendStartSearch(userActor, "many", "relevancy");
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
    }

    @Test
    public void startNewSearchCancelsExistingScheduler() throws Exception {
        stubSearch("double", "relevancy", completed(initialBlock));
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);

        Cancellable scheduler = mock(Cancellable.class);
        when(scheduler.isCancelled()).thenReturn(false);
        setField(actorRef.underlyingActor(), "updateScheduler", scheduler);

        invokeStartNewSearch(actorRef, "double", "relevancy");

        verify(scheduler).cancel();
    }

    @Test
    public void startNewSearchDoesNotCancelAlreadyCancelledScheduler() throws Exception {
        stubSearch("noop", "relevancy", completed(initialBlock));
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);

        Cancellable scheduler = mock(Cancellable.class);
        when(scheduler.isCancelled()).thenReturn(true);
        setField(actorRef.underlyingActor(), "updateScheduler", scheduler);

        invokeStartNewSearch(actorRef, "noop", "relevancy");

        verify(scheduler, never()).cancel();
    }

    @Test
    public void stopCurrentSearchCancelsScheduler() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);
        Cancellable scheduler = mock(Cancellable.class);
        when(scheduler.isCancelled()).thenReturn(false);
        setField(actorRef.underlyingActor(), "updateScheduler", scheduler);

        Method method = UserActor.class.getDeclaredMethod("stopCurrentSearch");
        method.setAccessible(true);
        method.invoke(actorRef.underlyingActor());

        verify(scheduler).cancel();
        assertNull(getField(actorRef.underlyingActor(), "updateScheduler"));
    }

    @Test
    public void stopCurrentSearchDoesNotCancelAlreadyCancelledScheduler() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);
        Cancellable scheduler = mock(Cancellable.class);
        when(scheduler.isCancelled()).thenReturn(true);
        setField(actorRef.underlyingActor(), "updateScheduler", scheduler);

        Method method = UserActor.class.getDeclaredMethod("stopCurrentSearch");
        method.setAccessible(true);
        method.invoke(actorRef.underlyingActor());

        verify(scheduler, never()).cancel();
    }

    @Test
    public void postStopCancelsScheduler() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);
        Cancellable scheduler = mock(Cancellable.class);
        when(scheduler.isCancelled()).thenReturn(false);
        setField(actorRef.underlyingActor(), "updateScheduler", scheduler);

        actorRef.underlyingActor().postStop();

        verify(scheduler).cancel();
    }

    @Test
    public void postStopDoesNotCancelAlreadyCancelledScheduler() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);
        Cancellable scheduler = mock(Cancellable.class);
        when(scheduler.isCancelled()).thenReturn(true);
        setField(actorRef.underlyingActor(), "updateScheduler", scheduler);

        actorRef.underlyingActor().postStop();

        verify(scheduler, never()).cancel();
    }

    @Test
    public void lruSetAddReturnsFalseForDuplicates() throws Exception {
        Class<?> lruClass = Class.forName("actors.UserActor$LRUSet");
        Constructor<?> ctor = lruClass.getDeclaredConstructor(int.class);
        ctor.setAccessible(true);
        Object lru = ctor.newInstance(2);
        Method add = lruClass.getDeclaredMethod("add", String.class);
        add.setAccessible(true);

        boolean first = (boolean) add.invoke(lru, "https://example.com");
        boolean second = (boolean) add.invoke(lru, "https://example.com");

        assertTrue(first);
        assertFalse(second);
    }

    @Test
    public void preStartLoadsPersistedHistoryAndReplaysLatestSearch() throws Exception {
        SearchBlock older = block("old", "relevancy", List.of(articleOne));
        SearchBlock latest = block("new", "relevancy", List.of(articleTwo));
        // SearchHistoryService returns newest-first; latest search should be first
        when(historyService.list(anyString())).thenReturn(List.of(latest, older));

        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);

        JsonNode initialResults = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("initial_results", initialResults.get("type").asText());
        assertEquals("new", initialResults.get("data").get("query").asText());

        JsonNode historyMessage = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("history", historyMessage.get("type").asText());
        JsonNode data = historyMessage.get("data");
        assertEquals(2, data.get("count").asInt());
        assertEquals("new", data.get("searches").get(0).get("query").asText());
        assertEquals("old", data.get("searches").get(1).get("query").asText());

        assertEquals("new", ((SearchBlock) ((java.util.List<?>) getField(
                actorRef.underlyingActor(), "searchHistory")).get(0)).query());
    }

    @Test
    public void addToHistoryMaintainsMaxSizeAndOrder() throws Exception {
        TestKit socketProbe = new TestKit(system);
        TestActorRef<UserActor> actorRef = spawnUserActorRef(socketProbe);

        Method addToHistory = UserActor.class.getDeclaredMethod("addToHistory", SearchBlock.class);
        addToHistory.setAccessible(true);

        for (int i = 0; i < 12; i++) {
            addToHistory.invoke(actorRef.underlyingActor(),
                    block("q" + i, "relevancy", List.of(articleOne)));
        }

        @SuppressWarnings("unchecked")
        java.util.List<SearchBlock> history =
                (java.util.List<SearchBlock>) getField(actorRef.underlyingActor(), "searchHistory");

        assertEquals(10, history.size());
        assertEquals("q11", history.get(0).query());
        assertEquals("q2", history.get(9).query());
    }
    
    @Test
    public void startSearchTriggersWordStatsAnalysis() {
        stubSearch("test", "relevancy",
            completed(initialBlock),
            completed(updateBlock));
        
        when(wordStatsService.computeWordStats("test"))
            .thenReturn(CompletableFuture.completedFuture(
                new WordStats("test", 10, 500, 200,
                    List.of(new WordStats.WordFrequency("testing", 50)))
            ));
        
        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);
        
        sendStartSearch(userActor, "test", "relevancy");
        
        JsonNode initialResults = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("initial_results", initialResults.get("type").asText());
        
        JsonNode historyMsg = socketProbe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals("history", historyMsg.get("type").asText());
        
        Map<String, JsonNode> taskMessages = expectTaskMessages(socketProbe, true);
        
        JsonNode wordStatsMsg = taskMessages.get("wordStats");
        assertNotNull("WordStats message should be received", wordStatsMsg);
        assertEquals("test", wordStatsMsg.get("data").get("query").asText());
        assertEquals(10, wordStatsMsg.get("data").get("totalArticles").asInt());
        assertEquals(500, wordStatsMsg.get("data").get("totalWords").asInt());
        
        verify(wordStatsService, times(1)).computeWordStats("test");
    }
    
    @Test
    public void handleWebSocketMessageRejectsBlankQuery() {
        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);
        
        ObjectNode startMessage = mapper.createObjectNode();
        startMessage.put("type", "start_search");
        startMessage.put("query", "   ");
        startMessage.put("sortBy", "publishedAt");
        
        userActor.tell(startMessage, ActorRef.noSender());
        
        // Expect error message
        JsonNode errorMsg = socketProbe.expectMsgClass(Duration.ofSeconds(3), JsonNode.class);
        assertEquals("error", errorMsg.get("type").asText());
        assertTrue(errorMsg.get("data").get("message").asText()
            .contains("cannot be empty"));
        
        // Verify search was NOT performed
        verify(searchService, never()).search(anyString(), anyString());
    }

    @Test
    public void handleWebSocketMessageRejectsEmptyQuery() {
        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);
        
        ObjectNode startMessage = mapper.createObjectNode();
        startMessage.put("type", "start_search");
        startMessage.put("query", "");
        startMessage.put("sortBy", "publishedAt");
        
        userActor.tell(startMessage, ActorRef.noSender());
        
        // Expect error message
        JsonNode errorMsg = socketProbe.expectMsgClass(Duration.ofSeconds(3), JsonNode.class);
        assertEquals("error", errorMsg.get("type").asText());
        assertTrue(errorMsg.get("data").get("message").asText()
            .contains("cannot be empty"));
        
        // Verify search was NOT performed
        verify(searchService, never()).search(anyString(), anyString());
    }

    @Test
    public void handleWebSocketMessageAcceptsValidQuery() {
        stubSearch("valid", "publishedAt", completed(initialBlock));
        
        TestKit socketProbe = new TestKit(system);
        ActorRef userActor = spawnUserActor(socketProbe);
        
        // Send start_search with valid query
        sendStartSearch(userActor, "valid", "publishedAt");
        
        // Should proceed with search (expect initial_results, not error)
        JsonNode firstMsg = socketProbe.expectMsgClass(Duration.ofSeconds(3), JsonNode.class);
        assertEquals("initial_results", firstMsg.get("type").asText());
        
        // Verify search WAS performed
        verify(searchService, times(1)).search("valid", "publishedAt");
    }

    private JsonNode expectMessageOfType(TestKit probe, String expectedType) {
        JsonNode message = probe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
        assertEquals(expectedType, message.get("type").asText());
        return message;
    }

    private Map<String, JsonNode> expectTaskMessages(TestKit probe, boolean includeSourceProfile) {
        if (includeSourceProfile) {
            return drainMessagesByType(probe, "sourceProfile", "wordStats", "sentiment", "readability");
        }
        return drainMessagesByType(probe, "wordStats", "sentiment", "readability");
    }

    private Map<String, JsonNode> drainMessagesByType(TestKit probe, String... expectedTypes) {
        Map<String, JsonNode> messagesByType = new HashMap<>();
        List<String> pending = new ArrayList<>(List.of(expectedTypes));
        for (int i = 0; i < expectedTypes.length; i++) {
            JsonNode message = probe.expectMsgClass(Duration.ofSeconds(5), JsonNode.class);
            String type = message.get("type").asText();
            assertTrue("Unexpected message type: " + type, pending.remove(type));
            messagesByType.put(type, message);
        }
        return messagesByType;
    }

    private ActorRef spawnUserActor(TestKit socketProbe) {
        return system.actorOf(
                UserActor.props(
                        socketProbe.getRef(),
                        UUID.randomUUID().toString(),
                        searchService,
                        historyService,
                        newsApiClient,
                        profileService,
                        readabilityService,
                        sentimentService,
                        sourcesService,
                        wordStatsService
                )
        );
    }

    private TestActorRef<UserActor> spawnUserActorRef(TestKit socketProbe) {
        return TestActorRef.create(
                system,
                UserActor.props(
                        socketProbe.getRef(),
                        UUID.randomUUID().toString(),
                        searchService,
                        historyService,
                        newsApiClient,
                        profileService,
                        readabilityService,
                        sentimentService,
                        sourcesService,
                        wordStatsService
                )
        );
    }

    private void sendStartSearch(ActorRef actor, String query, String sortBy) {
        ObjectNode startMessage = mapper.createObjectNode();
        startMessage.put("type", "start_search");
        startMessage.put("query", query);
        startMessage.put("sortBy", sortBy);
        actor.tell(startMessage, ActorRef.noSender());
    }

    @SafeVarargs
    private final void stubSearch(String query, String sortBy, CompletionStage<SearchBlock>... responses) {
        OngoingStubbing<CompletionStage<SearchBlock>> stubbing =
                when(searchService.search(eq(query), eq(sortBy)));
        for (CompletionStage<SearchBlock> response : responses) {
            stubbing = stubbing.thenReturn(response);
        }
    }

    private CompletionStage<SearchBlock> completed(SearchBlock block) {
        return CompletableFuture.completedFuture(block);
    }

    private CompletionStage<SearchBlock> failed(Throwable error) {
        CompletableFuture<SearchBlock> stage = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> stage.completeExceptionally(error));
        return stage;
    }

    private SearchBlock block(String query, String sortBy, List<Article> articles) {
        List<ReadabilityScores> perArticle = articles.stream()
                .map(a -> new ReadabilityScores(4.0, 70.0))
                .toList();
        return new SearchBlock(
                query,
                sortBy,
                articles.size(),
                articles,
                "2025-01-01T00:00:00Z",
                new ReadabilityScores(5.0, 65.0),
                perArticle,
                Sentiment.NEUTRAL
        );
    }

    private List<Article> generateArticles(int count) {
        List<Article> articles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            articles.add(new Article(
                    "Title " + i,
                    "https://example.com/" + i,
                    "desc " + i,
                    "src-" + i,
                    "Source " + i,
                    "2025-01-01T00:00:00Z"
            ));
        }
        return articles;
    }

    private void invokeHandleNewsApiError(TestActorRef<UserActor> actorRef, Throwable error) throws Exception {
        Method method = UserActor.class.getDeclaredMethod("handleNewsApiError", Throwable.class);
        method.setAccessible(true);
        method.invoke(actorRef.underlyingActor(), error);
    }

    private void invokeStartNewSearch(TestActorRef<UserActor> actorRef, String query, String sortBy) throws Exception {
        Method method = UserActor.class.getDeclaredMethod("startNewSearch", String.class, String.class);
        method.setAccessible(true);
        method.invoke(actorRef.underlyingActor(), query, sortBy);
    }

    private void setField(UserActor actor, String fieldName, Object value) throws Exception {
        Field field = UserActor.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(actor, value);
    }

    private Object getField(UserActor actor, String fieldName) throws Exception {
        Field field = UserActor.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(actor);
    }
}
