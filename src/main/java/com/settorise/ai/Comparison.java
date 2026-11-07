package com.settorise.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Structured output for "how two outlets cover the same story". Derived automatically by the Java SDK. */
public record Comparison(
        @JsonPropertyDescription("True only if both items clearly report the same event or development. "
                + "False if they are merely on a related topic.")
        boolean sameEvent,
        @JsonPropertyDescription("1-2 neutral sentences on what both outlets report, attributing claims to them. "
                + "If sameEvent is false, one sentence saying the items cover different events.")
        String bothReport,
        @JsonPropertyDescription("2-4 short points on how the coverage differs: emphasis, framing, word choice, "
                + "or which details each includes or leaves out. Name the outlet in each point. "
                + "Empty if sameEvent is false.")
        List<String> differences) {
}
