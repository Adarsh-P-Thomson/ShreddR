package com.shreddr.shreddr.service;

public enum CleanerDeletionMode {
    RECYCLE_BIN("Move to Recycle Bin"),
    NORMAL_DELETE("Delete normally");

    private final String displayName;

    CleanerDeletionMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
