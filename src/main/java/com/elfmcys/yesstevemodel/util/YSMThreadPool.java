package com.elfmcys.yesstevemodel.util;

import java.util.concurrent.*;

public final class YSMThreadPool {

    private static final ThreadPoolExecutor EXECUTOR;
    private static final ThreadPoolExecutor SYNC_EXECUTOR;

    static {
        EXECUTOR = new ThreadPoolExecutor(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), Math.max(2, Runtime.getRuntime().availableProcessors() / 2), 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "BPM Worker");
            thread.setPriority(5);
            thread.setDaemon(true);
            return thread;
        });
        EXECUTOR.allowCoreThreadTimeOut(true);

        SYNC_EXECUTOR = new ThreadPoolExecutor(32, 32, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "BPM Sync");
            thread.setPriority(7);
            thread.setDaemon(true);
            return thread;
        });
        SYNC_EXECUTOR.allowCoreThreadTimeOut(true);
    }

    public static Future<?> submit(Runnable runnable) {
        return EXECUTOR.submit(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                t.printStackTrace();
                throw t;
            }
        });
    }

    public static <T> Future<T> submitCallable(Callable<T> callable) {
        return EXECUTOR.submit(() -> {
            try {
                return callable.call();
            } catch (Throwable t) {
                t.printStackTrace();
                throw t;
            }
        });
    }

    public static Future<?> submitSync(Runnable runnable) {
        return SYNC_EXECUTOR.submit(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                t.printStackTrace();
                throw t;
            }
        });
    }

    public static boolean awaitTermination(int i) {
        try {
            boolean ex1 = EXECUTOR.awaitTermination(i, TimeUnit.MILLISECONDS);
            boolean ex2 = SYNC_EXECUTOR.awaitTermination(i, TimeUnit.MILLISECONDS);
            return ex1 && ex2;
        } catch (InterruptedException e) {
            return false;
        }
    }
}
