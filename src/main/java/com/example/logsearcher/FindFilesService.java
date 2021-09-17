package com.example.logsearcher;

import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.example.logsearcher.ReadFileService.isFileMatch;

public class FindFilesService extends Service<List<FileTree.FileEntry>> {
    private Path path;
    private Set<String> extensions;
    private String text;
    private final Object lock;

    public FindFilesService(Object lock) {
        this.lock = lock;
    }

    public void load(Path path, String text, Set<String> extensions) {
        this.path = path;
        this.extensions = extensions;
        this.text = text.replaceAll("(\r\n)|\n|\r", "\n");
    }

    @Override
    protected Task<List<FileTree.FileEntry>> createTask() {
        return new Task<List<FileTree.FileEntry>>() {
            private final Path path = FindFilesService.this.path.toAbsolutePath();
            private final Set<String> extensions = new HashSet<>(FindFilesService.this.extensions);
            private final String text = FindFilesService.this.text;

            private final List<FileTree.FileEntry> files = new ArrayList<>();
            private static final int emitFileCount = 10;

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
                        if (isCancelled()) {
                            break;
                        }

                        if (Files.isRegularFile(ch)) {
                            if (isExtensionMatch(ch, extensions) && isFileMatch(ch, text)) {
                                res.addChild(ch);
                                files.add(new FileTree.FileEntry(ch.getFileName().toString(), ch));
                                if (files.size() == emitFileCount) {
                                    synchronized (lock) {
                                        updateValue(new ArrayList<>(files));
                                        lock.wait();
                                        files.clear();
                                    }
                                }
                            }
                        } else {
                            FileTree walkedChild = walk(ch, text, extensions);
                            if (walkedChild.getChildren().size() > 0) {
                                res.addChild(walkedChild);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    // continue
                }

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

            @Override
            protected List<FileTree.FileEntry> call() {
                findFilesWithText(path, text, extensions);
                updateValue(new ArrayList<>(files));
                return Collections.emptyList();
            }
        };
    }
}
