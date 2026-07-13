package com.k2.music;

import android.app.Application;

import com.k2.music.ui.AppContainer;

/** Makes packaged assets available to the no-argument compatibility repository. */
public final class MusicApplication extends Application {
    private AppContainer appContainer;

    @Override
    public void onCreate() {
        super.onCreate();
        ChordDataLoader.setDefaultAssetSource(relativePath -> getAssets().open(relativePath));
        ChordDataLoader.preloadDefaultAsync();
        appContainer = new AppContainer(this);
    }

    public AppContainer getAppContainer() {
        return appContainer;
    }

    @Override
    public void onTerminate() {
        if (appContainer != null) {
            appContainer.close();
        }
        super.onTerminate();
    }
}
