package com.fixhua.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public final class BugreportAnalyzerTest {
    @Test
    public void extractsOnlyCompatibilityFactsAndFeedsHostDoctor() throws Exception {
        String report = String.join("\n",
                "mOverrideMaxCachedProcesses=4",
                "mCustomizedMaxCachedProcesses=32",
                "CUR_MAX_CACHED_PROCESSES=4",
                "always_finish_activities=0",
                "[debug.egl.force_msaa]: [true]",
                "Multiprocess enabled: true",
                "WebView package dirty: false",
                "Current WebView package: com.huawei.webview 114.0.5.302",
                "ProcessRecord{aaa 111:com.gbox.android/u0a174}",
                "ProcessRecord{bbb 112:com.gbox.android:server/u0a174}",
                "ProcessRecord{ccc 113:com.gbox.android:vlapp01/u0a174}",
                "AppProcessKiller: doKillProcess 4",
                "java.lang.NullPointerException: Application.getResources() on a null object reference",
                "Input dispatching timed out com.openai.chatgpt/.MainActivity");

        BugreportAnalyzer.Result result = new BugreportAnalyzer().analyzeZip(
                new ByteArrayInputStream(zip("bugreport-JLN-L21-test.txt", report)));

        assertEquals(4, result.overrideMaxCachedProcesses());
        assertEquals(32, result.customizedMaxCachedProcesses());
        assertEquals(4, result.currentMaxCachedProcesses());
        assertTrue(result.hasDeveloperProcessLimitOverride());
        assertTrue(result.force4xMsaa());
        assertFalse(result.alwaysFinishActivities());
        assertTrue(result.webViewMultiprocess());
        assertFalse(result.webViewDirty());
        assertEquals("com.huawei.webview", result.webViewPackage());
        assertEquals("114.0.5.302", result.webViewVersion());
        assertEquals(3, result.gboxProcessNames().size());
        assertEquals(1, result.gboxAppProcessKillerMarkers());
        assertEquals(1, result.nullApplicationResourcesMarkers());
        assertEquals(1, result.inputDispatchTimeoutMarkers());

        HostDoctor doctor = new HostDoctor();
        List<HostDoctor.Finding> findings = doctor.evaluate(
                result.toHostProfile(),
                new HostDoctor.RuntimeBudget("gbox-observed", result.gboxProcessNames().size()));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals("DEV_PROCESS_LIMIT_OVERRIDE")));
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsZipWithoutAndroidMainBugreportText() throws Exception {
        new BugreportAnalyzer().analyzeZip(
                new ByteArrayInputStream(zip("notes.txt", "not a bugreport")));
    }

    private static byte[] zip(String name, String content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
