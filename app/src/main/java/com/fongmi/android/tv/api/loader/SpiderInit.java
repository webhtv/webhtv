package com.fongmi.android.tv.api.loader;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class SpiderInit {

    @FunctionalInterface
    interface Initializer {
        void init(Spider spider) throws Exception;
    }

    static Spider load(ConcurrentHashMap<String, Spider> spiders, String key,
                       Callable<Spider> factory, Initializer initializer, Consumer<Throwable> log) {
        Spider result = spiders.computeIfAbsent(key, ignored -> {
            Spider spider = null;
            try {
                spider = Objects.requireNonNull(factory.call(), "Spider factory returned null");
                initializer.init(spider);
                return spider;
            } catch (Throwable failure) {
                if (spider != null) {
                    try {
                        spider.destroy();
                    } catch (Throwable cleanup) {
                        suppress(failure, cleanup);
                    }
                }
                try {
                    log.accept(failure);
                } catch (Throwable diagnostic) {
                    suppress(failure, diagnostic);
                    printBestEffort(failure);
                }
                return null;
            }
        });
        return result != null ? result : new SpiderNull();
    }

    private static void printBestEffort(Throwable failure) {
        try {
            failure.printStackTrace();
        } catch (Throwable ignored) {
        }
    }
    private static void suppress(Throwable failure, Throwable secondary) {
        if (failure == secondary) return;
        try {
            failure.addSuppressed(secondary);
        } catch (Throwable ignored) {
            printBestEffort(secondary);
        }
    }
}
