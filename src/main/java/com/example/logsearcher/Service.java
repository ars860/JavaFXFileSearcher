package com.example.logsearcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Service {

    public FileTree findFilesWithText(Path file) {
        return findFilesWithText(file, "", Collections.emptySet());
    }

    public FileTree findFilesWithText(Path file, String text, Set<String> extensions) {
        if (!Files.exists(file)) {
            return new FileTree();
        }

        return walk(file, text, extensions);
    }

    private FileTree walk(Path file, String text, Set<String> extensions) {
        FileTree res = new FileTree(file);
        try (Stream<Path> paths = Files.list(file)) {
            for (Path ch : paths.collect(Collectors.toList())) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (Files.isRegularFile(ch)) {
                    if (isExtensionMatch(ch, extensions) && isFileMatch(ch, text)) {
                        res.addChild(ch);
                    }
                } else {
                    FileTree walkedChild = walk(ch, text, extensions);
                    if (walkedChild.getChildren().size() > 0) {
                        res.addChild(walkedChild);
                    }
                }
            }
        } catch (IOException e) {}

        return res;
    }

    private boolean isExtensionMatch(Path file, Set<String> extensions) {
        String fileName = file.toString();
        String ext = Optional.of(fileName)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(fileName.lastIndexOf(".") + 1))
                .orElse(null);

        return ext != null && (extensions.isEmpty() || extensions.contains(ext));
    }

    private boolean isFileMatch(Path file, String text) {
        try {
            String fileContent = Files.readString(file);
            return fileContent.contains(text);
        } catch (IOException e) {
            return false;
        }
    }
}
