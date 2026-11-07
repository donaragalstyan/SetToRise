package com.settorise.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Outlet lean labels from an editable JSON file. The bundled file is a PLACEHOLDER.
 * Point OUTLET_LEANS_FILE at a licensed ratings file to override it.
 * Used only to mix outlets in the feed and in related coverage. Never shown as fact.
 */
@Component
public class OutletLeans {

    private static final Logger log = LoggerFactory.getLogger(OutletLeans.class);

    /** Coarse bucket used for mixing: left, center or right. */
    public enum Bucket { LEFT, CENTER, RIGHT }

    private final Map<String, String> labels = new HashMap<>();
    private final boolean placeholder;

    public OutletLeans(ObjectMapper mapper, @Value("${OUTLET_LEANS_FILE:}") String overridePath) throws Exception {
        JsonNode root;
        if (overridePath != null && !overridePath.isBlank()) {
            try (InputStream in = Files.newInputStream(Path.of(overridePath))) {
                root = mapper.readTree(in);
            }
            log.info("Loaded outlet leans from {}", overridePath);
        } else {
            try (InputStream in = new ClassPathResource("outlet-leans.json").getInputStream()) {
                root = mapper.readTree(in);
            }
        }
        placeholder = root.has("_PLACEHOLDER");
        if (placeholder) {
            log.warn("Outlet lean labels are PLACEHOLDERS. Replace with licensed ratings before public release.");
        }
        root.path("outlets").fields().forEachRemaining(e -> labels.put(e.getKey(), e.getValue().asText()));
    }

    /** The raw label (e.g. "lean-left"), or "unrated". */
    public String label(String outlet) {
        return labels.getOrDefault(outlet, "unrated");
    }

    public Bucket bucket(String outlet) {
        String l = label(outlet);
        if (l.endsWith("left")) {
            return Bucket.LEFT;
        }
        if (l.endsWith("right")) {
            return Bucket.RIGHT;
        }
        return Bucket.CENTER;
    }

    public boolean isPlaceholder() {
        return placeholder;
    }
}
