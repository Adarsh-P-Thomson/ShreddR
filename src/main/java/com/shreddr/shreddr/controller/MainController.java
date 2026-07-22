package com.shreddr.shreddr.controller;

import com.shreddr.shreddr.service.CleanerTarget;
import com.shreddr.shreddr.service.CleanerDeletionMode;
import com.shreddr.shreddr.service.CleanerResult;
import com.shreddr.shreddr.service.FileShreddingService;
import com.shreddr.shreddr.service.ShredProgress;
import com.shreddr.shreddr.service.ShredResult;
import com.shreddr.shreddr.service.SystemCleanerService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.util.StringConverter;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class MainController {

    @FXML private StackPane dropZone;
    @FXML private TableView<QueueEntry> queueTable;
    @FXML private TableColumn<QueueEntry, String> queueNameColumn;
    @FXML private TableColumn<QueueEntry, String> queueTypeColumn;
    @FXML private TableColumn<QueueEntry, String> queueLocationColumn;
    @FXML private TableColumn<QueueEntry, String> queueSizeColumn;
    @FXML private Label queueSummaryLabel;
    @FXML private Label statusLabel;
    @FXML private Label progressLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Button removeButton;
    @FXML private Button clearButton;
    @FXML private Button shredButton;
    @FXML private Button cancelButton;
    @FXML private Button scanButton;
    @FXML private Button cleanButton;
    @FXML private Button selectCleanerButton;
    @FXML private ChoiceBox<CleanerDeletionMode> cleanerModeChoice;
    @FXML private TableView<CleanerTarget> cleanerTable;
    @FXML private TableColumn<CleanerTarget, Boolean> cleanerSelectedColumn;
    @FXML private TableColumn<CleanerTarget, String> cleanerNameColumn;
    @FXML private TableColumn<CleanerTarget, String> cleanerCategoryColumn;
    @FXML private TableColumn<CleanerTarget, String> cleanerItemsColumn;
    @FXML private TableColumn<CleanerTarget, String> cleanerSizeColumn;
    @FXML private TableColumn<CleanerTarget, String> cleanerStatusColumn;
    @FXML private Label cleanerSummaryLabel;

    private final ObservableList<QueueEntry> queue = FXCollections.observableArrayList();
    private final ObservableList<CleanerTarget> cleanerTargets = FXCollections.observableArrayList();
    private final FileShreddingService shreddingService;
    private final SystemCleanerService cleanerService;
    private boolean running;
    private boolean cleanerRunning;

    public MainController(FileShreddingService shreddingService, SystemCleanerService cleanerService) {
        this.shreddingService = shreddingService;
        this.cleanerService = cleanerService;
    }

    @FXML
    private void initialize() {
        configureQueueTable();
        configureCleanerTable();
        queueTable.setItems(queue);
        cleanerTable.setItems(cleanerTargets);
        cleanerModeChoice.setItems(FXCollections.observableArrayList(CleanerDeletionMode.values()));
        cleanerModeChoice.setValue(CleanerDeletionMode.RECYCLE_BIN);
        cleanerModeChoice.setConverter(new StringConverter<>() {
            @Override public String toString(CleanerDeletionMode mode) { return mode == null ? "" : mode.getDisplayName(); }
            @Override public CleanerDeletionMode fromString(String value) { return null; }
        });
        queue.addListener((javafx.collections.ListChangeListener<QueueEntry>) change -> updateQueuePresentation());
        queueTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateButtons());
        updateQueuePresentation();
        updateCleanerButtons();
        statusLabel.setText("Ready. Add files or folders to begin.");
    }

    @FXML
    public void handleDragOver(DragEvent event) {
        if (!running && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    @FXML
    public void handleDragDropped(DragEvent event) {
        boolean accepted = !running && event.getDragboard().hasFiles();
        if (accepted) {
            addPaths(event.getDragboard().getFiles().stream().map(File::toPath).toList());
        }
        event.setDropCompleted(accepted);
        event.consume();
    }

    @FXML
    public void handleBrowseFiles(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add files to the shredding queue");
        List<File> selected = chooser.showOpenMultipleDialog(dropZone.getScene().getWindow());
        if (selected != null) addPaths(selected.stream().map(File::toPath).toList());
    }

    @FXML
    public void handleBrowseFolder(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Add a folder to the shredding queue");
        File selected = chooser.showDialog(dropZone.getScene().getWindow());
        if (selected != null) addPaths(List.of(selected.toPath()));
    }

    @FXML
    public void handleRemoveSelected(ActionEvent event) {
        if (!running) queue.remove(queueTable.getSelectionModel().getSelectedItem());
    }

    @FXML
    public void handleClearQueue(ActionEvent event) {
        if (!running) queue.clear();
    }

    @FXML
    public void handleStartShredding(ActionEvent event) {
        if (queue.isEmpty() || running) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm secure erasure");
        confirmation.setHeaderText("Permanently destroy " + queue.size() + " queued item" + (queue.size() == 1 ? "?" : "s?"));
        confirmation.setContentText("ShreddR will overwrite each file three times before deleting it. This cannot be undone.");
        confirmation.getButtonTypes().setAll(ButtonType.CANCEL, new ButtonType("Securely erase", ButtonBar.ButtonData.OK_DONE));
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL).getButtonData() != ButtonBar.ButtonData.OK_DONE) return;

        running = true;
        progressBar.setProgress(0);
        statusLabel.setText("Preparing secure erasure…");
        updateButtons();
        List<Path> targets = queue.stream().map(QueueEntry::path).toList();
        shreddingService.shred(targets, this::publishProgress).whenComplete(this::finishShredding);
    }

    @FXML
    public void handleCancelShredding(ActionEvent event) {
        if (running) {
            shreddingService.cancelCurrentOperation();
            statusLabel.setText("Stopping after the current safe write…");
            cancelButton.setDisable(true);
        }
    }

    @FXML
    public void handleScanCleaner(ActionEvent event) {
        if (cleanerRunning) return;
        scanButton.setDisable(true);
        cleanerSummaryLabel.setText("Scanning common cache locations…");
        CompletableFuture.supplyAsync(cleanerService::scan).whenComplete((found, error) -> Platform.runLater(() -> {
            scanButton.setDisable(false);
            if (error != null) {
                cleanerSummaryLabel.setText("The cleaner scan could not be completed.");
                return;
            }
            cleanerTargets.setAll(found);
            cleanerTargets.forEach(target -> target.selectedProperty().addListener((observable, oldValue, newValue) -> updateCleanerButtons()));
            long bytes = found.stream().mapToLong(CleanerTarget::getTotalBytes).sum();
            cleanerSummaryLabel.setText(found.isEmpty()
                    ? "No supported cache locations were found on this PC."
                    : found.size() + " cleanable locations found — " + formatBytes(bytes));
            updateCleanerButtons();
        }));
    }

    @FXML
    public void handleSelectAllCleaner(ActionEvent event) {
        if (cleanerRunning) return;
        boolean select = cleanerTargets.stream().anyMatch(target -> !target.isSelected());
        cleanerTargets.forEach(target -> target.setSelected(select));
        cleanerTable.refresh();
        updateCleanerButtons();
    }

    @FXML
    public void handleCleanSelected(ActionEvent event) {
        if (cleanerRunning) return;
        List<Path> selected = cleanerTargets.stream().filter(CleanerTarget::isSelected)
                .filter(target -> target.getAvailability().equals("Ready")).map(CleanerTarget::getPath).toList();
        long blocked = cleanerTargets.stream().filter(CleanerTarget::isSelected)
                .filter(target -> !target.getAvailability().equals("Ready")).count();
        if (selected.isEmpty()) {
            cleanerSummaryLabel.setText(blocked > 0
                    ? "Selected locations are in use. Close the related apps, then scan again."
                    : "Select one or more cache locations to clean.");
            return;
        }
        if (blocked > 0) {
            cleanerSummaryLabel.setText(blocked + " selected location" + (blocked == 1 ? " is" : "s are") + " in use and will be skipped.");
        }

        CleanerDeletionMode mode = cleanerModeChoice.getValue();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm cache cleanup");
        confirmation.setHeaderText("Clean " + selected.size() + " cache location" + (selected.size() == 1 ? "?" : "s?"));
        confirmation.setContentText(mode == CleanerDeletionMode.RECYCLE_BIN
                ? "The selected cache folders will be moved to the Windows Recycle Bin, where they can be restored."
                : "The selected cache folders will be deleted normally. Their contents will not be securely overwritten.");
        confirmation.getButtonTypes().setAll(ButtonType.CANCEL, new ButtonType("Clean now", ButtonBar.ButtonData.OK_DONE));
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL).getButtonData() != ButtonBar.ButtonData.OK_DONE) return;

        List<CleanerTarget> cleanerSelection = cleanerTargets.stream().filter(CleanerTarget::isSelected)
                .filter(target -> target.getAvailability().equals("Ready")).toList();
        cleanerRunning = true;
        updateCleanerButtons();
        cleanerService.clean(cleanerSelection, mode, progress -> Platform.runLater(() ->
                cleanerSummaryLabel.setText(progress.message()))).whenComplete(this::finishCleaning);
    }

    private void addPaths(Collection<Path> paths) {
        if (running) return;
        Map<Path, QueueEntry> combined = new LinkedHashMap<>();
        queue.forEach(entry -> combined.put(entry.path(), entry));
        for (Path rawPath : paths) {
            if (rawPath == null || !Files.exists(rawPath)) continue;
            Path path = rawPath.toAbsolutePath().normalize();
            combined.putIfAbsent(path, QueueEntry.from(path));
        }
        queue.setAll(combined.values());
        if (!paths.isEmpty()) statusLabel.setText("Queue updated. Review the items, then choose Securely erase.");
    }

    private void publishProgress(ShredProgress progress) {
        Platform.runLater(() -> {
            statusLabel.setText(progress.message());
            progressLabel.setText(progress.totalItems() == 0 ? "" : progress.completedItems() + " of " + progress.totalItems() + " items completed");
            if (progress.totalItems() > 0) {
                progressBar.setProgress(Math.min(1, (double) progress.completedItems() / progress.totalItems()));
            }
        });
    }

    private void finishShredding(ShredResult result, Throwable error) {
        Platform.runLater(() -> {
            running = false;
            if (error != null) {
                statusLabel.setText("The shredding job ended unexpectedly.");
            } else if (!result.failures().isEmpty()) {
                statusLabel.setText("Completed with " + result.failures().size() + " item(s) that could not be removed.");
                showFailures(result.failures());
            }
            queue.removeIf(entry -> !Files.exists(entry.path()));
            progressBar.setProgress(result != null && !result.cancelled() ? 1 : progressBar.getProgress());
            updateButtons();
        });
    }

    private void configureQueueTable() {
        queueNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));
        queueTypeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type()));
        queueLocationColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().location()));
        queueSizeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().size()));
    }

    private void configureCleanerTable() {
        cleanerTable.setEditable(true);
        cleanerSelectedColumn.setCellValueFactory(cell -> cell.getValue().selectedProperty());
        cleanerSelectedColumn.setCellFactory(CheckBoxTableCell.forTableColumn(cleanerSelectedColumn));
        cleanerNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
        cleanerCategoryColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCategory()));
        cleanerItemsColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getItemCount() + " files"));
        cleanerSizeColumn.setCellValueFactory(cell -> new SimpleStringProperty(formatBytes(cell.getValue().getTotalBytes())));
        cleanerStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAvailability()));
    }

    private void finishCleaning(CleanerResult result, Throwable error) {
        Platform.runLater(() -> {
            cleanerRunning = false;
            if (error != null) {
                cleanerSummaryLabel.setText("The cleanup could not be completed.");
            } else {
                cleanerTargets.removeIf(target -> !Files.exists(target.getPath()));
                cleanerSummaryLabel.setText(result.failures().isEmpty()
                        ? "Finished cleaning " + result.cleanedLocations() + " location" + (result.cleanedLocations() == 1 ? "." : "s.")
                        : "Finished with " + result.failures().size() + " location(s) requiring attention.");
                if (!result.failures().isEmpty()) showCleanerFailures(result.failures());
            }
            updateCleanerButtons();
        });
    }

    private void updateQueuePresentation() {
        long fileCount = queue.size();
        queueSummaryLabel.setText(fileCount == 0 ? "No items queued" : fileCount + " item" + (fileCount == 1 ? " queued" : "s queued"));
        updateButtons();
    }

    private void updateButtons() {
        boolean hasQueue = !queue.isEmpty();
        removeButton.setDisable(running || queueTable.getSelectionModel().getSelectedItem() == null);
        clearButton.setDisable(running || !hasQueue);
        shredButton.setDisable(running || !hasQueue);
        cancelButton.setDisable(!running);
        queueTable.setDisable(running);
    }

    private void updateCleanerButtons() {
        scanButton.setDisable(cleanerRunning);
        selectCleanerButton.setDisable(cleanerRunning || cleanerTargets.isEmpty());
        cleanButton.setDisable(cleanerRunning || cleanerTargets.stream().noneMatch(CleanerTarget::isSelected));
        cleanerModeChoice.setDisable(cleanerRunning);
        cleanerTable.setDisable(cleanerRunning);
    }

    private void showFailures(List<String> failures) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Some items need attention");
        alert.setHeaderText("A few files could not be securely removed.");
        alert.setContentText(String.join("\n", failures.stream().limit(6).toList()));
        alert.show();
    }

    private void showCleanerFailures(List<String> failures) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Some cache locations need attention");
        alert.setHeaderText("A few selected locations could not be cleaned.");
        alert.setContentText(String.join("\n", failures.stream().limit(6).toList()));
        alert.show();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do { value /= 1024; unit++; } while (value >= 1024 && unit < units.length - 1);
        return String.format("%.1f %s", value, units[unit]);
    }

    private record QueueEntry(Path path, String name, String type, String location, String size) {
        static QueueEntry from(Path path) {
            boolean directory = Files.isDirectory(path);
            long size = 0;
            if (!directory) {
                try { size = Files.size(path); } catch (Exception ignored) { }
            }
            Path fileName = path.getFileName();
            return new QueueEntry(path, fileName == null ? path.toString() : fileName.toString(), directory ? "Folder" : "File",
                    path.getParent() == null ? "" : path.getParent().toString(), directory ? "Will scan when erased" : formatBytes(size));
        }
    }
}
