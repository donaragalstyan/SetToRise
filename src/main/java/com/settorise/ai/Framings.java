package com.settorise.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** The structured output schema Claude fills in. Derived automatically by the Java SDK. */
public record Framings(
        @JsonPropertyDescription("2-3 neutral sentences on what the story reports, attributing claims to the outlet.")
        String neutralSummary,
        @JsonPropertyDescription("Framing for a reader whose views lean left.")
        LeanFraming left,
        @JsonPropertyDescription("Framing for a reader whose views lean right.")
        LeanFraming right) {

    public record LeanFraming(
            @JsonPropertyDescription("2-3 sentences, addressed to 'you', on practical ways this story may affect a reader with this lean, tied to the concerns such readers commonly prioritize.")
            String howThisMayAffectYou,
            @JsonPropertyDescription("2-4 sentences: the strongest good-faith case from the OPPOSITE side, written so people who hold that view would recognize it as fair.")
            String strongestOpposingCase) {
    }
}
