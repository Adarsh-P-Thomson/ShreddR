package com.shreddr.shreddr.service;

import java.util.List;

/** Finds disposable, application-owned cache locations. It never deletes anything itself. */
public interface SystemCleanerService {
    List<CleanerTarget> scan();
}
