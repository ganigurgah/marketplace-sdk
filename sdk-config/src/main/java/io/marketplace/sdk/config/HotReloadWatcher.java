package io.marketplace.sdk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.function.Consumer;

public class HotReloadWatcher {
    private static final Logger log = LoggerFactory.getLogger(HotReloadWatcher.class);

    private final Path watchDir;
    private final Consumer<Path> onChanged;
    private volatile boolean running = false;
    private Thread watchThread;

    public HotReloadWatcher(Path watchDir, Consumer<Path> onChanged) {
        this.watchDir = watchDir;
        this.onChanged = onChanged;
    }

    public void start() {
        running = true;
        watchThread = new Thread(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                watchDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
                log.info("Hot-reload watcher started on: {}", watchDir);
                while (running) {
                    WatchKey key = watchService.poll(2, java.util.concurrent.TimeUnit.SECONDS);
                    if (key == null) continue;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = watchDir.resolve((Path) event.context());
                        if (changed.toString().endsWith(".yaml")) {
                            log.info("Config file changed: {}", changed.getFileName());
                            Thread.sleep(200);
                            onChanged.accept(changed);
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                if (running) log.error("Hot-reload watcher error", e);
            }
        }, "marketplace-config-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public void stop() {
        running = false;
        if (watchThread != null) watchThread.interrupt();
    }
}
