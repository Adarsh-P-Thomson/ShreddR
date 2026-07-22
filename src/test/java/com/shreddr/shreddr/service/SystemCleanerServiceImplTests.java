package com.shreddr.shreddr.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemCleanerServiceImplTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void normalCleanupDeletesTheCacheFolderWithoutUsingTheShredder() throws Exception {
        Path cache = Files.createDirectory(temporaryDirectory.resolve("cache"));
        Files.writeString(cache.resolve("index.db"), "cache content");
        CleanerTarget target = new CleanerTarget("Test", "Test cache", cache, 1, 13, "Ready");

        SystemCleanerServiceImpl service = new SystemCleanerServiceImpl();
        try {
            CleanerResult result = service.clean(List.of(target), CleanerDeletionMode.NORMAL_DELETE, progress -> { })
                    .get(10, TimeUnit.SECONDS);
            assertTrue(result.failures().isEmpty());
            assertTrue(result.cleanedLocations() == 1);
            assertFalse(Files.exists(cache));
        } finally {
            service.shutdown();
        }
    }
}
