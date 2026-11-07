package com.settorise.news;

import com.settorise.config.AppConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

final class TextUtil {

    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** WordPress footer that some feeds add to every description. It is boilerplate, not a summary. */
    private static final Pattern WP_FOOTER = Pattern.compile("(?is)<p>\\s*The post .*? appeared first on .*$");

    private TextUtil() { }

    /** Turns a feed description into a short plain-text teaser. Never used on article bodies. */
    static String cleanSummary(String html) {
        if (html == null) {
            return "";
        }
        String text = WP_FOOTER.matcher(html).replaceAll("");
        text = TAGS.matcher(text).replaceAll(" ");
        text = decodeEntities(text);
        text = SPACES.matcher(text).replaceAll(" ").trim();
        if (text.length() > AppConfig.MAX_SUMMARY_CHARS) {
            int cut = text.lastIndexOf(' ', AppConfig.MAX_SUMMARY_CHARS);
            if (cut < AppConfig.MAX_SUMMARY_CHARS / 2) {
                cut = AppConfig.MAX_SUMMARY_CHARS;
            }
            text = text.substring(0, cut).trim() + "…";
        }
        return text;
    }

    /** Headlines are shown as published. We only decode HTML entities and collapse whitespace. */
    static String cleanHeadline(String raw) {
        if (raw == null) {
            return "";
        }
        return SPACES.matcher(decodeEntities(raw)).replaceAll(" ").trim();
    }

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    static String decodeEntities(String s) {
        String out = NUMERIC_ENTITY.matcher(s).replaceAll(m -> {
            try {
                int cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
                return java.util.regex.Matcher.quoteReplacement(new String(Character.toChars(cp)));
            } catch (IllegalArgumentException e) {
                return java.util.regex.Matcher.quoteReplacement(m.group());
            }
        });
        return out.replace("&nbsp;", " ").replace("&apos;", "'").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    static String idFor(String link) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(link.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
