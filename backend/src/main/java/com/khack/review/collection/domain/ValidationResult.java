package com.khack.review.collection.domain;

import java.util.List;

public record ValidationResult(List<String> errors, List<String> warnings) {
}
