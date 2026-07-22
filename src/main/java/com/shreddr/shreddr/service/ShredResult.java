package com.shreddr.shreddr.service;

import java.util.List;

public record ShredResult(int shreddedItems, long shreddedBytes, boolean cancelled, List<String> failures) {
    public ShredResult {
        failures = List.copyOf(failures);
    }
}
