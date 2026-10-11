package com.fixhua.guard;

import android.content.Context;

import com.fixhua.core.CoreModels;
import com.fixhua.core.SyntheticPackageRecord;
import com.fixhua.core.SyntheticSlicePlans;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/** Controller-owned source of truth for the active Synthetic Runtime session. */
final class RuntimeSessionStore {
    private static final String STORE_DIR = "artifact-store-v1";
    private static final String SESSION_FILE = "runtime-session-v1.json";
    private static final long SESSION_TTL_MILLIS = 60L * 60L * 1000L;

    record Session(
            String sessionId,
            String artifactSha256,
            String packageName,
            String versionName,
            long versionCode,
            int minSdk,
            int targetSdk,
            String applicationClassName,
            String primarySigningSha256,
            String recipeId,
            int recipeVersion,
            String planDigest,
            long createdAtMillis,
            long expiresAtMillis) {}

    private final Context context;

    RuntimeSessionStore(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized Session create(SyntheticPackageRecord record) throws Exception {
        if (record == null) throw new IllegalArgumentException("record");
        File artifact = artifactFile(record.artifactSha256());
        verifyArtifact(artifact, record.artifactSha256());
        if (record.signingCertificateSha256().isEmpty()) {
            throw new SecurityException("Synthetic package identity requires signing evidence");
        }

        CoreModels.ResolutionPlan plan = SyntheticSlicePlans.runtimeSessionPlan();
        long now = System.currentTimeMillis();
        Session session = new Session(
                UUID.randomUUID().toString(),
                record.artifactSha256(),
                record.packageName(),
                record.versionName(),
                record.versionCode(),
                record.minSdk(),
                record.targetSdk(),
                record.applicationClassName(),
                normalizeSha256(record.signingCertificateSha256().get(0)),
                SyntheticSlicePlans.RECIPE_ID,
                SyntheticSlicePlans.RECIPE_VERSION,
                plan.digestSha256(),
                now,
                now + SESSION_TTL_MILLIS);
        persist(session);
        return session;
    }

    synchronized Session active() throws Exception {
        Session session = read();
        if (session == null) return null;
        if (System.currentTimeMillis() >= session.expiresAtMillis()) {
            revoke();
            return null;
        }
        CoreModels.ResolutionPlan expectedPlan = SyntheticSlicePlans.runtimeSessionPlan();
        if (!constantEquals(expectedPlan.digestSha256(), session.planDigest())) {
            throw new SecurityException("Runtime session plan is no longer valid");
        }
        verifyArtifact(artifactFile(session.artifactSha256()), session.artifactSha256());
        return session;
    }

    synchronized Session requireActive(
            String sessionId,
            String artifactSha256,
            String planDigest) throws Exception {
        Session session = active();
        if (session == null) throw new SecurityException("No active Runtime session");
        if (!constantEquals(session.sessionId(), sessionId)) {
            throw new SecurityException("Runtime session ID mismatch");
        }
        if (!constantEquals(session.artifactSha256(), normalizeSha256(artifactSha256))) {
            throw new SecurityException("Runtime artifact hash mismatch");
        }
        if (!constantEquals(session.planDigest(), normalizeSha256(planDigest))) {
            throw new SecurityException("Runtime plan digest mismatch");
        }
        return session;
    }

    synchronized Session requireCapability(
            String sessionId,
            String artifactSha256,
            String planDigest,
            int capabilityId) throws Exception {
        Session session = requireActive(sessionId, artifactSha256, planDigest);
        CoreModels.ResolutionPlan expectedPlan = SyntheticSlicePlans.runtimeSessionPlan();
        boolean authorized = false;
        for (CoreModels.ResolutionStep step : expectedPlan.steps()) {
            if (step.capabilityId() == capabilityId) {
                authorized = true;
                break;
            }
        }
        if (!authorized) {
            throw new SecurityException(
                    "Capability not authorized by Runtime plan: " + capabilityId);
        }
        return session;
    }

    synchronized File requireArtifactFile(
            String sessionId,
            String artifactSha256,
            String planDigest) throws Exception {
        Session session = requireActive(sessionId, artifactSha256, planDigest);
        return artifactFile(session.artifactSha256());
    }

    synchronized void revoke() {
        File file = new File(context.getFilesDir(), SESSION_FILE);
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Cannot revoke Runtime session");
        }
    }

    private File artifactFile(String hash) {
        File store = new File(context.getFilesDir(), STORE_DIR);
        return new File(store, normalizeSha256(hash) + ".apk");
    }

    private void persist(Session session) throws Exception {
        JSONObject json = new JSONObject();
        json.put("schemaVersion", 2);
        json.put("sessionId", session.sessionId());
        json.put("artifactSha256", session.artifactSha256());
        json.put("packageName", session.packageName());
        json.put("versionName", session.versionName());
        json.put("versionCode", session.versionCode());
        json.put("minSdk", session.minSdk());
        json.put("targetSdk", session.targetSdk());
        json.put("applicationClassName", session.applicationClassName());
        json.put("primarySigningSha256", session.primarySigningSha256());
        json.put("recipeId", session.recipeId());
        json.put("recipeVersion", session.recipeVersion());
        json.put("planDigest", session.planDigest());
        json.put("createdAtMillis", session.createdAtMillis());
        json.put("expiresAtMillis", session.expiresAtMillis());

        File target = new File(context.getFilesDir(), SESSION_FILE);
        File temp = new File(context.getFilesDir(), SESSION_FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (target.exists() && !target.delete()) throw new IOException("Cannot replace Runtime session");
        if (!temp.renameTo(target)) throw new IOException("Cannot commit Runtime session");
    }

    private Session read() throws Exception {
        File file = new File(context.getFilesDir(), SESSION_FILE);
        if (!file.isFile()) return null;
        if (file.length() <= 0 || file.length() > 32 * 1024) {
            throw new SecurityException("Runtime session state size invalid");
        }
        byte[] bytes = new byte[(int) file.length()];
        int offset = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int read = in.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
        }
        if (offset != bytes.length) throw new IOException("Incomplete Runtime session state");
        JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (json.getInt("schemaVersion") != 2) throw new SecurityException("Unknown Runtime session schema");
        return new Session(
                json.getString("sessionId"),
                normalizeSha256(json.getString("artifactSha256")),
                json.getString("packageName"),
                json.optString("versionName", ""),
                json.getLong("versionCode"),
                json.getInt("minSdk"),
                json.getInt("targetSdk"),
                json.optString("applicationClassName", ""),
                normalizeSha256(json.getString("primarySigningSha256")),
                json.getString("recipeId"),
                json.getInt("recipeVersion"),
                normalizeSha256(json.getString("planDigest")),
                json.getLong("createdAtMillis"),
                json.getLong("expiresAtMillis"));
    }

    private static void verifyArtifact(File artifact, String expectedHash) throws Exception {
        if (!artifact.isFile()) throw new SecurityException("Imported artifact is missing");
        if (artifact.canWrite()) throw new SecurityException("Imported artifact is unexpectedly writable");
        String actual = sha256(artifact);
        if (!constantEquals(normalizeSha256(expectedHash), actual)) {
            throw new SecurityException("Imported artifact integrity mismatch");
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[128 * 1024];
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file), buffer.length)) {
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", value));
        return out.toString();
    }

    private static boolean constantEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.US_ASCII),
                b.getBytes(StandardCharsets.US_ASCII));
    }

    private static String normalizeSha256(String value) {
        if (value == null) throw new SecurityException("Missing SHA-256");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != 64) throw new SecurityException("Malformed SHA-256");
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                throw new SecurityException("Malformed SHA-256");
            }
        }
        return normalized;
    }
}
