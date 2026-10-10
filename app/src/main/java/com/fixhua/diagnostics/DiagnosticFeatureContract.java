package com.fixhua.diagnostics;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class DiagnosticFeatureContract {
    static final String PRIVACY_BOUNDARY =
            "NO accounts/messages/photos/contacts/location/android_id/serial/ip; passwords are never collected";

    static final List<String> REQUIRED_ACTIONS = Collections.unmodifiableList(Arrays.asList(
            "root_probe",
            "guard_start",
            "guard_mode_cycle",
            "usage_access",
            "huawei_background_settings",
            "battery_exemption",
            "open_gbox",
            "diagnostic_toggle",
            "lag_marker",
            "report_copy",
            "guard_stop",
            "refresh_status"
    ));

    private DiagnosticFeatureContract() {}
}
