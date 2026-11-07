package com.settorise.news;

import com.settorise.config.AppConfig.Section;

import java.time.Instant;

/**
 * Everything we store about a story: headline (unedited), outlet, the feed's own summary,
 * link to the original, date and the feed's own thumbnail URL (or null). The id is a hash of the link.
 * The section is the feed it came from. The image itself is never downloaded or stored: the browser
 * loads it straight from the outlet.
 */
public record Story(
        String id,
        String headline,
        String outlet,
        String summary,
        String link,
        Instant publishedAt,
        Section section,
        String imageUrl) {
}
