package com.settorise.news;

import java.net.URI;
import java.util.Locale;

/** Accepts only plain http(s) image URLs from feeds. Anything else becomes null (no image). */
final class ImageUrls {

    private ImageUrls() { }

    /**
     * Some feeds (MS NOW) repeat the host in the path, e.g. https://www.ms.now/www.ms.now/wp-content/...,
     * which returns 404. Drop the repeated host so the image loads.
     */
    static URI fixDoubledHost(URI uri) {
        String host = uri.getHost();
        String path = uri.getRawPath();
        if (host != null && path != null && path.startsWith("/" + host + "/")) {
            String fixed = uri.getScheme() + "://" + uri.getRawAuthority() + path.substring(host.length() + 1)
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            return URI.create(fixed);
        }
        return uri;
    }

    static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String url = raw.trim();
        if (url.isEmpty() || url.length() > 2000) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("https") && !scheme.equals("http")) || uri.getHost() == null) {
                return null;
            }
            return fixDoubledHost(uri).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
