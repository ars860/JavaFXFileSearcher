package com.example.logsearcher;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.control.IndexRange;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ReadFileService extends Service<ReadFileService.HighlightedString> {
    private static final int PORTION_SIZE = 1000;
    private Path filePath;
    private String text;
    private final Object lock;

    public ReadFileService(Object lock) {
        this.lock = lock;
    }

    public ReadFileService withArgs(Path filePath, String text) {
        this.filePath = filePath;
        this.text = text;
        return this;
    }

    public static boolean isFileMatch(Path file, String text) {
        return text.isEmpty() || !findAllOccurrences(file, text, true, s -> {
        }).isEmpty();
    }

    public static List<IndexRange> findAllOccurrences(Path file, String text, Consumer<HighlightedString> onPortion) {
        return findAllOccurrences(file, text, false, onPortion);
    }

    public static class HighlightedString {
        public String text;
        public List<IndexRange> highlights;

        public HighlightedString(String text, List<IndexRange> highlights) {
            this.text = text;
            this.highlights = highlights;
        }
    }

    private static List<IndexRange> findAllOccurrences(Path file, String text, boolean stopOnFirst, Consumer<HighlightedString> onPortion) {
        int bufferSize = Math.max(text.length(), 1);

        char[] prev_buffer = new char[bufferSize];
        char[] buffer = new char[bufferSize];
        boolean has_prev = false;

        List<IndexRange> occurrences = new ArrayList<>();
        int pos = 0;

        int portionSize = PORTION_SIZE < bufferSize ? 1 : PORTION_SIZE / bufferSize;
        List<String> portion = new ArrayList<>();
        int portionOccurrencesCnt = 0;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            int len;
            while ((len = reader.read(buffer)) != -1) {
                if (has_prev && !text.isEmpty()) {
                    char[] concatBuffer = new char[2 * bufferSize];
                    System.arraycopy(prev_buffer, 0, concatBuffer, 0, bufferSize);
                    System.arraycopy(buffer, 0, concatBuffer, bufferSize, len);
                    String concatString = new String(concatBuffer, 0, bufferSize + len).replaceAll("(\r\n)", "\n").replaceAll("\r", "");
                    int occurrenceIndex = concatString.indexOf(text);
                    if (occurrenceIndex != -1) {
                        String prevStr = new String(prev_buffer).replaceAll("(\r\n)", "\n").replaceAll("\r", "");

                        if (!prevStr.equals(text) || pos == prevStr.length()) {
                            occurrences.add(new IndexRange(pos - prevStr.length() + occurrenceIndex, pos - prevStr.length() + occurrenceIndex + text.length()));
                            portionOccurrencesCnt++;

                            if (stopOnFirst) {
                                return occurrences;
                            }
                        }
                    }
                }

                has_prev = true;
                String bufferString = new String(buffer, 0, len).replaceAll("(\r\n)", "\n").replaceAll("\r", "");
                portion.add(bufferString);

                if (portion.size() == portionSize || len != bufferSize) {
                    onPortion.accept(new HighlightedString(
                            String.join("", portion),
                            occurrences.subList(occurrences.size() - portionOccurrencesCnt, occurrences.size())
                    ));
                    portion.clear();
                    portionOccurrencesCnt = 0;
                }

                System.arraycopy(buffer, 0, prev_buffer, 0, bufferSize);
                pos += bufferString.length();
            }
        } catch (IOException e) {
            // ignore
        }

        onPortion.accept(new HighlightedString(
                String.join("", portion),
                occurrences.subList(occurrences.size() - portionOccurrencesCnt, occurrences.size())
        ));

        return occurrences;
    }

    @Override
    protected Task<HighlightedString> createTask() {
        return new Task<>() {
            @Override
            protected HighlightedString call() {

                try {
                    List<IndexRange> occurrences = findAllOccurrences(filePath, text, portion -> {
                        synchronized (lock) {
                            updateValue(portion);
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                throw new RuntimeInterruptedException();
                            }
                        }
                    });

                    return new HighlightedString(null, occurrences);
                } catch (RuntimeInterruptedException e) {
                    return null;
                }
            }
        };
    }
}
