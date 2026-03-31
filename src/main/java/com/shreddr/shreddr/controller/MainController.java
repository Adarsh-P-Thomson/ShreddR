package com.shreddr.shreddr.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class MainController {

    @FXML
    private StackPane dropZone;

    @FXML
    private Label statusLabel;

    @FXML
    public void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        }
        event.consume();
    }

    @FXML
    public void handleDragDropped(DragEvent event) {
        boolean success = false;
        if (event.getDragboard().hasFiles()) {
            List<File> files = event.getDragboard().getFiles();
            handleFiles(files);
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }

    @FXML
    public void handleBrowseFiles(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files to Shred");
        List<File> files = fileChooser.showOpenMultipleDialog(dropZone.getScene().getWindow());

        if (files != null && !files.isEmpty()) {
            handleFiles(files);
        }
    }

    private void handleFiles(List<File> files) {
        // Output for now since shredding logic isn't there yet
        int count = files.size();
        statusLabel.setText("Selected " + count + " file(s) for shredding.");
    }
}
