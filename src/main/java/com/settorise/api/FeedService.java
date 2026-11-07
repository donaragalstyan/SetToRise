package com.settorise.api;

import com.settorise.news.Classifier;
import com.settorise.news.NewsService;
import com.settorise.news.OutletLeans;
import com.settorise.news.OutletLeans.Bucket;
import com.settorise.news.Story;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds tab feeds and "same story, other outlets" lists. */
@Service
public class FeedService {

    private static final Set<String> STOP = Set.of(
            "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "at", "by", "with", "from", "as",
            "is", "are", "was", "were", "be", "been", "it", "its", "his", "her", "their", "they", "he", "she",
            "this", "that", "after", "over", "into", "about", "says", "said", "new", "will", "has", "have",
            "not", "but", "who", "what", "how", "why", "more", "than", "up", "out", "amid", "could", "would");

    /**
     * Other-side articles to choose from when no close match exists: those sharing a tab with the story
     * (or any, if none do), ranked by word overlap and then recency.
     */
    public List<Story> otherSideCandidates(Story target, int limit) {
        Set<Bucket> sides = otherSides(target);
        Set<String> targetTabs = new HashSet<>(toCard(target).tabs());
        Set<String> base = keywords(target);
        List<Story> sameTab = new ArrayList<>();
        List<Story> any = new ArrayList<>();
        for (Story s : news.allStories()) {   // newest first
            if (s.id().equals(target.id()) || !sides.contains(leans.bucket(s.outlet()))) {
                continue;
            }
            any.add(s);
            if (toCard(s).tabs().stream().anyMatch(targetTabs::contains)) {
                sameTab.add(s);
            }
        }
        List<Story> pool = sameTab.isEmpty() ? any : sameTab;
        List<Story> ranked = new ArrayList<>(pool);
        // Stable sort: equal overlap keeps newest first.
        ranked.sort((a, b) -> Double.compare(jaccard(base, keywords(b)), jaccard(base, keywords(a))));
        return ranked.subList(0, Math.min(limit, ranked.size()));
    }

    /** Minimum word overlap (Jaccard) for "same story, other side". */
    static final double PERSPECTIVE_MIN_SIMILARITY = 0.15;

    private final NewsService news;
    private final Classifier classifier;
    private final OutletLeans leans;

    public FeedService(NewsService news, Classifier classifier, OutletLeans leans) {
        this.news = news;
        this.classifier = classifier;
        this.leans = leans;
    }

    /**
     * The feed for a tab. A left or right lens sees only outlets rated on that side
     * (placeholder ratings, see OutletLeans). A "both" lens sees every outlet, mixed.
     */
    public List<StoryCard> feed(String tab, Show show) {
        List<StoryCard> matching = new ArrayList<>();
        for (Story s : news.allStories()) {
            StoryCard c = toCard(s);
            if (c.tabs().contains(tab) && onSide(s.outlet(), show)) {
                matching.add(c);
            }
        }
        return mixOutlets(matching, show);
    }

    private boolean onSide(String outlet, Show show) {
        return switch (show) {
            case LEFT -> leans.bucket(outlet) == Bucket.LEFT;
            case RIGHT -> leans.bucket(outlet) == Bucket.RIGHT;
            case BOTH -> true;
        };
    }

    /** The side(s) opposite the story's outlet: left for a right outlet, right for a left one, both for center. */
    public Set<Bucket> otherSides(Story story) {
        return switch (leans.bucket(story.outlet())) {
            case LEFT -> Set.of(Bucket.RIGHT);
            case RIGHT -> Set.of(Bucket.LEFT);
            case CENTER -> Set.of(Bucket.LEFT, Bucket.RIGHT);
        };
    }

    /**
     * The same story as covered by outlets on the other side, best match first, one per outlet.
     * Matching is by headline and summary word overlap, so it can miss or mismatch; the AI
     * comparison double-checks that the best match is really the same event.
     */
    public List<Story> otherSideCoverage(Story target, int limit) {
        Set<Bucket> sides = otherSides(target);
        Set<String> base = keywords(target);
        List<Story> candidates = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (Story s : news.allStories()) {
            if (s.id().equals(target.id()) || !sides.contains(leans.bucket(s.outlet()))) {
                continue;
            }
            double sim = jaccard(base, keywords(s));
            if (sim >= PERSPECTIVE_MIN_SIMILARITY) {
                int i = 0;
                while (i < scores.size() && scores.get(i) >= sim) {
                    i++;
                }
                candidates.add(i, s);
                scores.add(i, sim);
            }
        }
        List<Story> out = new ArrayList<>();
        Set<String> outletsSeen = new HashSet<>();
        for (Story s : candidates) {
            if (out.size() < limit && outletsSeen.add(s.outlet())) {
                out.add(s);
            }
        }
        return out;
    }

    public StoryCard toCard(Story s) {
        Classifier.Tags t = classifier.tag(s);
        List<String> tabs = new ArrayList<>();
        if (t.politics()) {
            tabs.add("politics");
        }
        if (!t.topics().isEmpty()) {
            tabs.add("issues");
        }
        if (t.global()) {
            tabs.add("global");
        }
        return new StoryCard(s.id(), s.headline(), s.outlet(), leans.label(s.outlet()), s.summary(),
                s.link(), s.publishedAt(), tabs, t.topics(), t.electionRelated(), s.imageUrl());
    }

    /**
     * Other outlets' coverage of the same story (headline/summary word overlap).
     * Outlets outside the reader's chosen lean come first so they see a mix.
     */
    public List<StoryCard> related(Story target, Show show, int limit) {
        Set<String> base = keywords(target);
        List<Scored> scored = new ArrayList<>();
        for (Story s : news.allStories()) {
            if (s.id().equals(target.id()) || s.outlet().equals(target.outlet()) || !onSide(s.outlet(), show)) {
                // Before "See another perspective", the reader sees only outlets on their own side.
                continue;
            }
            double sim = jaccard(base, keywords(s));
            if (sim >= 0.2) {
                scored.add(new Scored(toCard(s), sim));
            }
        }
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<StoryCard> cards = new ArrayList<>();
        Set<String> outletsSeen = new HashSet<>();
        for (Scored sc : scored) {
            // One best match per outlet keeps the list varied.
            if (outletsSeen.add(sc.card().outlet())) {
                cards.add(sc.card());
            }
        }
        List<StoryCard> mixed = mixOutlets(cards, show);
        return mixed.subList(0, Math.min(limit, mixed.size()));
    }

    private record Scored(StoryCard card, double score) { }

    /**
     * Round-robins across outlet-lean buckets so no single lean dominates the top of the feed.
     * The reader's own lean goes last in each round; "both" starts with center.
     * Within a bucket, newest first (the input is already sorted that way).
     */
    List<StoryCard> mixOutlets(List<StoryCard> cards, Show show) {
        List<Bucket> order = switch (show) {
            case LEFT -> List.of(Bucket.RIGHT, Bucket.CENTER, Bucket.LEFT);
            case RIGHT -> List.of(Bucket.LEFT, Bucket.CENTER, Bucket.RIGHT);
            case BOTH -> List.of(Bucket.CENTER, Bucket.LEFT, Bucket.RIGHT);
        };
        Map<Bucket, Deque<StoryCard>> byBucket = new EnumMap<>(Bucket.class);
        for (Bucket b : Bucket.values()) {
            byBucket.put(b, new ArrayDeque<>());
        }
        for (StoryCard c : cards) {
            byBucket.get(leans.bucket(c.outlet())).add(c);
        }
        List<StoryCard> out = new ArrayList<>(cards.size());
        while (out.size() < cards.size()) {
            for (Bucket b : order) {
                StoryCard next = byBucket.get(b).poll();
                if (next != null) {
                    out.add(next);
                }
            }
        }
        return out;
    }

    private static Set<String> keywords(Story s) {
        Set<String> out = new HashSet<>();
        for (String w : (s.headline() + " " + s.summary()).toLowerCase(Locale.ROOT).split("[^a-z0-9']+")) {
            if (w.length() > 2 && !STOP.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String w : a) {
            if (b.contains(w)) {
                inter++;
            }
        }
        return (double) inter / (a.size() + b.size() - inter);
    }
}
