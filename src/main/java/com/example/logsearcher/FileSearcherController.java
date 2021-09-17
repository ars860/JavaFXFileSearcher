package com.example.logsearcher;


import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.effect.BlendMode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import org.fxmisc.richtext.Caret;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.Selection;
import org.fxmisc.richtext.SelectionImpl;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FileSearcherController {
    // make interface
    private final DirectoryChooser fileChooser = new DirectoryChooser();
    private final Object fileAddLock = new Object();
    private final Object readFileLock = new Object();
    private final FindFilesService findFilesService = new FindFilesService(fileAddLock);
    private final ReadFileService readFileService = new ReadFileService(readFileLock);
    private int fileCount = 0;
    private int currentOccurrenceIndex = -1;
    private List<IndexRange> occurrences = new ArrayList<>();

    @FXML
    public Button cancelButton;

    @FXML
    public TextField extInput;

    @FXML
    public TextArea textInput;

    @FXML
    private TextField explorerInput;

    @FXML
    private TreeView<FileTree.FileEntry> treeView;

    @FXML
    private InlineCssTextArea fileContent;
    private final List<Selection<String, String, String>> selections = new ArrayList<>();

    @FXML
    private ProgressIndicator spinner;

    @FXML
    public Text fileCountText;

    public FileSearcherController() {
        findFilesService.valueProperty().addListener((o, old, files) -> {
            if (files == null) {
                return;
            }

            synchronized (fileAddLock) {
                fileCount += files.size();
                for (FileTree.FileEntry entry : files) {
                    addEntry(entry);
                }
                fileCountText.setText(Integer.toString(fileCount));
                fileAddLock.notifyAll();
            }
        });
        readFileService.valueProperty().addListener((o, old, content) -> {
            if (content == null) {
                return;
            }
            if (content.text == null) {
                occurrences = content.highlights;
                return;
            }

            synchronized (readFileLock) {
                fileContent.appendText(content.text);

                for (IndexRange range : content.highlights) {
                    Selection<String, String, String> extraSelection = new SelectionImpl<>("selection_" + range.getStart() + "_" + range.getEnd(), fileContent,
                            path -> {
                                path.setStrokeWidth(0);
                                path.setFill(Color.YELLOW);
                                path.setBlendMode(BlendMode.BLUE);
                            }
                    );
                    selections.add(extraSelection);
                    if (!fileContent.addSelection(extraSelection)) {
                        throw new IllegalStateException("selection was not added to area");
                    }
                    extraSelection.selectRange(range.getStart(), range.getEnd());
                }

                readFileLock.notifyAll();
            }
        });
        findFilesService.setOnSucceeded(f -> {
            cancelButton.setVisible(false);
            spinner.setVisible(false);
        });
    }

    @FXML
    private void onExploreClick() {
        File file = new File(explorerInput.getText());
        if (file.exists()) {
            fileChooser.setInitialDirectory(file);
        }
        File chosenFile = fileChooser.showDialog(explorerInput.getScene().getWindow());
        if (chosenFile != null) {
            explorerInput.setText(chosenFile.getAbsolutePath());
            spinner.setVisible(true);
            cancelButton.setVisible(true);
            treeView.setRoot(new TreeItem<>(new FileTree.FileEntry(chosenFile.toPath().toString(), chosenFile.toPath())));
            treeView.getRoot().setExpanded(true);
            fileCount = 0;
            clearFileContentArea();
            fileCountText.setText(Integer.toString(fileCount));

            findFilesService.load(chosenFile.toPath(), textInput.getText(), getExtensions());
            findFilesService.restart();
        }
    }

    @FXML
    private void onCancelClick() {
        findFilesService.cancel();
        cancelButton.setVisible(false);
        spinner.setVisible(false);
    }

    private void clearFileContentArea() {
        fileContent.replaceText("");
        for (var selection : selections) {
            fileContent.removeSelection(selection);
            selection.dispose();
        }
        selections.clear();
        currentOccurrenceIndex = -1;
        occurrences.clear();
        fileContent.setShowCaret(Caret.CaretVisibility.OFF);
    }

    public void initialize() {
        treeView.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            if (selected == null) {
                return;
            }

            Path selectedFile = selected.getValue().getPath();
            if (Files.isRegularFile(selectedFile)) {
                clearFileContentArea();
                readFileService.withArgs(selectedFile, textInput.getText()).restart();
            }
        });
    }

    private Set<String> getExtensions() {
        return Arrays.stream(extInput.getText().split(" "))
                .filter(s -> !s.isEmpty())
                .map(s -> s.replaceAll("\\.", ""))
                .collect(Collectors.toSet());
    }

    private void addEntry(FileTree.FileEntry file) {
        TreeItem<FileTree.FileEntry> root = treeView.getRoot();

        if (root == null) {
            root = new TreeItem<>(new FileTree.FileEntry(file.getPath().toString(), file.getPath()));
            treeView.setRoot(root);
            return;
        }

        while (file.getPath().startsWith(root.getValue().getPath())) {
            boolean nextFound = false;
            for (TreeItem<FileTree.FileEntry> ch : root.getChildren()) {
                if (file.getPath().startsWith(ch.getValue().getPath())) {
                    root = ch;
                    nextFound = true;
                    break;
                }
            }

            if (!nextFound) {
                break;
            }
        }

        Path rest = root.getValue().getPath().relativize(file.getPath());

        for (Path p : rest) {
            TreeItem<FileTree.FileEntry> newChild = new TreeItem<>(new FileTree.FileEntry(p.toString(), root.getValue().getPath().resolve(p)));
            newChild.setExpanded(true);
            root.getChildren().add(newChild);
            root = newChild;
        }
    }

    @FXML
    private void onNextFileClick() {
        selectFile(row -> row + 1, () -> treeView.getRoot().getChildren().get(0));
    }

    @FXML
    private void onPrevFileClick() {
        selectFile(row -> row - 1, () -> {
            TreeItem<FileTree.FileEntry> res = treeView.getRoot().getChildren().get(treeView.getRoot().getChildren().size() - 1);
            while (!res.isLeaf()) {
                res = res.getChildren().get(res.getChildren().size() - 1);
            }
            return res;
        });
    }

    private void selectFile(Function<Integer, Integer> selectSibling, Supplier<TreeItem<FileTree.FileEntry>> selectInitial) {
        if (treeView.getRoot() == null || treeView.getRoot().isLeaf()) {
            return;
        }

        TreeItem<FileTree.FileEntry> selected = treeView.getSelectionModel().selectedItemProperty().get();

        if (selected == null) {
            selected = selectInitial.get();
            treeView.getSelectionModel().select(selected);

            if (selected.isLeaf()) {
                return;
            }
        }

        selected = treeView.getTreeItem(selectSibling.apply(treeView.getRow(selected)));
        while (selected != null && !selected.isLeaf()) {
            selected = treeView.getTreeItem(selectSibling.apply(treeView.getRow(selected)));
        }

        if (selected == null) {
            treeView.getSelectionModel().clearSelection();
            selectFile(selectSibling, selectInitial);
            return;
        }

        treeView.getSelectionModel().select(selected);
    }

    @FXML
    private void onSelectAllClick() {
        fileContent.selectAll();
        Clipboard clipboard = Clipboard.getSystemClipboard();
        clipboard.setContent(Map.of(DataFormat.PLAIN_TEXT, fileContent.getText()));
    }

    @FXML
    public void onNextOccurrenceClick() {
        if (occurrences.isEmpty()) {
            return;
        }

        fileContent.setShowCaret(Caret.CaretVisibility.ON);
        if (currentOccurrenceIndex == -1) {
            currentOccurrenceIndex = 0;
        } else {
            currentOccurrenceIndex++;
            if (currentOccurrenceIndex == occurrences.size()) {
                currentOccurrenceIndex = 0;
            }
        }

        fileContent.moveTo(occurrences.get(currentOccurrenceIndex).getStart());
        fileContent.requestFollowCaret();
        System.out.println(occurrences.get(currentOccurrenceIndex).getStart());
    }

    @FXML
    public void onPrevOccurrenceClick() {
        if (occurrences.isEmpty()) {
            return;
        }

        fileContent.setShowCaret(Caret.CaretVisibility.ON);
        if (currentOccurrenceIndex == -1) {
            currentOccurrenceIndex = occurrences.size() - 1;
        } else {
            currentOccurrenceIndex--;
            if (currentOccurrenceIndex == -1) {
                currentOccurrenceIndex = occurrences.size() - 1;
            }
        }

        fileContent.moveTo(occurrences.get(currentOccurrenceIndex).getStart());
        fileContent.requestFollowCaret();
        System.out.println(occurrences.get(currentOccurrenceIndex).getStart());
    }
}