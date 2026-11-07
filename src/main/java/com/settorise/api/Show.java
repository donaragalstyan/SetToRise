package com.settorise.api;

import java.util.Locale;

/** The only thing the server ever learns about the reader: which framing(s) they asked to see. */
public enum Show {
    LEFT, RIGHT, BOTH;

    public static Show parse(String raw) {
        if (raw == null) {
            return BOTH;
        }
        try {
            return Show.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BOTH;
        }
    }
}
