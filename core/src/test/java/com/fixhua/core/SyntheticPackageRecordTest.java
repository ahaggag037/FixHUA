package com.fixhua.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class SyntheticPackageRecordTest {
    private static final String ARTIFACT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CERT = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void canonicalizesListsAndProducesStableIdentity() {
        SyntheticPackageRecord record = new SyntheticPackageRecord(
                ARTIFACT.toUpperCase(),
                1234,
                "com.example.target",
                null,
                42,
                29,
                35,
                null,
                false,
                false,
                List.of("android.permission.INTERNET", "android.permission.INTERNET", ""),
                List.of(CERT.toUpperCase(), CERT));

        assertEquals(ARTIFACT, record.artifactSha256());
        assertEquals("", record.versionName());
        assertEquals("", record.applicationClassName());
        assertEquals(List.of("android.permission.INTERNET"), record.requestedPermissions());
        assertEquals(List.of(CERT), record.signingCertificateSha256());
        assertEquals("com.example.target@42#" + ARTIFACT, record.identityKey());
        assertEquals(ARTIFACT, record.contentAddress());
    }

    @Test
    public void rejectsMissingSignerAndMalformedDigest() {
        assertThrows(IllegalArgumentException.class, () -> new SyntheticPackageRecord(
                ARTIFACT, 100, "com.example.target", "1", 1, 29, 35, "",
                false, false, List.of(), List.of()));

        IllegalArgumentException badHash = assertThrows(IllegalArgumentException.class, () ->
                new SyntheticPackageRecord("xyz", 100, "com.example.target", "1", 1, 29, 35, "",
                        false, false, List.of(), List.of(CERT)));
        assertTrue(badHash.getMessage().contains("sha256"));
    }
}
