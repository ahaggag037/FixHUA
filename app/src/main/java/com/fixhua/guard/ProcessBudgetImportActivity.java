package com.fixhua.guard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/**
 * Shell/system-only ingress for four sanitized ActivityManager process-budget integers.
 * The manifest protects this exported Activity with android.permission.DUMP, which adb shell holds
 * but ordinary third-party applications do not. No raw dumpsys text is accepted.
 */
public final class ProcessBudgetImportActivity extends Activity {
    private static final String TAG = "FixHUAProcessBudgetImport";
    static final String EXTRA_OVERRIDE = "override";
    static final String EXTRA_CUSTOMIZED = "customized";
    static final String EXTRA_PLATFORM = "platform";
    static final String EXTRA_CURRENT = "current";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        ProcessBudgetImport.Payload payload = new ProcessBudgetImport.Payload(
                intent.getIntExtra(EXTRA_OVERRIDE, ProcessBudgetImport.UNKNOWN),
                intent.getIntExtra(EXTRA_CUSTOMIZED, ProcessBudgetImport.UNKNOWN),
                intent.getIntExtra(EXTRA_PLATFORM, ProcessBudgetImport.UNKNOWN),
                intent.getIntExtra(EXTRA_CURRENT, ProcessBudgetImport.UNKNOWN));

        if (!payload.isValid()) {
            Log.w(TAG,
                    "accepted=false reason=INVALID_BOUNDED_INTS current="
                            + payload.currentMaxCachedProcesses());
            Toast.makeText(this, "رفض FixHUA بيانات ميزانية عمليات غير صالحة", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        boolean saved = ProcessBudgetImportStore.save(this, payload, System.currentTimeMillis());
        Log.i(TAG,
                "accepted=" + saved
                        + " source=ADB_SHELL_SANITIZED"
                        + " current=" + payload.currentMaxCachedProcesses()
                        + " reference=" + payload.referenceMaxCachedProcesses()
                        + " override=" + payload.overrideMaxCachedProcesses());
        if (!saved) {
            Toast.makeText(this, "تعذر حفظ نتيجة الفحص", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Intent main = new Intent(this, OptimizerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(main);
        finish();
    }
}
