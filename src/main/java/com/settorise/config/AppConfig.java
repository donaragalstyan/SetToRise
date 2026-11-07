package com.settorise.config;

import java.time.Duration;
import java.util.List;

/**
 * Central app constants. Rename the app by changing APP_NAME only:
 * the frontend reads it from GET /api/meta.
 *
 * Secrets (API keys) are NOT here. They come from environment variables; see AppSettings.
 */
public final class AppConfig {

    public static final String APP_NAME = "Set to Rise";

    /** Honest, generic client identifier for outbound feed requests. Some feeds reject requests without one. */
    public static final String USER_AGENT = "Mozilla/5.0 (compatible; " + APP_NAME + "-hackathon/0.1; non-commercial)";

    public static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

    /** Feed summaries are trimmed to this length. Keeps us to a short teaser, never article body text. */
    public static final int MAX_SUMMARY_CHARS = 300;

    /** Upper bound on remembered stories, so /api/story/{id} still works after a feed refresh. */
    public static final int MAX_REMEMBERED_STORIES = 3000;

    /** Upper bound on cached AI summaries (one per article ID). */
    public static final int MAX_CACHED_SUMMARIES = 2000;

    /** A section hint tells the classifier what kind of feed an item came from. */
    public enum Section { GENERAL, POLITICS, WORLD }

    public record RssSource(String outlet, String url, Section section) { }

    /** Headlines-only RSS sources. Verified at startup; failures are logged, not fatal. */
    public static final List<RssSource> RSS_SOURCES = List.of(
            new RssSource("CNN", "http://rss.cnn.com/rss/cnn_latest.rss", Section.GENERAL),
            new RssSource("CNN", "http://rss.cnn.com/rss/cnn_allpolitics.rss", Section.POLITICS),
            new RssSource("CNN", "http://rss.cnn.com/rss/edition_world.rss", Section.WORLD),
            new RssSource("Fox News", "https://moxie.foxnews.com/google-publisher/latest.xml", Section.GENERAL),
            new RssSource("Fox News", "https://moxie.foxnews.com/google-publisher/politics.xml", Section.POLITICS),
            new RssSource("Fox News", "https://moxie.foxnews.com/google-publisher/world.xml", Section.WORLD),
            new RssSource("MS NOW", "https://www.ms.now/feed", Section.GENERAL),
            // Added 2026-11-07. All verified live with an image on every item.
            // Right-leaning (placeholder ratings, see outlet-leans.json):
            new RssSource("New York Post", "https://nypost.com/politics/feed/", Section.POLITICS),
            new RssSource("New York Post", "https://nypost.com/world-news/feed/", Section.WORLD),
            new RssSource("Washington Examiner", "https://www.washingtonexaminer.com/section/news/feed/", Section.POLITICS),
            new RssSource("The Washington Times", "https://www.washingtontimes.com/rss/headlines/news/politics/", Section.POLITICS),
            new RssSource("The Washington Times", "https://www.washingtontimes.com/rss/headlines/news/world/", Section.WORLD),
            new RssSource("The Daily Caller", "https://dailycaller.com/feed/", Section.GENERAL),
            // Left-leaning:
            new RssSource("The Guardian", "https://www.theguardian.com/us-news/rss", Section.GENERAL),
            new RssSource("The Guardian", "https://www.theguardian.com/world/rss", Section.WORLD),
            new RssSource("NBC News", "https://feeds.nbcnews.com/nbcnews/public/politics", Section.POLITICS),
            new RssSource("NBC News", "https://feeds.nbcnews.com/nbcnews/public/world", Section.WORLD),
            new RssSource("ABC News", "https://abcnews.go.com/abcnews/politicsheadlines", Section.POLITICS),
            new RssSource("ABC News", "https://abcnews.go.com/abcnews/internationalheadlines", Section.WORLD)
    );

    public record NytSection(String name, Section section) { }

    /** NYT Top Stories sections. Each refresh costs one request per section. */
    public static final List<NytSection> NYT_SECTIONS = List.of(
            new NytSection("politics", Section.POLITICS),
            new NytSection("world", Section.WORLD),
            new NytSection("us", Section.GENERAL)
    );

    public static final String NYT_OUTLET = "The New York Times";

    private AppConfig() { }
}
