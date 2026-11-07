package com.settorise.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Structured output: which other-side article best answers "what does the other side say about this?". */
public record Pick(
        @JsonPropertyDescription("The number of the chosen candidate article (1-based). Always choose one.")
        int choice,
        @JsonPropertyDescription("How the chosen article relates to the story: \"same_event\" if it reports the same "
                + "event or development, \"same_issue\" if it covers the same issue or people from another angle, "
                + "or \"loosely_related\" if nothing closer exists.")
        String relation,
        @JsonPropertyDescription("One neutral sentence telling the reader why this article is relevant to the story.")
        String reason) {
}
