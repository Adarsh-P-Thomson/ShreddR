package com.shreddr.shreddr.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Finds and removes disposable, application-owned cache locations without secure overwriting. */
public interface SystemCleanerService {
    List<CleanerTarget> scan();

    CompletableFuture<CleanerResult> clean(List<CleanerTarget> targets, CleanerDeletionMode mode,
                                            Consumer<CleanerProgress> progressListener);
}
