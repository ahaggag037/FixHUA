package com.fixhua.runtime;

import android.util.Log;

import com.fixhua.core.CoreModels;
import com.fixhua.core.ExecutionEvidence;

import java.util.concurrent.atomic.AtomicLong;

/** Process-local ordered evidence stream. Controller persistence can consume this format later. */
final class ExecutionEvidenceEmitter {
    private static final String TAG = "FixHUAEvidence";
    private static final AtomicLong SEQUENCE = new AtomicLong();

    static void emit(
            int capabilityId,
            String providerId,
            ExecutionEvidence.Decision decision,
            CoreModels.TruthScope truthScope,
            String subject,
            String outcome) {
        ExecutionEvidence.Event event = new ExecutionEvidence.Event(
                SEQUENCE.incrementAndGet(),
                capabilityId,
                providerId,
                decision,
                truthScope,
                subject,
                outcome);
        Log.i(TAG, event.canonicalLine());
    }

    private ExecutionEvidenceEmitter() {}
}
