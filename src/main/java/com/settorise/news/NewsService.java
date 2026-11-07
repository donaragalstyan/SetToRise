package com.settorise.news;

import com.settorise.config.AppConfig;
import com.settorise.config.AppConfig.NytSection;
import com.settorise.config.AppConfig.RssSource;
import com.settorise.config.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates all sources. Each source is cached separately (RSS 10 min, NYT 15 min by default)
 * and refreshed lazily when a request finds it stale. A failing source keeps its last good items.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private record Cached(List<Story> stories, Instant fetchedAt) { }

    private final RssClient rss;
    private final NytClient nyt;
    private final PreviewImages previews;
    private final AppSettings settings;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final Set<String> failing = ConcurrentHashMap.newKeySet();

    /** Every story seen recently, so a story page still works after it falls out of a feed. */
    private final Map<String, Story> remembered = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Story> eldest) {
                    return size() > AppConfig.MAX_REMEMBERED_STORIES;
                }
            });

    public NewsService(RssClient rss, NytClient nyt, PreviewImages previews, AppSettings settings) {
        this.rss = rss;
        this.nyt = nyt;
        this.previews = previews;
        this.settings = settings;
    }

    /** Startup check: fetch every source once, log what works and what does not. */
    @EventListener(ApplicationReadyEvent.class)
    public void verifySourcesAtStartup() {
        log.info("Verifying news sources...");
        List<CompletableFuture<Void>> checks = new ArrayList<>();
        for (RssSource s : AppConfig.RSS_SOURCES) {
            checks.add(CompletableFuture.runAsync(() -> verify(rssKey(s), s.outlet(), s.url())));
        }
        if (settings.hasNytKey()) {
            for (NytSection s : AppConfig.NYT_SECTIONS) {
                checks.add(CompletableFuture.runAsync(() -> verify(nytKey(s), AppConfig.NYT_OUTLET, "top stories/" + s.name())));
            }
        } else {
            log.warn("NYT_API_KEY is not set. NYT Top Stories are disabled.");
        }
        CompletableFuture.allOf(checks.toArray(new CompletableFuture[0])).join();
    }

    private void verify(String key, String outlet, String label) {
        List<Story> items = refresh(key);
        if (failing.contains(key)) {
            log.error("SOURCE FAILED  {} [{}]", outlet, label);
            return;
        }
        Instant newest = items.stream().map(Story::publishedAt).filter(d -> d != null)
                .max(Comparator.naturalOrder()).orElse(null);
        if (newest == null || newest.isBefore(cutoff())) {
            log.warn("SOURCE STALE   {} [{}]: {} items, newest {} (older than {} days, hidden)",
                    outlet, label, items.size(), newest, settings.maxStoryAgeDays());
        } else {
            log.info("SOURCE OK      {} [{}]: {} items, newest {}", outlet, label, items.size(), newest);
        }
    }

    /** All current stories from every source: deduplicated, fresh, newest first. */
    public List<Story> allStories() {
        Map<String, Story> byId = new LinkedHashMap<>();
        for (RssSource s : AppConfig.RSS_SOURCES) {
            addAll(byId, getOrRefresh(rssKey(s)));
        }
        if (settings.hasNytKey()) {
            for (NytSection s : AppConfig.NYT_SECTIONS) {
                addAll(byId, getOrRefresh(nytKey(s)));
            }
        }
        Instant cutoff = cutoff();
        List<Story> out = new ArrayList<>();
        for (Story s : byId.values()) {
            if (s.publishedAt() != null && s.publishedAt().isAfter(cutoff)) {
                out.add(s);
            }
        }
        out.sort(Comparator.comparing(Story::publishedAt).reversed());
        return out;
    }

    public Optional<Story> find(String id) {
        Story s = remembered.get(id);
        if (s == null) {
            allStories();
            s = remembered.get(id);
        }
        return Optional.ofNullable(s);
    }

    private void addAll(Map<String, Story> byId, List<Story> stories) {
        for (Story s : stories) {
            // The first source to list a link wins (a section feed and a "latest" feed often overlap).
            byId.putIfAbsent(s.id(), s);
        }
    }

    private Instant cutoff() {
        return Instant.now().minus(Duration.ofDays(settings.maxStoryAgeDays()));
    }

    private List<Story> getOrRefresh(String key) {
        Cached c = cache.get(key);
        if (c != null && !isStale(key, c)) {
            return c.stories();
        }
        return refresh(key);
    }

    private boolean isStale(String key, Cached c) {
        int minutes = key.startsWith("nyt:") ? settings.nytCacheMinutes() : settings.rssCacheMinutes();
        return c.fetchedAt().plus(Duration.ofMinutes(minutes)).isBefore(Instant.now());
    }

    private List<Story> refresh(String key) {
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            Cached c = cache.get(key);
            if (c != null && !isStale(key, c)) {
                return c.stories();
            }
            try {
                // Stories whose feed has no image get the article's link-preview image (cached per link).
                List<Story> fresh = previews.fillMissing(fetch(key), cutoff());
                cache.put(key, new Cached(fresh, Instant.now()));
                failing.remove(key);
                for (Story s : fresh) {
                    remembered.put(s.id(), s);
                }
                return fresh;
            } catch (Exception e) {
                log.warn("Fetch failed for {}: {}", key, e.toString());
                failing.add(key);
                // Keep serving the last good copy (or nothing), and back off until the next TTL window
                // so a dead source does not slow down every request.
                List<Story> last = c == null ? List.of() : c.stories();
                cache.put(key, new Cached(last, Instant.now()));
                return last;
            }
        }
    }

    private List<Story> fetch(String key) throws Exception {
        for (RssSource s : AppConfig.RSS_SOURCES) {
            if (rssKey(s).equals(key)) {
                return rss.fetch(s);
            }
        }
        for (NytSection s : AppConfig.NYT_SECTIONS) {
            if (nytKey(s).equals(key)) {
                return nyt.fetch(s);
            }
        }
        throw new IllegalArgumentException("Unknown source " + key);
    }

    private static String rssKey(RssSource s) {
        return "rss:" + s.url();
    }

    private static String nytKey(NytSection s) {
        return "nyt:" + s.name();
    }
}
