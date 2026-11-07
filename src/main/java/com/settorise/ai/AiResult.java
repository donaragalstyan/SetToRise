package com.settorise.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** What the browser receives. Always labeled AI-generated. */
public record AiResult(String status, String label, String model, String message, Framings framings) {

    public static final String LABEL = "AI-generated from the headline and outlet summary only. "
            + "It may be incomplete or wrong. Read the original story.";

    public static AiResult ok(String model, Framings f) {
        return new AiResult("ok", LABEL, model, null, f);
    }

    public static AiResult disabled() {
        return new AiResult("disabled", LABEL, null, "AI summaries are off (no ANTHROPIC_API_KEY set).", null);
    }

    public static AiResult unavailable(String message) {
        return new AiResult("unavailable", LABEL, null, message, null);
    }

    @JsonIgnore
    public boolean isOk() {
        return "ok".equals(status);
    }
}
