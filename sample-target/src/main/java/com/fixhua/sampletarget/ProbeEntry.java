package com.fixhua.sampletarget;

import android.content.Context;
import android.content.pm.PackageManager;

public final class ProbeEntry {
    private static final String RUNTIME_BRIDGE_PERMISSION =
            "com.fixhua.guard.permission.RUNTIME_BRIDGE";

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
        String bridgePermission = context.checkSelfPermission(RUNTIME_BRIDGE_PERMISSION)
                == PackageManager.PERMISSION_GRANTED ? "granted" : "denied";
        Object connectivityService = context.getSystemService(Context.CONNECTIVITY_SERVICE);
        String connectivity = connectivityService == null ? "missing" : "host";
        return "package=" + context.getPackageName()
                + ";gms=" + gms
                + ";bridgePerm=" + bridgePermission
                + ";connectivity=" + connectivity
                + ";context=" + context.getClass().getName();
    }

    private ProbeEntry() {}
}
