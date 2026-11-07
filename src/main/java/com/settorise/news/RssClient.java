package com.settorise.news;

import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.settorise.config.AppConfig.RssSource;
import org.jdom2.Element;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Reads headlines from RSS with Rome. Uses only title, link, date, the feed's description and the
 * feed's own thumbnail URL (Media RSS media:content / media:thumbnail, or an image enclosure).
 * The content:encoded module (full article body in some feeds) is deliberately ignored.
 */
@Component
public class RssClient {

    private static final String MEDIA_NS = "http://search.yahoo.com/mrss/";

    public List<Story> fetch(RssSource source) throws Exception {
        SyndFeed feed;
        try (InputStream in = FeedHttp.get(source.url(), "application/rss+xml, application/xml, text/xml")) {
            SyndFeedInput input = new SyndFeedInput();
            input.setAllowDoctypes(false);
            feed = input.build(new XmlReader(in));
        }
        List<Story> out = new ArrayList<>();
        for (SyndEntry e : feed.getEntries()) {
            String link = e.getLink();
            String title = TextUtil.cleanHeadline(e.getTitle());
            if (link == null || link.isBlank() || title.isEmpty()) {
                continue;
            }
            String desc = e.getDescription() == null ? "" : e.getDescription().getValue();
            Date date = e.getPublishedDate() != null ? e.getPublishedDate() : e.getUpdatedDate();
            Instant published = date == null ? null : date.toInstant();
            out.add(new Story(TextUtil.idFor(link), title, source.outlet(),
                    TextUtil.cleanSummary(desc), link, published, source.section(), imageFor(e)));
        }
        return out;
    }

    /** The feed's own image for an entry, or null. Rome leaves Media RSS tags in the foreign markup. */
    static String imageFor(SyndEntry e) {
        String found = null;
        for (Element el : e.getForeignMarkup()) {
            found = firstMediaImage(el);
            if (found != null) {
                break;
            }
        }
        if (found == null) {
            for (SyndEnclosure enc : e.getEnclosures()) {
                if (enc.getType() != null && enc.getType().startsWith("image/")) {
                    found = enc.getUrl();
                    break;
                }
            }
        }
        return ImageUrls.clean(found);
    }

    private static String firstMediaImage(Element el) {
        if (!MEDIA_NS.equals(el.getNamespaceURI())) {
            return null;
        }
        switch (el.getName()) {
            case "content" -> {
                String type = el.getAttributeValue("type", "");
                String medium = el.getAttributeValue("medium", "");
                boolean image = type.startsWith("image/") || medium.equals("image") || (type.isEmpty() && medium.isEmpty());
                return image ? el.getAttributeValue("url") : null;
            }
            case "thumbnail" -> {
                return el.getAttributeValue("url");
            }
            case "group" -> {
                for (Element child : el.getChildren()) {
                    String url = firstMediaImage(child);
                    if (url != null) {
                        return url;
                    }
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }
}
