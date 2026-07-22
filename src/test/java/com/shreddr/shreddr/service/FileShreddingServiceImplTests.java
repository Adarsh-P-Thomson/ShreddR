package com.shreddr.shreddr.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileShreddingServiceImplTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void securelyRemovesFilesInsideASelectedFolder() throws Exception {
        Path folder = Files.createDirectory(temporaryDirectory.resolve("to-remove"));
        Path nested = Files.createDirectory(folder.resolve("nested"));
        Files.writeString(folder.resolve("notes.txt"), "sensitive content");
        Files.writeString(nested.resolve("token.txt"), "another secret");

        FileShreddingServiceImpl service = new FileShreddingServiceImpl();
        try {
            ShredResult result = service.shred(List.of(folder), progress -> { }).get(10, TimeUnit.SECONDS);
            assertFalse(result.cancelled());
            assertTrue(result.failures().isEmpty());
            assertFalse(Files.exists(folder));
            assertTrue(result.shreddedItems() == 2);
        } finally {
            service.shutdown();
        }
    }
}
