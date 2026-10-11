package com.fixhua.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Immutable metadata for an APK artifact imported by the Controller. */
public record SyntheticPackageRecord(
        String artifactSha256,
        long artifactSizeBytes,
        String packageName,
        String versionName,
        long versionCode,
        int minSdk,
        int targetSdk,
        String applicationClassName,
        boolean debuggable,
        boolean hasNativeCode,
        List<String> requestedPermissions,
        List<String> signingCertificateSha256) {

    public SyntheticPackageRecord {
        artifactSha256 = normalizeSha256(artifactSha256);
        if (artifactSizeBytes <= 0) throw new IllegalArgumentException("artifactSizeBytes");
        if (packageName == null || packageName.isBlank()) throw new IllegalArgumentException("packageName");
        versionName = versionName == null ? "" : versionName;
        if (versionCode < 0) throw new IllegalArgumentException("versionCode");
        if (minSdk < 0 || targetSdk < 0) throw new IllegalArgumentException("sdk");
        applicationClassName = applicationClassName == null ? "" : applicationClassName;
        requestedPermissions = canonicalStrings(requestedPermissions);
        signingCertificateSha256 = canonicalDigests(signingCertificateSha256);
        if (signingCertificateSha256.isEmpty()) {
            throw new IllegalArgumentException("signingCertificateSha256");
        }
    }

    public String contentAddress() {
        return artifactSha256;
    }

    public String identityKey() {
        return packageName + "@" + versionCode + "#" + artifactSha256;
    }

    private static List<String> canonicalStrings(List<String> values) {
        TreeSet<String> ordered = new TreeSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) ordered.add(value);
            }
        }
        return List.copyOf(new ArrayList<>(ordered));
    }

    private static List<String> canonicalDigests(List<String> values) {
        TreeSet<String> ordered = new TreeSet<>();
        if (values != null) {
            for (String value : values) ordered.add(normalizeSha256(value));
        }
        return List.copyOf(new ArrayList<>(ordered));
    }

    private static String normalizeSha256(String value) {
        if (value == null) throw new IllegalArgumentException("sha256");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != 64) throw new IllegalArgumentException("sha256");
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) throw new IllegalArgumentException("sha256");
        }
        return normalized;
    }
}
