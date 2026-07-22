package com.shreddr.shreddr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
public class SystemCleanerServiceImpl implements SystemCleanerService {

    private static final Logger logger = LoggerFactory.getLogger(SystemCleanerServiceImpl.class);
    private final ExecutorService cleanerWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "shreddr-cleaner-worker");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public List<CleanerTarget> scan() {
        Path userHome = Path.of(System.getProperty("user.home"));
        Path localAppData = userHome.resolve("AppData/Local");
        Path roamingAppData = userHome.resolve("AppData/Roaming");
        List<Candidate> candidates = new ArrayList<>(List.of(
                new Candidate("Windows", "Temporary files", Path.of(System.getProperty("java.io.tmpdir")), List.of()),
                new Candidate("Windows", "Windows Prefetch", windowsPath("Prefetch"), List.of()),
                new Candidate("Browsers", "Google Chrome cache", localAppData.resolve("Google/Chrome/User Data/Default/Cache"), List.of("chrome")),
                new Candidate("Browsers", "Google Chrome code cache", localAppData.resolve("Google/Chrome/User Data/Default/Code Cache"), List.of("chrome")),
                new Candidate("Browsers", "Microsoft Edge cache", localAppData.resolve("Microsoft/Edge/User Data/Default/Cache"), List.of("msedge")),
                new Candidate("Browsers", "Microsoft Edge code cache", localAppData.resolve("Microsoft/Edge/User Data/Default/Code Cache"), List.of("msedge")),
                new Candidate("Developer tools", "Visual Studio Code cache", roamingAppData.resolve("Code/Cache"), List.of("code")),
                new Candidate("Developer tools", "Visual Studio Code GPU cache", roamingAppData.resolve("Code/GPUCache"), List.of("code")),
                new Candidate("Developer tools", "Docker cache", localAppData.resolve("Docker/cache"), List.of("docker", "com.docker"))
        ));
        addFirefoxCaches(candidates, roamingAppData.resolve("Mozilla/Firefox/Profiles"));
        addJetBrainsCaches(candidates, localAppData.resolve("JetBrains"));

        List<CleanerTarget> results = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (Files.exists(candidate.path) && Files.isDirectory(candidate.path) && !Files.isSymbolicLink(candidate.path)) {
                Metrics metrics = measure(candidate.path);
                String availability = isApplicationRunning(candidate.processes) ? "Close app first" : "Ready";
                results.add(new CleanerTarget(candidate.category, candidate.name, candidate.path, metrics.items, metrics.bytes, availability));
            }
        }
        return results;
    }

    @Override
    public CompletableFuture<CleanerResult> clean(List<CleanerTarget> targets, CleanerDeletionMode mode,
                                                   Consumer<CleanerProgress> progressListener) {
        List<CleanerTarget> work = List.copyOf(targets);
        return CompletableFuture.supplyAsync(() -> runCleaner(work, mode, progressListener), cleanerWorker);
    }

    private CleanerResult runCleaner(List<CleanerTarget> targets, CleanerDeletionMode mode,
                                     Consumer<CleanerProgress> progressListener) {
        List<String> failures = new ArrayList<>();
        long cleanedBytes = 0;
        int complete = 0;
        Desktop desktop = recycleBinSupported() ? Desktop.getDesktop() : null;

        for (CleanerTarget target : targets) {
            progressListener.accept(new CleanerProgress(complete, targets.size(), "Cleaning " + target.getName() + "…"));
            try {
                if (!Files.exists(target.getPath())) {
                    continue;
                }
                if (mode == CleanerDeletionMode.RECYCLE_BIN) {
                    if (desktop == null) {
                        throw new IOException("The Windows Recycle Bin is not available. Choose normal delete instead.");
                    }
                    if (!desktop.moveToTrash(target.getPath().toFile())) {
                        throw new IOException("Windows could not move this location to the Recycle Bin");
                    }
                } else {
                    deleteNormally(target.getPath());
                }
                complete++;
                cleanedBytes += target.getTotalBytes();
            } catch (Exception error) {
                logger.warn("Could not clean {}", target.getPath(), error);
                failures.add(target.getName() + ": " + error.getMessage());
            }
        }
        String message = failures.isEmpty()
                ? "Finished cleaning " + complete + " location" + (complete == 1 ? "." : "s.")
                : "Finished with " + failures.size() + " location" + (failures.size() == 1 ? " requiring attention." : "s requiring attention.");
        progressListener.accept(new CleanerProgress(complete, targets.size(), message));
        return new CleanerResult(complete, cleanedBytes, failures);
    }

    private static boolean recycleBinSupported() {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    }

    private static void deleteNormally(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void addFirefoxCaches(List<Candidate> candidates, Path profilesRoot) {
        if (!Files.isDirectory(profilesRoot)) return;
        try (Stream<Path> profiles = Files.list(profilesRoot)) {
            profiles.filter(Files::isDirectory).forEach(profile ->
                    candidates.add(new Candidate("Browsers", "Firefox cache — " + profile.getFileName(), profile.resolve("cache2"), List.of("firefox"))));
        } catch (IOException error) {
            logger.debug("Could not enumerate Firefox profiles", error);
        }
    }

    private void addJetBrainsCaches(List<Candidate> candidates, Path jetBrainsRoot) {
        if (!Files.isDirectory(jetBrainsRoot)) return;
        try (Stream<Path> products = Files.list(jetBrainsRoot)) {
            products.filter(Files::isDirectory).forEach(product ->
                    candidates.add(new Candidate("Developer tools", "JetBrains cache — " + product.getFileName(), product.resolve("caches"), List.of("idea", "studio", "pycharm", "webstorm", "clion", "rider"))));
        } catch (IOException error) {
            logger.debug("Could not enumerate JetBrains caches", error);
        }
    }

    private Metrics measure(Path root) {
        long[] values = {0, 0};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!Files.isSymbolicLink(file)) {
                        values[0]++;
                        values[1] += attrs.size();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException error) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException error) {
            logger.debug("Could not measure cleaner target {}", root, error);
        }
        return new Metrics(values[0], values[1]);
    }

    private boolean isApplicationRunning(List<String> processNames) {
        if (processNames.isEmpty()) return false;
        return ProcessHandle.allProcesses().anyMatch(process -> process.info().command()
                .map(command -> {
                    String executable = Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT);
                    return processNames.stream().anyMatch(name -> executable.startsWith(name + ".") || executable.equals(name));
                })
                .orElse(false));
    }

    private static Path windowsPath(String child) {
        String systemRoot = System.getenv("SystemRoot");
        return Path.of(systemRoot == null || systemRoot.isBlank() ? "C:/Windows" : systemRoot).resolve(child);
    }

    @PreDestroy
    void shutdown() {
        cleanerWorker.shutdownNow();
    }

    private record Candidate(String category, String name, Path path, List<String> processes) { }
    private record Metrics(long items, long bytes) { }
}
