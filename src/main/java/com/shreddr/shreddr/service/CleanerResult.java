package com.shreddr.shreddr.service;

import java.util.List;

public record CleanerResult(int cleanedLocations, long cleanedBytes, List<String> failures) {
    public CleanerResult {
        failures = List.copyOf(failures);
    }
}
