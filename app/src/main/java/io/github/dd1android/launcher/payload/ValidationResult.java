package io.github.dd1android.launcher.payload;

import java.util.List;

public record ValidationResult(boolean valid, List<String> errors) {
    public ValidationResult {
        errors = List.copyOf(errors);
    }
}
