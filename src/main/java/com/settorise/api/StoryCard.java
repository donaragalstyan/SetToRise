package com.settorise.api;

import java.time.Instant;
import java.util.List;

/** A story as sent to the browser. The headline is never edited and the outlet is always credited. */
public record StoryCard(
        String id,
        String headline,
        String outlet,
        String outletLean,
        String summary,
        String link,
        Instant publishedAt,
        List<String> tabs,
        List<String> topics,
        boolean electionRelated,
        String imageUrl) {
}
