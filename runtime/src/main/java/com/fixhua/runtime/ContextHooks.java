package com.fixhua.runtime;

import android.content.Context;

/** Entry points used by transformed Context identity calls. */
public final class ContextHooks {
    private static final ThreadLocal<String> PACKAGE_NAME = new ThreadLocal<>();

    static final class Scope implements AutoCloseable {
        private final String previous;
        private boolean closed;

        private Scope(String packageName) {
            previous = PACKAGE_NAME.get();
            PACKAGE_NAME.set(packageName);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) PACKAGE_NAME.remove();
            else PACKAGE_NAME.set(previous);
        }
    }

    static Scope install(String packageName) {
        if (packageName == null || packageName.isBlank()) throw new IllegalArgumentException("packageName");
        return new Scope(packageName);
    }

    public static String getPackageName(Context ignoredHostContext) {
        String value = PACKAGE_NAME.get();
        if (value == null) throw new SecurityException("Synthetic context environment is not active");
        return value;
    }

    private ContextHooks() {}
}
