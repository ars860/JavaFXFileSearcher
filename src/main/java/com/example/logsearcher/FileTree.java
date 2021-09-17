package com.example.logsearcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileTree {
    public FileTree(Path file) {
        path = file;
    }

    static public class FileEntry {
        private final String name;
        private final Path path;

        public Path getPath() {
            return path;
        }

        @Override
        public String toString() {
            return name;
        }

        public FileEntry(String name, Path path) {
            this.name = name;
            this.path = path;
        }
    }
    public FileEntry toFileEntry() {
        return new FileEntry(path.getFileName().toString(), path);
    }

    private Path path;
    private final List<FileTree> children = new ArrayList<>();

    public String getName() {
        return path.getFileName().toString();
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public List<FileTree> getChildren() {
        return children;
    }

    public FileTree() {
    }

    public void addChild(FileTree tree) {
        children.add(tree);
    }

    public void addChild(Path path) {
        children.add(new FileTree(path));
    }
}
