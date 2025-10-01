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
import java.util.ArrayList;
import java.util.stream.StreamSupport;

import models.*;

/**
 * This controller contains an action to handle HTTP requests
 * to show the source website profile page.
 */
public class Profile extends Controller {

    private static final String KEY = "2f87b3d95a724986af1e3c2651ddc810";
    private List<SourceProfile> sources;
    private HashMap<String, NewsResponse> cache;

    public Result index(String name) {
        if (sources == null || sources.isEmpty()) {
            sources = handleSourceRequest();
        }
        if (sources.isEmpty()) {
            return ok(views.html.error.render("Api failed to load sources"));
        }
        var source = sources.stream().filter( profile -> profile.name.equalsIgnoreCase(name) ).findAny();
        if (source.isEmpty()) {
            return ok(views.html.error.render(name + " is not found in all sources"));
        }
        NewsResponse response;
        if (cache == null) {
            cache = new HashMap<>();
        }
        if (cache.containsKey(name)) {
            response = cache.get(name);
        } else {
            response = handleArticleRequest(name);
            if (response == null) {
                return ok(views.html.error.render("Api failed to load articles from source " + name));
            }
            cache.put(name, response);
        }

        return ok(views.html.profile.render(source.get(), response.articles));
    }

    NewsResponse handleArticleRequest(String name) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://newsapi.org/v2/top-headlines?sources=" + name + "&pageSize=10&apiKey=" + KEY))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return Json.fromJson(Json.parse(response.body()), NewsResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    List<SourceProfile> handleSourceRequest() {
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://newsapi.org/v2/top-headlines/sources?apiKey=" + KEY))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            var sourcesNode = Json.parse(response.body()).get("sources");
            return StreamSupport.stream(sourcesNode.spliterator(), false)
                        .map(node -> Json.fromJson(node, SourceProfile.class))
                        .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

}
