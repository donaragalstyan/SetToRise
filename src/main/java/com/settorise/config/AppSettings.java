package com.settorise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Settings that come from environment variables (via application.properties placeholders). */
@Component
public class AppSettings {

    private final String nytApiKey;
    private final String anthropicApiKey;
    private final String claudeModel;
    private final int rssCacheMinutes;
    private final int nytCacheMinutes;
    private final int maxStoryAgeDays;

    public AppSettings(
            @Value("${app.nyt-api-key:}") String nytApiKey,
            @Value("${app.anthropic-api-key:}") String anthropicApiKey,
            @Value("${app.claude-model:claude-opus-5-5}") String claudeModel,
            @Value("${app.rss-cache-minutes:10}") int rssCacheMinutes,
            @Value("${app.nyt-cache-minutes:15}") int nytCacheMinutes,
            @Value("${app.max-story-age-days:7}") int maxStoryAgeDays) {
        this.nytApiKey = nytApiKey == null ? "" : nytApiKey.trim();
        this.anthropicApiKey = anthropicApiKey == null ? "" : anthropicApiKey.trim();
        this.claudeModel = claudeModel;
        // NYT allows about 500 requests/day. Never refresh faster than every 10 minutes.
        this.rssCacheMinutes = Math.max(1, rssCacheMinutes);
        this.nytCacheMinutes = Math.max(10, nytCacheMinutes);
        this.maxStoryAgeDays = Math.max(1, maxStoryAgeDays);
    }

    public String nytApiKey() { return nytApiKey; }
    public boolean hasNytKey() { return !nytApiKey.isEmpty(); }
    public String anthropicApiKey() { return anthropicApiKey; }
    public boolean hasAnthropicKey() { return !anthropicApiKey.isEmpty(); }
    public String claudeModel() { return claudeModel; }
    public int rssCacheMinutes() { return rssCacheMinutes; }
    public int nytCacheMinutes() { return nytCacheMinutes; }
    public int maxStoryAgeDays() { return maxStoryAgeDays; }
}
