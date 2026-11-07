package com.settorise.news;

import com.settorise.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback thumbnails for stories whose feed has no image (some MS NOW items).
 * For those stories only, the article page is requested once and its link-preview image
 * (og:image, else twitter:image) is read from the page head. Nothing else on the page is used,
 * nothing is sent to the AI, and the image itself is never downloaded: the browser loads it from
 * the outlet. Results, including "no image", are cached per link.
 */
@Component
public class PreviewImages {

    private static final Logger log = LoggerFactory.getLogger(PreviewImages.class);

    /** Stop reading once the head is done or after this many bytes. */
    private static final int MAX_BYTES = 512 * 1024;
    private static final Pattern META = Pattern.compile("<meta\\s[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY = Pattern.compile("(?:property|name)\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT = Pattern.compile("content\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    private final Map<String, String> byLink = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > AppConfig.MAX_REMEMBERED_STORIES;
                }
            });

    /**
     * Returns the stories with a preview image filled in where the feed gave none. Fetches in parallel.
     * Stories published before {@code notBefore} are hidden from readers, so their pages are never requested.
     */
    public List<Story> fillMissing(List<Story> stories, Instant notBefore) {
        List<CompletableFuture<Story>> jobs = new ArrayList<>(stories.size());
        for (Story s : stories) {
            boolean shown = s.publishedAt() != null && s.publishedAt().isAfter(notBefore);
            if (s.imageUrl() != null || !shown) {
                jobs.add(CompletableFuture.completedFuture(s));
            } else {
                jobs.add(CompletableFuture.supplyAsync(() -> withImage(s, imageFor(s.link()))));
            }
        }
        List<Story> out = new ArrayList<>(stories.size());
        for (CompletableFuture<Story> j : jobs) {
            out.add(j.join());
        }
        return out;
    }

    private static Story withImage(Story s, String image) {
        return image == null ? s : new Story(s.id(), s.headline(), s.outlet(), s.summary(), s.link(),
                s.publishedAt(), s.section(), image);
    }

    private String imageFor(String link) {
        String cached = byLink.get(link);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String found = null;
        if (ImageUrls.clean(link) != null) {
            try {
                found = ImageUrls.clean(fromHead(readHead(link)));
            } catch (Exception e) {
                log.debug("No preview image for {}: {}", link, e.toString());
            }
        }
        byLink.put(link, found == null ? "" : found);
        return found;
    }

    private static String readHead(String link) throws Exception {
        try (InputStream in = FeedHttp.get(link, "text/html")) {
            byte[] buf = new byte[16 * 1024];
            StringBuilder html = new StringBuilder();
            int total = 0;
            int n;
            while (total < MAX_BYTES && (n = in.read(buf)) > 0) {
                total += n;
                html.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                int end = html.indexOf("</head>");
                if (end >= 0) {
                    return html.substring(0, end);
                }
            }
            return html.toString();
        }
    }

    /** og:image first, then og:image:secure_url, then twitter:image. Package-private for tests. */
    static String fromHead(String head) {
        String og = null;
        String secure = null;
        String twitter = null;
        Matcher m = META.matcher(head);
        while (m.find()) {
            String tag = m.group();
            Matcher k = KEY.matcher(tag);
            Matcher c = CONTENT.matcher(tag);
            if (!k.find() || !c.find()) {
                continue;
            }
            String content = c.group(1).replace("&amp;", "&").replace("&#038;", "&").trim();
            switch (k.group(1).toLowerCase()) {
                case "og:image" -> og = og == null ? content : og;
                case "og:image:secure_url" -> secure = secure == null ? content : secure;
                case "twitter:image", "twitter:image:src" -> twitter = twitter == null ? content : twitter;
                default -> { }
            }
        }
        return og != null ? og : secure != null ? secure : twitter;
    }
}
