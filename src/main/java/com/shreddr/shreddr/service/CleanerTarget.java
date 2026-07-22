package com.shreddr.shreddr.service;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.nio.file.Path;

/** A known cache location that the user may choose to add to the shred queue. */
public final class CleanerTarget {
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final String category;
    private final String name;
    private final Path path;
    private final long itemCount;
    private final long totalBytes;
    private final String availability;

    public CleanerTarget(String category, String name, Path path, long itemCount, long totalBytes, String availability) {
        this.category = category;
        this.name = name;
        this.path = path;
        this.itemCount = itemCount;
        this.totalBytes = totalBytes;
        this.availability = availability;
    }

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean selected) { this.selected.set(selected); }
    public String getCategory() { return category; }
    public String getName() { return name; }
    public Path getPath() { return path; }
    public long getItemCount() { return itemCount; }
    public long getTotalBytes() { return totalBytes; }
    public String getAvailability() { return availability; }
}
