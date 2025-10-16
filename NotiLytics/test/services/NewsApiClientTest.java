package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Article;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class NewsApiClientTest {

    @Mock
    private WSClient wsClient;

    @Mock
    private WSRequest wsRequest;

    @Mock
    private WSResponse wsResponse;

    private ObjectMapper mapper;
    private Config config;
    private NewsApiClient client;

    @Before
    public void setUp() {
        mapper = new ObjectMapper();
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("newsapi.key", "test-api-key");
        configMap.put("newsapi.baseUrl", "https://newsapi.org/v2");
        configMap.put("cache.ttl.search", "15 minutes");
        configMap.put("cache.maxSize", 100);
        config = ConfigFactory.parseMap(configMap);

        when(wsClient.url(anyString())).thenReturn(wsRequest);
        when(wsRequest.get()).thenReturn(CompletableFuture.completedFuture(wsResponse));

        client = new NewsApiClient(wsClient, config);
    }

    @Test(expected = IllegalStateException.class)
    public void constructorRequiresApiKey() {
        Config badConfig = ConfigFactory.parseString("newsapi.baseUrl = \"https://newsapi.org/v2\"");
        new NewsApiClient(wsClient, badConfig);
    }

    @Test(expected = IllegalStateException.class)
    public void constructorRequiresBaseUrl() {
        Config badConfig = ConfigFactory.parseString("newsapi.key = \"abc\"");
        new NewsApiClient(wsClient, badConfig);
    }

    @Test
    public void searchEverythingParsesArticlesAndCachesResult() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 2,
                  "articles": [
                    {
                      "source": {"id": "techcrunch", "name": "TechCrunch"},
                      "title": "First article",
                      "description": "desc",
                      "url": "https://example.com/a",
                      "publishedAt": "2024-01-01T00:00:00Z"
                    },
                    {
                      "source": {"id": "verge", "name": "The Verge"},
                      "title": "Second article",
                      "description": "desc",
                      "url": "https://example.com/b",
                      "publishedAt": "2024-01-01T01:00:00Z"
                    }
                  ]
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse first = client.searchEverything("java", "publishedAt", 10)
                .toCompletableFuture()
                .get();

        assertEquals(2, first.articles().size());
        assertEquals(2, first.totalResults());
        Article article = first.articles().get(0);
        assertEquals("First article", article.title());
        assertEquals("TechCrunch", article.sourceName());

        NewsApiClient.SearchResponse second = client.searchEverything("java", "publishedAt", 10)
                .toCompletableFuture()
                .get();
        assertEquals(first, second);

        verify(wsClient, times(1)).url(contains("q=java"));
        verify(wsRequest, times(1)).get();
    }

    @Test
    public void searchEverythingEncodesQueryAndHandlesHttpError() throws Exception {
        when(wsResponse.getStatus()).thenReturn(500);
        when(wsResponse.getBody()).thenReturn("failure");

        NewsApiClient.SearchResponse response = client.searchEverything("java news", "relevancy", 5)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
        verify(wsClient).url(argThat(url -> url.contains("java+news")));
        verify(wsRequest).get();
    }

    @Test
    public void searchEverythingHandlesApiErrorStatus() throws Exception {
        String json = """
                {
                  "status": "error",
                  "code": "apiKeyInvalid",
                  "message": "Invalid key"
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    @Test
    public void searchEverythingHandlesMalformedJson() throws Exception {
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(null);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
    }

    @Test
    public void searchEverythingHandlesException() throws Exception {
        CompletableFuture<WSResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("network down"));
        when(wsRequest.get()).thenReturn(failed);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    @Test
    public void searchEverythingFiltersInvalidArticlesAndAvoidsCachingEmptyResult() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 2,
                  "articles": [
                    {
                      "source": {"id": "id1", "name": "Source 1"},
                      "title": "",
                      "url": "https://example.com/invalid"
                    },
                    {
                      "source": {"id": null, "name": "Source 2"},
                      "title": "Valid",
                      "description": null,
                      "url": "https://example.com/valid"
                    }
                  ]
                }
                """;
        String emptyJson = """
                {
                  "status": "ok",
                  "totalResults": 0,
                  "articles": []
                }
                """;
        JsonNode firstNode = mapper.readTree(json);
        JsonNode emptyNode = mapper.readTree(emptyJson);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(firstNode, emptyNode, emptyNode);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertEquals(1, response.articles().size());
        assertEquals("Valid", response.articles().get(0).title());

        NewsApiClient.SearchResponse emptyResponse = client.searchEverything("another", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(emptyResponse.articles().isEmpty());

        NewsApiClient.SearchResponse secondCall = client.searchEverything("another", "relevancy", 10)
                .toCompletableFuture()
                .get();

        assertTrue(secondCall.articles().isEmpty());
        verify(wsRequest, times(3)).get();
    }

    @Test
    public void searchEverythingHandlesMissingArticlesArray() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "totalResults": 3
                }
                """;
        JsonNode jsonNode = mapper.readTree(json);
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(jsonNode);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "publishedAt", 5)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(3, response.totalResults());
    }

    @Test
    public void searchEverythingHandlesJsonParsingException() throws Exception {
        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenThrow(new RuntimeException("boom"));

        NewsApiClient.SearchResponse response = client.searchEverything("java", "publishedAt", 5)
                .toCompletableFuture()
                .get();

        assertTrue(response.articles().isEmpty());
        assertEquals(0, response.totalResults());
    }

    @Test
    public void searchEverythingContinuesWhenArticleParsingFails() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "ok");
        root.put("totalResults", 2);

        ArrayNode articles = mapper.createArrayNode();
        ObjectNode validArticle = mapper.createObjectNode();
        ObjectNode source = mapper.createObjectNode();
        source.put("id", "id1");
        source.put("name", "Source 1");
        validArticle.set("source", source);
        validArticle.put("title", "Valid Article");
        validArticle.put("url", "https://example.com/valid");
        articles.add(validArticle);

        JsonNode badNode = mock(JsonNode.class);
        when(badNode.has(anyString())).thenThrow(new RuntimeException("broken"));
        articles.add(badNode);

        root.set("articles", articles);

        when(wsResponse.getStatus()).thenReturn(200);
        when(wsResponse.asJson()).thenReturn(root);

        NewsApiClient.SearchResponse response = client.searchEverything("java", "publishedAt", 5)
                .toCompletableFuture()
                .get();

        assertEquals(1, response.articles().size());
        assertEquals("Valid Article", response.articles().get(0).title());
    }
}
