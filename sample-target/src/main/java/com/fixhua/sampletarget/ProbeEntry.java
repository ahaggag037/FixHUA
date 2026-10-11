package com.fixhua.sampletarget;

import android.content.Context;
import android.content.pm.PackageManager;

public final class ProbeEntry {
    public static String pureMarker() {
        return "sample-code-v1";
    }

    public static String run(Context context) {
        String gms;
        try {
            context.getPackageManager().getPackageInfo("com.google.android.gms", 0);
            gms = "present";
        } catch (PackageManager.NameNotFoundException e) {
            gms = "absent";
        }
        return "package=" + context.getPackageName()
                + ";gms=" + gms
                + ";context=" + context.getClass().getName();
    }

    private ProbeEntry() {}
}
