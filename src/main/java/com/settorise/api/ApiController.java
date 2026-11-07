package com.settorise.api;

import com.settorise.ai.AiResult;
import com.settorise.ai.PickResult;
import com.settorise.ai.SummaryService;
import com.settorise.config.AppConfig;
import com.settorise.config.AppSettings;
import com.settorise.news.Classifier;
import com.settorise.news.NewsService;
import com.settorise.news.OutletLeans;
import com.settorise.news.OutletLeans.Bucket;
import com.settorise.news.Story;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Public API. Note what is NOT accepted: quiz answers, scores or axis values.
 * The only reader-derived input is show=left|right|both.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Set<String> TABS = Set.of("politics", "issues", "global");

    private final NewsService news;
    private final FeedService feeds;
    private final SummaryService summaries;
    private final OutletLeans leans;
    private final AppSettings settings;

    public ApiController(NewsService news, FeedService feeds, SummaryService summaries, OutletLeans leans,
                         AppSettings settings) {
        this.settings = settings;
        this.news = news;
        this.feeds = feeds;
        this.summaries = summaries;
        this.leans = leans;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appName", AppConfig.APP_NAME);
        m.put("topics", Classifier.topicNames());
        m.put("aiEnabled", summaries.isEnabled());
        m.put("nytEnabled", settings.hasNytKey());
        m.put("outletLeansArePlaceholder", leans.isPlaceholder());
        return m;
    }

    @GetMapping("/feed")
    public ResponseEntity<?> feed(@RequestParam(defaultValue = "politics") String tab,
                                  @RequestParam(required = false) String show) {
        if (!TABS.contains(tab)) {
            return ResponseEntity.badRequest().body(Map.of("error", "tab must be politics, issues or global"));
        }
        Show s = Show.parse(show);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tab", tab);
        body.put("show", s.name().toLowerCase());
        body.put("stories", feeds.feed(tab, s));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/story/{id}")
    public ResponseEntity<?> story(@PathVariable String id, @RequestParam(required = false) String show) {
        Story story = news.find(id).orElse(null);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "story not found"));
        }
        Show s = Show.parse(show);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("show", s.name().toLowerCase());
        body.put("story", feeds.toCard(story));
        // All framings are returned regardless of show. The browser picks which to display.
        body.put("ai", summaries.summarize(story));
        body.put("otherCoverage", feeds.related(story, s, 4));
        body.put("citations", List.of(Map.of("outlet", story.outlet(), "url", story.link())));
        return ResponseEntity.ok(body);
    }

    /**
     * "See another perspective": the same story from outlets on the other side, plus an AI comparison of
     * the story and the best match. If no match is found, the story's own AI framings are returned so the
     * page can still show the strongest case from the other side.
     */
    @GetMapping("/perspective/{id}")
    public ResponseEntity<?> perspective(@PathVariable String id) {
        Story story = news.find(id).orElse(null);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "story not found"));
        }
        Set<Bucket> sides = feeds.otherSides(story);
        List<Story> others = feeds.otherSideCoverage(story, 3);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("story", feeds.toCard(story));
        body.put("otherSides", sides.stream().map(b -> b.name().toLowerCase()).sorted().collect(Collectors.toList()));
        if (!others.isEmpty()) {
            body.put("match", "same_event");
            body.put("otherCoverage", others.stream().map(feeds::toCard).collect(Collectors.toList()));
            body.put("comparison", summaries.compare(story, others.get(0)));
            return ResponseEntity.ok(body);
        }
        // No close match: always show the most relevant other-side article, chosen by the AI from the
        // ranked candidates (or the top-ranked one if the AI is off or fails), plus the AI overview.
        CompletableFuture<AiResult> overview = CompletableFuture.supplyAsync(() -> summaries.summarize(story));
        List<Story> candidates = feeds.otherSideCandidates(story, 40);
        if (candidates.isEmpty()) {
            body.put("match", "none");
            body.put("otherCoverage", List.of());
            body.put("ai", overview.join());
            return ResponseEntity.ok(body);
        }
        PickResult pick = summaries.pickOtherSide(story, candidates);
        Story chosen = pick.isOk() ? candidates.get(pick.pick().choice() - 1) : candidates.get(0);
        String relation = pick.isOk() ? pick.pick().relation() : "related";
        body.put("match", relation);
        body.put("matchReason", pick.isOk() ? pick.pick().reason() : null);
        body.put("otherCoverage", List.of(feeds.toCard(chosen)));
        if ("same_event".equals(relation)) {
            body.put("comparison", summaries.compare(story, chosen));
        }
        body.put("ai", overview.join());
        return ResponseEntity.ok(body);
    }
}
