package com.bytedance.tictok_live.context;

import android.app.Application;
import android.content.Context;

/**
 * 提供全局唯一的应用上下文
 */
public class App extends Application {

    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = this;
    }

    public static Context getAppContext() {
        return appContext;
    }
}
