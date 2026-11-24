package io.github.kkskkkcc.englishmc.client.speech;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.zip.ZipInputStream;

final class SpeechFiles {
    private SpeechFiles() {}

    static Path instanceCache(Path gameDirectory) {
        return gameDirectory.toAbsolutePath().normalize().resolve("englishmc-cache");
    }

    static Path modelDirectory(Path gameDirectory, String name) {
        Path instance = instanceCache(gameDirectory).resolve(name);
        if (Files.isRegularFile(instance.resolve(".ready"))) return instance;
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            Path legacy = Path.of(localAppData).resolve("EnglishMC").resolve(name);
            if (Files.isRegularFile(legacy.resolve(".ready"))) return legacy;
        }
        return instance;
    }

    static Path install(Path destination, Installer installer) throws IOException {
        if (Files.isRegularFile(destination.resolve(".ready"))) return destination;
        Files.createDirectories(destination.getParent());
        Path staging = Files.createTempDirectory(destination.getParent(), "speech-install-");
        try {
            installer.extract(staging);
            Files.createFile(staging.resolve(".ready"));
            try {
                move(staging, destination);
            } catch (IOException error) {
                if (!Files.isRegularFile(destination.resolve(".ready"))) throw error;
            }
        } finally {
            if (Files.exists(staging)) {
                try (var children = Files.walk(staging)) {
                    for (Path path : children.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
        return destination;
    }

    static void unpack(ZipInputStream zip, Path directory, Path expectedRoot) throws IOException {
        for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            Path target = directory.resolve(entry.getName()).normalize();
            if (!target.startsWith(expectedRoot)) throw new IOException("Unexpected speech archive path");
            if (entry.isDirectory()) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(zip, target);
            }
        }
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(source, destination);
        }
    }

    @FunctionalInterface
    interface Installer {
        void extract(Path staging) throws IOException;
    }
}
