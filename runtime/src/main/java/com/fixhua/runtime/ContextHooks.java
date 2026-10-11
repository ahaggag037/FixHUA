package com.fixhua.runtime;

import android.content.Context;

/** Entry points used when transformed foreign code asks Context for guest identity. */
public final class ContextHooks {
    /**
     * Synthetic replacement for Context#getPackageName(). The host Context argument is ignored so
     * transformed guest code cannot accidentally inherit the Runtime package identity.
     */
    public static String getPackageName(Context ignoredHostContext) {
        return SyntheticExecutionScope.require().packageName();
    }

    private ContextHooks() {}
}
