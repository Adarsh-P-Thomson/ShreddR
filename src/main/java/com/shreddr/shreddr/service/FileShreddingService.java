package com.shreddr.shreddr.service;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Securely removes files and directory contents without blocking the UI thread. */
public interface FileShreddingService {

    CompletableFuture<ShredResult> shred(List<Path> targets, Consumer<ShredProgress> progressListener);

    void cancelCurrentOperation();

    boolean isRunning();
}
