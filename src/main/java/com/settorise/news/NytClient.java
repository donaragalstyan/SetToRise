package com.settorise.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.settorise.config.AppConfig;
import com.settorise.config.AppConfig.NytSection;
import com.settorise.config.AppSettings;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** NYT Top Stories API. Uses title, abstract, url, published_date and the largest multimedia image URL. */
@Component
public class NytClient {

    private static final String BASE = "https://api.nytimes.com/svc/topstories/v2/";

    private final AppSettings settings;
    private final ObjectMapper mapper;

    public NytClient(AppSettings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    public List<Story> fetch(NytSection section) throws Exception {
        String url = BASE + section.name() + ".json?api-key="
                + URLEncoder.encode(settings.nytApiKey(), StandardCharsets.UTF_8);
        JsonNode root;
        try (InputStream in = FeedHttp.get(url, "application/json")) {
            root = mapper.readTree(in);
        }
        List<Story> out = new ArrayList<>();
        for (JsonNode r : root.path("results")) {
            String link = r.path("url").asText("");
            String title = TextUtil.cleanHeadline(r.path("title").asText(""));
            if (link.isBlank() || title.isEmpty()) {
                continue;
            }
            String date = r.path("published_date").asText("");
            out.add(new Story(TextUtil.idFor(link), title, AppConfig.NYT_OUTLET,
                    TextUtil.cleanSummary(r.path("abstract").asText("")), link,
                    date.isEmpty() ? null : OffsetDateTime.parse(date).toInstant(),
                    section.section(), largestImage(r.path("multimedia"))));
        }
        return out;
    }

    private static String largestImage(JsonNode multimedia) {
        String best = null;
        int bestWidth = -1;
        for (JsonNode m : multimedia) {
            if (!"image".equals(m.path("type").asText("image"))) {
                continue;
            }
            int w = m.path("width").asInt(0);
            if (w > bestWidth) {
                bestWidth = w;
                best = m.path("url").asText(null);
            }
        }
        return ImageUrls.clean(best);
    }
}
