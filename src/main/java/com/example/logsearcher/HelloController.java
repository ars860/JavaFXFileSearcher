package com.example.logsearcher;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class HelloController {
    // make interface
    private final DirectoryChooser fileChooser = new DirectoryChooser();
    private final Service service = new Service();

    @FXML
    public TextField extInput;

    @FXML
    public TextArea textInput;

    @FXML
    private TextField explorerInput;

    @FXML
    private TreeView<FileTree.FileEntry> treeView;

    @FXML
    private TextArea fileContent;

    @FXML
    private ProgressIndicator spinner;

    @FXML
    private void onExploreClick() throws IOException {
        File file = new File(explorerInput.getText());
        if (file.exists()) {
            fileChooser.setInitialDirectory(file);
        }
        File chosenFile = fileChooser.showDialog(explorerInput.getScene().getWindow());
        if (chosenFile != null) {
            explorerInput.setText(chosenFile.getAbsolutePath());
            spinner.setVisible(true);
            new Thread(() -> {
                FileTree files = service.findFilesWithText(chosenFile.toPath(), textInput.getText(), getExtensions());
                Platform.runLater(() -> {
                    treeView.setRoot(displayTree(files));
                    treeView.getRoot().setExpanded(true);
                    spinner.setVisible(false);
                });
            }).start();
        }
    }

    private TreeItem<FileTree.FileEntry> displayTree(FileTree files) {
        TreeItem<FileTree.FileEntry> root = new TreeItem<>(files.toFileEntry());
        for (FileTree f : files.getChildren()) {
            root.getChildren().add(displayTree(f));
        }
        return root;
    }

    public void initialize() {
        treeView.getSelectionModel().selectedItemProperty().addListener((observable, __, selected) -> {
            if (selected == null) {
                return;
            }

            System.out.println("Selected Text : " + selected.getValue());
            Path selectedFile = selected.getValue().getPath();
            if (Files.isRegularFile(selectedFile)) {
                try {
                    String content = Files.readString(selectedFile);
                    fileContent.setText(content);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        fileContent.setEditable(false);
        extInput.setText(".log");
    }

    private Set<String> getExtensions() {
        return Arrays.stream(extInput.getText().split(" "))
                .filter(s -> !s.isEmpty())
                .map(s -> s.replaceAll("\\.", ""))
                .collect(Collectors.toSet());
    }
}