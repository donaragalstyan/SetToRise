package com.settorise.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** What the browser receives for a comparison. Always labeled AI-generated. */
public record ComparisonResult(String status, String label, String model, String message, Comparison comparison) {

    public static final String LABEL = "AI-generated from each outlet's headline and summary only. "
            + "It may be incomplete or wrong. Read both original stories.";

    public static ComparisonResult ok(String model, Comparison c) {
        return new ComparisonResult("ok", LABEL, model, null, c);
    }

    public static ComparisonResult disabled() {
        return new ComparisonResult("disabled", LABEL, null, "AI summaries are off (no ANTHROPIC_API_KEY set).", null);
    }

    public static ComparisonResult unavailable(String message) {
        return new ComparisonResult("unavailable", LABEL, null, message, null);
    }

    @JsonIgnore
    public boolean isOk() {
        return "ok".equals(status);
    }
}
