package com.fixhua.guard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/**
 * Emulator-only acceptance bootstrap. The exported component is additionally protected by the
 * platform DUMP permission so ordinary third-party apps cannot invoke this test path.
 */
public final class AcceptanceBootstrapActivity extends Activity {
    private static final String TAG = "FixHUAAcceptance";
    private static final String SAMPLE_PACKAGE = "com.fixhua.sampletarget";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView status = new TextView(this);
        status.setPadding(32, 48, 32, 32);
        status.setText("Synthetic acceptance bootstrap…");
        setContentView(status);

        if (!isEmulatorHardware()) {
            Log.e(TAG, "acceptanceBootstrap=false reason=not_emulator hardware=" + Build.HARDWARE);
            status.setText("Acceptance bootstrap is emulator-only.");
            return;
        }

        new Thread(() -> {
            try {
                ArtifactImporter.ImportedArtifact imported = new ArtifactImporter(this)
                        .importInstalledPackageForAcceptance(SAMPLE_PACKAGE);
                RuntimeSessionStore.Session session = imported.runtimeSession();
                Log.i(TAG, "sessionReady=true package=" + imported.record().packageName()
                        + " sha256=" + imported.record().artifactSha256()
                        + " plan=" + session.planDigest()
                        + " reused=" + imported.reusedExistingArtifact());

                Intent runtime = new Intent();
                runtime.setComponent(new ComponentName(
                        "com.fixhua.runtime",
                        "com.fixhua.runtime.RuntimeActivity"));
                runtime.putExtra("com.fixhua.runtime.extra.SESSION_ID", session.sessionId());
                runtime.putExtra("com.fixhua.runtime.extra.ARTIFACT_SHA256", session.artifactSha256());
                runtime.putExtra("com.fixhua.runtime.extra.PLAN_DIGEST", session.planDigest());
                runOnUiThread(() -> {
                    startActivity(runtime);
                    finish();
                });
            } catch (Exception failure) {
                Log.e(TAG, "acceptanceBootstrap=false", failure);
                runOnUiThread(() -> status.setText(
                        "Acceptance bootstrap failed: " + failure.getClass().getSimpleName()));
            }
        }, "fixhua-acceptance-bootstrap").start();
    }

    private static boolean isEmulatorHardware() {
        String hardware = Build.HARDWARE == null ? "" : Build.HARDWARE.toLowerCase();
        String fingerprint = Build.FINGERPRINT == null ? "" : Build.FINGERPRINT.toLowerCase();
        return hardware.contains("ranchu")
                || hardware.contains("goldfish")
                || fingerprint.contains("generic")
                || fingerprint.contains("emulator");
    }
}
