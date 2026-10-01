package com.khack.review.common.json;

import tools.jackson.databind.json.JsonMapper;

public final class Json {

    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }
}
