package controllers;

import play.*;
import play.mvc.*;
import play.libs.Json;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import models.*;

/**
 * This controller contains an action to handle HTTP requests
 * to show the source website profile page.
 */
public class Profile extends Controller {

    private static final String KEY = "2f87b3d95a724986af1e3c2651ddc810";

    public Result index(String name) {
        JsonNode jsonNode = handleRequest(name);
        if (jsonNode == null) {
            // TODO: not found page
            return ok(views.html.profile.render(null));
        }
        NewsResponse response = Json.fromJson(jsonNode, NewsResponse.class);
        // Access JSON fields
        System.out.println("Status: " + response.status);
        System.out.println("Total Results: " + response.totalResults);

        for (var article : response.articles) {
            System.out.println("Title: " + article.title);
            System.out.println("Source: " + article.source.name);
        }
        return ok(views.html.profile.render(response));
    }

    JsonNode handleRequest(String name) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://newsapi.org/v2/top-headlines?sources=" + name + "&apiKey=" + KEY))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return Json.parse(response.body());
        } catch (Exception e) {
            return null;
        }
    }

}
