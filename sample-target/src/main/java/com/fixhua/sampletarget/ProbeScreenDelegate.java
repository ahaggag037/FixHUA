package com.fixhua.sampletarget;

import android.content.Context;

/**
 * Deterministic foreign lifecycle target used by FixHUA CI. It is intentionally not an Android
 * Activity: the Runtime owns the real host Activity and delegates lifecycle into this imported
 * object without hidden ActivityThread/attach APIs.
 */
public final class ProbeScreenDelegate {
    private int createCount;
    private int resumeCount;
    private int configurationCount;
    private int destroyCount;

    public String onCreate(Context context) {
        if (context == null) throw new IllegalArgumentException("context");
        createCount++;
        return snapshot();
    }

    public String onResume() {
        resumeCount++;
        return snapshot();
    }

    public String onConfigurationChanged(int orientation) {
        configurationCount++;
        return snapshot() + ";orientation=" + orientation;
    }

    public String onDestroy() {
        destroyCount++;
        return snapshot();
    }

    public String snapshot() {
        return "create=" + createCount
                + ";resume=" + resumeCount
                + ";config=" + configurationCount
                + ";destroy=" + destroyCount;
    }
}
