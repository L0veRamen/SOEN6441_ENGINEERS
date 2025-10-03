package controllers;

import play.*;
import play.mvc.*;
import play.libs.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.concurrent.*;

import models.*;

/**
 * This controller contains an action to handle HTTP requests
 * to show the source website profile page.
 */
public class Profile extends Controller {

    private static final String KEY = "2f87b3d95a724986af1e3c2651ddc810";
    private List<SourceProfile> sources;
    private HashMap<String, NewsResponse> cache;

    public CompletionStage<Result> index(String name) {
        if (cache == null) {
            cache = new HashMap<>();
        }
        CompletableFuture<List<SourceProfile>> response = (sources == null || sources.isEmpty())
                ? handleSourceRequest()
                : CompletableFuture.completedFuture(sources);

        CompletableFuture<NewsResponse> newsResponse = (cache.containsKey(name))
                ? CompletableFuture.completedFuture(cache.get(name))
                : handleArticleRequest(name);

        return response.thenCombine(newsResponse, (srcs, news) -> {
            sources = srcs;
            if (sources.isEmpty()) {
                return internalServerError(views.html.error.render("Api failed to load sources"));
            }
            var source = sources.stream()
                    .filter( profile -> profile.name.equalsIgnoreCase(name) )
                    .limit(1)
                    .collect(Collectors.toList());
            if (source.isEmpty()) {
                return internalServerError(views.html.error.render(name + " is not found in all sources"));
            }
            cache.put(name, news);
            return ok(views.html.profile.render(source.get(0), news.articles));

        }).exceptionally(e -> internalServerError(views.html.error.render(e.getMessage())));
    }

    CompletableFuture<NewsResponse> handleArticleRequest(String name) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://newsapi.org/v2/top-headlines?sources=" + name + "&pageSize=10&apiKey=" + KEY))
                    .GET()
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> Json.fromJson(Json.parse(body), NewsResponse.class))
                    .exceptionally(e -> null);
        } catch (Exception e) {
            CompletableFuture<NewsResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    CompletableFuture<List<SourceProfile>> handleSourceRequest() {
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://newsapi.org/v2/top-headlines/sources?apiKey=" + KEY))
                    .GET()
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        var sourcesNode = Json.parse(body).get("sources");
                        return StreamSupport.stream(sourcesNode.spliterator(), false)
                                .map(node -> Json.fromJson(node, SourceProfile.class))
                                .toList();
                    })
                    .exceptionally(ex -> List.of());

        } catch (Exception e) {
            CompletableFuture<List<SourceProfile>> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

}
