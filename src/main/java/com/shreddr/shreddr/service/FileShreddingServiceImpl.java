package com.shreddr.shreddr.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class FileShreddingServiceImpl implements FileShreddingService {

    private static final Logger logger = LoggerFactory.getLogger(FileShreddingServiceImpl.class);
    private static final int BUFFER_SIZE = 64 * 1024;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "shreddr-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final SecureRandom random = new SecureRandom();

    @Override
    public CompletableFuture<ShredResult> shred(List<Path> targets, Consumer<ShredProgress> progressListener) {
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A shredding job is already running."));
        }
        cancelRequested.set(false);
        List<Path> copiedTargets = List.copyOf(targets);
        return CompletableFuture.supplyAsync(() -> runJob(copiedTargets, progressListener), worker)
                .whenComplete((result, error) -> running.set(false));
    }

    @Override
    public void cancelCurrentOperation() {
        cancelRequested.set(true);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private ShredResult runJob(List<Path> requestedTargets, Consumer<ShredProgress> listener) {
        List<String> failures = new ArrayList<>();
        try {
            WorkPlan plan = createPlan(requestedTargets, failures);
            if (plan.files.isEmpty() && plan.directories.isEmpty()) {
                listener.accept(new ShredProgress(0, 0, 0, 0, "Nothing usable was found in the selected locations.", ShredProgress.State.COMPLETE));
                return new ShredResult(0, 0, false, failures);
            }

            int complete = 0;
            long processedBytes = 0;
            listener.accept(new ShredProgress(0, plan.files.size(), 0, plan.totalBytes, "Preparing secure overwrite…", ShredProgress.State.RUNNING));

            for (Path file : plan.files) {
                checkCancelled();
                String displayName = file.getFileName() == null ? file.toString() : file.getFileName().toString();
                try {
                    long size = Files.isSymbolicLink(file) ? 0 : Files.size(file);
                    listener.accept(new ShredProgress(complete, plan.files.size(), processedBytes, plan.totalBytes,
                            "Securely erasing " + displayName, ShredProgress.State.RUNNING));
                    shredFile(file, size, listener, complete, plan.files.size(), processedBytes, plan.totalBytes, displayName);
                    processedBytes += size;
                    complete++;
                } catch (CancellationException cancelled) {
                    throw cancelled;
                } catch (Exception error) {
                    failures.add(displayName + ": " + error.getMessage());
                    logger.warn("Could not shred {}", file, error);
                }
            }

            for (Path directory : plan.directories) {
                checkCancelled();
                try {
                    Files.deleteIfExists(directory);
                } catch (IOException error) {
                    failures.add(directory.getFileName() + ": " + error.getMessage());
                }
            }

            String message = failures.isEmpty()
                    ? "Completed secure erasure of " + complete + " item" + (complete == 1 ? "." : "s.")
                    : "Completed with " + failures.size() + " item" + (failures.size() == 1 ? " requiring attention." : "s requiring attention.");
            listener.accept(new ShredProgress(complete, plan.files.size(), processedBytes, plan.totalBytes, message, ShredProgress.State.COMPLETE));
            return new ShredResult(complete, processedBytes, false, failures);
        } catch (CancellationException cancelled) {
            listener.accept(new ShredProgress(0, 0, 0, 0, "Cancellation requested. Remaining items were left untouched.", ShredProgress.State.CANCELLED));
            return new ShredResult(0, 0, true, failures);
        } catch (Exception error) {
            logger.error("Shredding job failed", error);
            listener.accept(new ShredProgress(0, 0, 0, 0, "The shredding job could not be completed.", ShredProgress.State.FAILED));
            failures.add(error.getMessage());
            return new ShredResult(0, 0, false, failures);
        }
    }

    private WorkPlan createPlan(List<Path> requestedTargets, List<String> failures) throws IOException {
        Set<Path> normalized = new LinkedHashSet<>();
        for (Path path : requestedTargets) {
            if (path != null && Files.exists(path)) {
                normalized.add(path.toAbsolutePath().normalize());
            }
        }
        List<Path> roots = normalized.stream()
                .filter(path -> normalized.stream().noneMatch(other -> !path.equals(other) && path.startsWith(other)))
                .toList();
        List<Path> files = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        long[] totalBytes = {0};

        for (Path root : roots) {
            if (Files.isSymbolicLink(root) || Files.isRegularFile(root)) {
                files.add(root);
                if (!Files.isSymbolicLink(root)) {
                    totalBytes[0] += Files.size(root);
                }
                continue;
            }
            if (!Files.isDirectory(root)) {
                failures.add(root + ": not a regular file or directory");
                continue;
            }
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    directories.add(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    files.add(file);
                    if (!Files.isSymbolicLink(file)) {
                        totalBytes[0] += attrs.size();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException error) {
                    failures.add(file + ": " + error.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        directories.sort(Comparator.comparingInt(Path::getNameCount).reversed());
        return new WorkPlan(files, directories, totalBytes[0]);
    }

    private void shredFile(Path file, long size, Consumer<ShredProgress> listener, int completed, int total,
                           long processedBytes, long totalBytes, String displayName) throws IOException {
        if (Files.isSymbolicLink(file)) {
            Files.deleteIfExists(file);
            return;
        }
        overwrite(file, size, (byte) 0x00, false, listener, completed, total, processedBytes, totalBytes, displayName, "pass 1 of 3");
        overwrite(file, size, (byte) 0xFF, false, listener, completed, total, processedBytes, totalBytes, displayName, "pass 2 of 3");
        overwrite(file, size, (byte) 0x00, true, listener, completed, total, processedBytes, totalBytes, displayName, "pass 3 of 3");

        // Cancellation takes effect between files. Once all three passes finish,
        // complete this file's cleanup so an opaque renamed file is not left behind.
        checkCancelled();
        Path renamed = renameToRandomName(file);
        Files.delete(renamed);
    }

    private void overwrite(Path file, long size, byte value, boolean useRandom, Consumer<ShredProgress> listener,
                           int completed, int total, long processedBytes, long totalBytes, String displayName, String pass) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        if (!useRandom) {
            Arrays.fill(buffer, value);
        }
        try (RandomAccessFile handle = new RandomAccessFile(file.toFile(), "rws")) {
            long written = 0;
            while (written < size) {
                checkCancelled();
                if (useRandom) {
                    random.nextBytes(buffer);
                }
                int amount = (int) Math.min(buffer.length, size - written);
                handle.write(buffer, 0, amount);
                written += amount;
                if (written == size || written % (1024 * 1024) == 0) {
                    listener.accept(new ShredProgress(completed, total, processedBytes + written, totalBytes,
                            "Erasing " + displayName + " — " + pass, ShredProgress.State.RUNNING));
                }
            }
            handle.getFD().sync();
        }
    }

    private Path renameToRandomName(Path file) throws IOException {
        Path parent = file.getParent();
        for (int attempt = 0; attempt < 10; attempt++) {
            Path candidate = parent.resolve(UUID.randomUUID().toString());
            try {
                return Files.move(file, candidate, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Extremely unlikely; choose another opaque name.
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                return Files.move(file, candidate);
            }
        }
        throw new IOException("Could not create an obfuscated file name");
    }

    private void checkCancelled() {
        if (cancelRequested.get()) {
            throw new CancellationException();
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    private record WorkPlan(List<Path> files, List<Path> directories, long totalBytes) {
    }
}
