package com.shreddr.shreddr.service;

/** A UI-safe snapshot of an active shredding job. */
public record ShredProgress(int completedItems, int totalItems, long processedBytes, long totalBytes, String message, State state) {
    public enum State { RUNNING, COMPLETE, CANCELLED, FAILED }
}
