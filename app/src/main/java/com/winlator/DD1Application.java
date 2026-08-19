package com.winlator;

import android.app.Application;

public final class DD1Application extends Application {
    static {
        System.setProperty("kotlinx.coroutines.scheduler.core.pool.size", "2");
        System.setProperty("kotlinx.coroutines.scheduler.max.pool.size", "4");
    }
}
