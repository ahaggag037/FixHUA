package com.fixhua.diagnostics;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class DiagnosticFeatureContract {
    static final String PRIVACY_BOUNDARY =
            "NO accounts/messages/photos/contacts/location/android_id/serial/ip; passwords are never collected; package names may be captured only for runtime attribution when Usage Access is granted";

    static final List<String> REQUIRED_ACTIONS = Collections.unmodifiableList(Arrays.asList(
            "root_probe",
            "guard_start",
            "guard_mode_cycle",
            "usage_access",
            "huawei_background_settings",
            "battery_exemption",
            "open_gbox",
            "diagnostic_toggle",
            "report_copy",
            "guard_stop",
            "refresh_status"
    ));

    static final List<String> AUTOMATIC_DIAGNOSTIC_REQUIREMENTS = Collections.unmodifiableList(Arrays.asList(
            "off_main_thread_sampling",
            "bounded_partial_wakelock",
            "automatic_incident_detection",
            "stable_recovery_close",
            "psi_unavailable_not_zero",
            "completion_notification"
    ));

    private DiagnosticFeatureContract() {}
}
