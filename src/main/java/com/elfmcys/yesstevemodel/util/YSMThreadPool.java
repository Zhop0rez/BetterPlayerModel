package com.elfmcys.yesstevemodel.util;

import java.util.concurrent.*;

public final class YSMThreadPool {

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), Math.max(2, Runtime.getRuntime().availableProcessors() / 2), 30, TimeUnit.SECONDS, new LinkedBlockingQueue(), runnable -> {
        Thread thread = new Thread(runnable, "BPM Worker");
        thread.setPriority(5);
        thread.setDaemon(true);
        return thread;
    });

    private static final ThreadPoolExecutor SYNC_EXECUTOR = new ThreadPoolExecutor(32, 32, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
        Thread thread = new Thread(runnable, "BPM Sync");
        thread.setPriority(7);
        thread.setDaemon(true);
        return thread;
    });

    public static Future<?> submit(Runnable runnable) {
        return EXECUTOR.submit(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.error("[BPM] Uncaught error in YSMThreadPool worker", t);
                throw t;
            }
        });
    }

    public static <T> Future<T> submitCallable(Callable<T> callable) {
        return EXECUTOR.submit(callable);
    }

    public static Future<?> submitSync(Runnable runnable) {
        return SYNC_EXECUTOR.submit(runnable);
    }

    public static boolean awaitTermination(int i) {
        try {
            Thread.sleep(i);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }
}
