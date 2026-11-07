package com.settorise.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Result of choosing an other-side article. Not sent to the browser as is. */
public record PickResult(String status, Pick pick) {

    public static PickResult ok(Pick p) {
        return new PickResult("ok", p);
    }

    public static PickResult unavailable(String message) {
        return new PickResult("unavailable", null);
    }

    @JsonIgnore
    public boolean isOk() {
        return "ok".equals(status);
    }
}
