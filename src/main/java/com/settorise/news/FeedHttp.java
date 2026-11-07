package com.settorise.news;

import com.settorise.config.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Small shared HTTP client for feeds and the NYT API. */
final class FeedHttp {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(AppConfig.HTTP_TIMEOUT)
            .build();

    private FeedHttp() { }

    static InputStream get(String url, String accept) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(AppConfig.HTTP_TIMEOUT)
                .header("User-Agent", AppConfig.USER_AGENT)
                .header("Accept", accept)
                .GET()
                .build();
        HttpResponse<InputStream> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            res.body().close();
            throw new IOException("HTTP " + res.statusCode());
        }
        return res.body();
    }
}
