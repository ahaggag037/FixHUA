package com.fixhua.rootprobe;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class ProbeCollector {
    private final Context context;

    ProbeCollector(Context context) {
        this.context = context.getApplicationContext();
    }

    String collect() {
        StringBuilder out = new StringBuilder(8192);
        line(out, "FIXHUA_ROOT_FEASIBILITY_PROBE");
        line(out, "schema=1");
        line(out, "generated=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()));
        line(out, "mode=READ_ONLY");
        line(out, "privacy=no IMEI/serial/Android-ID/account/location/message/photo/contact/password collection");
        line(out, "warning=no reboot, no flashing, no bootloader unlock, no property writes, no partition writes");

        section(out, "DEVICE");
        kv(out, "manufacturer", Build.MANUFACTURER);
        kv(out, "brand", Build.BRAND);
        kv(out, "model", Build.MODEL);
        kv(out, "device", Build.DEVICE);
        kv(out, "product", Build.PRODUCT);
        kv(out, "board", Build.BOARD);
        kv(out, "hardware", Build.HARDWARE);
        kv(out, "bootloader_build", Build.BOOTLOADER);
        kv(out, "android_release", Build.VERSION.RELEASE);
        kv(out, "sdk", String.valueOf(Build.VERSION.SDK_INT));
        kv(out, "security_patch", Build.VERSION.SECURITY_PATCH);
        kv(out, "build_display", Build.DISPLAY);
        kv(out, "build_fingerprint", Build.FINGERPRINT);
        kv(out, "supported_abis", join(Build.SUPPORTED_ABIS));
        if (Build.VERSION.SDK_INT >= 31) {
            kv(out, "soc_manufacturer", Build.SOC_MANUFACTURER);
            kv(out, "soc_model", Build.SOC_MODEL);
        }

        section(out, "HUAWEI_AND_SOC_PROPERTIES");
        prop(out, "ro.build.version.emui");
        prop(out, "ro.build.version.magic");
        prop(out, "ro.huawei.build.display.id");
        prop(out, "ro.build.hw_emui_api_level");
        prop(out, "ro.board.platform");
        prop(out, "ro.soc.manufacturer");
        prop(out, "ro.soc.model");
        prop(out, "ro.boot.hardware");
        prop(out, "ro.hardware");

        section(out, "BOOTLOADER_AND_VERIFIED_BOOT");
        String flashLocked = getProp("ro.boot.flash.locked");
        String vbmetaState = getProp("ro.boot.vbmeta.device_state");
        String verifiedBoot = getProp("ro.boot.verifiedbootstate");
        kv(out, "ro.boot.flash.locked", flashLocked);
        kv(out, "ro.boot.vbmeta.device_state", vbmetaState);
        kv(out, "ro.boot.verifiedbootstate", verifiedBoot);
        prop(out, "ro.boot.veritymode");
        prop(out, "ro.boot.avb_version");
        prop(out, "ro.boot.bootloader");
        prop(out, "ro.bootmode");
        prop(out, "ro.boot.bootreason");
        prop(out, "ro.boot.warranty_bit");
        kv(out, "derived_bootloader_state", classifyBootloader(flashLocked, vbmetaState, verifiedBoot));

        section(out, "OEM_UNLOCK_AND_DEVELOPER_STATE");
        prop(out, "ro.oem_unlock_supported");
        prop(out, "sys.oem_unlock_allowed");
        prop(out, "ro.boot.oem_unlock_support");
        setting(out, "development_settings_enabled");
        setting(out, "adb_enabled");
        setting(out, "adb_wifi_enabled");
        setting(out, "oem_unlock_allowed");
        line(out, "note_oem_unlock=These values indicate build/settings state only; they do not prove Huawei will accept an unlock command.");

        section(out, "PARTITION_AND_FASTBOOT_LAYOUT");
        prop(out, "ro.boot.slot_suffix");
        prop(out, "ro.boot.slot");
        prop(out, "ro.build.ab_update");
        prop(out, "ro.virtual_ab.enabled");
        prop(out, "ro.boot.dynamic_partitions");
        prop(out, "ro.treble.enabled");
        prop(out, "ro.fastbootd.available");
        path(out, "/dev/block/by-name/boot");
        path(out, "/dev/block/by-name/boot_a");
        path(out, "/dev/block/by-name/boot_b");
        path(out, "/dev/block/by-name/init_boot");
        path(out, "/dev/block/by-name/vbmeta");
        path(out, "/dev/block/by-name/vbmeta_a");
        path(out, "/dev/block/by-name/vbmeta_b");
        path(out, "/dev/block/bootdevice/by-name/boot");
        path(out, "/dev/block/bootdevice/by-name/vbmeta");
        kv(out, "proc_cmdline", safeReadFirstLine("/proc/cmdline"));
        line(out, "fastboot_runtime_limit=An Android APK cannot execute while the phone itself is in bootloader/fastboot mode; actual fastboot command responses require an external host or later service-mode tooling.");

        section(out, "ROOT_AND_PRIVILEGE_STATE");
        kv(out, "su_binary_hint", suBinaryHint());
        kv(out, "magisk_package_visible", yesNo(isPackageVisible("com.topjohnwu.magisk")));
        kv(out, "shizuku_package_visible", yesNo(isPackageVisible("moe.shizuku.privileged.api")));
        kv(out, "huawei_systemmanager_visible", yesNo(isPackageVisible("com.huawei.systemmanager")));
        String rootProbe = activeRootProbe();
        kv(out, "active_su_id_probe", rootProbe);
        kv(out, "selinux_mode", runCommand(1200, "/system/bin/getenforce"));

        section(out, "READABILITY_CAPABILITIES");
        path(out, "/proc/pressure/cpu");
        path(out, "/proc/pressure/memory");
        path(out, "/proc/pressure/io");
        path(out, "/proc/stat");
        path(out, "/proc/meminfo");
        path(out, "/sys/block/zram0");
        path(out, "/sys/devices/system/cpu/cpufreq");

        section(out, "ROOT_FEASIBILITY_SUMMARY");
        String bootState = classifyBootloader(flashLocked, vbmetaState, verifiedBoot);
        kv(out, "bootloader_state", bootState);
        if ("LIKELY_UNLOCKED".equals(bootState)) {
            line(out, "standard_root_path_candidate=YES: bootloader evidence looks unlocked; exact boot image/partition workflow still requires separate verification.");
        } else if ("LIKELY_LOCKED".equals(bootState)) {
            line(out, "standard_root_path_candidate=BLOCKED: standard patched-boot rooting normally requires an unlocked bootloader.");
            line(out, "next_research_target=verified JLN-LX1-specific bootloader/service path; do not assume generic Huawei unlock methods apply.");
        } else if ("CONFLICTING".equals(bootState)) {
            line(out, "standard_root_path_candidate=UNKNOWN: boot properties conflict; external fastboot/service evidence is required.");
        } else {
            line(out, "standard_root_path_candidate=UNKNOWN: Android-visible properties are insufficient; external fastboot/service evidence is required.");
        }
        line(out, "edl_statement=Qualcomm hardware can have service/EDL paths, but this APK does not enter EDL and does not claim an unlock path exists.");
        line(out, "recommended_handoff=Share this complete report with the engineering/research model; no manual adb/fastboot commands are needed for this first-stage probe.");

        return out.toString();
    }

    private void prop(StringBuilder out, String key) {
        kv(out, key, getProp(key));
    }

    private String getProp(String key) {
        String value = runCommand(1000, "/system/bin/getprop", key);
        if (value.startsWith("ERROR:")) {
            value = runCommand(1000, "getprop", key);
        }
        return normalize(value);
    }

    private void setting(StringBuilder out, String key) {
        try {
            String value = Settings.Global.getString(context.getContentResolver(), key);
            kv(out, "settings.global." + key, value == null ? "UNAVAILABLE" : value);
        } catch (Throwable t) {
            kv(out, "settings.global." + key, "UNAVAILABLE:" + t.getClass().getSimpleName());
        }
    }

    private boolean isPackageVisible(String packageName) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            return info != null;
        } catch (PackageManager.NameNotFoundException | SecurityException e) {
            return false;
        }
    }

    private String suBinaryHint() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/debug_ramdisk/su"};
        for (String path : paths) {
            try {
                if (new File(path).exists()) return "FOUND:" + path;
            } catch (Throwable ignored) {
            }
        }
        String which = runCommand(900, "/system/bin/sh", "-c", "command -v su 2>/dev/null || true");
        return which.trim().isEmpty() ? "NOT_FOUND" : normalize(which);
    }

    private String activeRootProbe() {
        String hint = suBinaryHint();
        if ("NOT_FOUND".equals(hint)) return "NOT_AVAILABLE";
        String result = runCommand(2200, "su", "-c", "id");
        if (result.contains("uid=0")) return "ROOT_AVAILABLE:" + oneLine(result);
        if (result.startsWith("TIMEOUT")) return "SU_PRESENT_BUT_PROBE_TIMEOUT";
        return "SU_PRESENT_NOT_ROOT:" + oneLine(result);
    }

    private String classifyBootloader(String flashLocked, String vbmetaState, String verifiedBoot) {
        boolean unlockedSignal = "0".equals(flashLocked)
                || "unlocked".equalsIgnoreCase(vbmetaState)
                || "orange".equalsIgnoreCase(verifiedBoot);
        boolean lockedSignal = "1".equals(flashLocked)
                || "locked".equalsIgnoreCase(vbmetaState)
                || "green".equalsIgnoreCase(verifiedBoot);
        if (unlockedSignal && lockedSignal) return "CONFLICTING";
        if (unlockedSignal) return "LIKELY_UNLOCKED";
        if (lockedSignal) return "LIKELY_LOCKED";
        return "UNKNOWN";
    }

    private void path(StringBuilder out, String path) {
        try {
            File f = new File(path);
            if (!f.exists()) {
                kv(out, "path:" + path, "ABSENT_OR_HIDDEN");
            } else {
                kv(out, "path:" + path, "EXISTS readable=" + f.canRead() + " writable=" + f.canWrite());
            }
        } catch (Throwable t) {
            kv(out, "path:" + path, "UNAVAILABLE:" + t.getClass().getSimpleName());
        }
    }

    private String safeReadFirstLine(String path) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(path)))) {
            String value = reader.readLine();
            return value == null ? "EMPTY" : oneLine(value);
        } catch (Throwable t) {
            return "UNAVAILABLE:" + t.getClass().getSimpleName();
        }
    }

    private String runCommand(long timeoutMs, String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "TIMEOUT";
            }
            StringBuilder s = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null && s.length() < 4096) {
                    if (s.length() > 0) s.append(' ');
                    s.append(line);
                }
            }
            if (process.exitValue() != 0 && s.length() == 0) return "ERROR:exit=" + process.exitValue();
            return normalize(s.toString());
        } catch (Throwable t) {
            return "ERROR:" + t.getClass().getSimpleName();
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) { }
            }
        }
    }

    private static String normalize(String value) {
        if (value == null) return "UNAVAILABLE";
        value = value.trim();
        return value.isEmpty() ? "UNAVAILABLE" : oneLine(value);
    }

    private static String oneLine(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) return "UNAVAILABLE";
        StringBuilder s = new StringBuilder();
        for (String v : values) {
            if (s.length() > 0) s.append(',');
            s.append(v);
        }
        return s.toString();
    }

    private static String yesNo(boolean value) {
        return value ? "true" : "false";
    }

    private static void section(StringBuilder out, String name) {
        out.append("\n=== ").append(name).append(" ===\n");
    }

    private static void kv(StringBuilder out, String key, String value) {
        line(out, key + "=" + normalize(value));
    }

    private static void line(StringBuilder out, String value) {
        out.append(value).append('\n');
    }
}
