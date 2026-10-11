package com.fixhua.runtime;

/** Session-bound synthetic environment visible only while transformed foreign code is executing. */
final class SyntheticExecutionScope {
    interface PackageQueryBackend {
        SyntheticPackageManagerView.QueryResult query(String packageName) throws Exception;
    }

    record Environment(String packageName, PackageQueryBackend packageQuery) {
        Environment {
            if (packageName == null || packageName.isBlank()) {
                throw new IllegalArgumentException("packageName");
            }
            if (packageQuery == null) throw new IllegalArgumentException("packageQuery");
        }
    }

    static final class Scope implements AutoCloseable {
        private final Environment previous;
        private boolean closed;

        private Scope(Environment environment) {
            previous = ACTIVE.get();
            ACTIVE.set(environment);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    private static final ThreadLocal<Environment> ACTIVE = new ThreadLocal<>();

    static Scope install(String packageName, PackageQueryBackend packageQuery) {
        return new Scope(new Environment(packageName, packageQuery));
    }

    static Environment require() {
        Environment environment = ACTIVE.get();
        if (environment == null) {
            throw new SecurityException("Synthetic execution environment is not active");
        }
        return environment;
    }

    private SyntheticExecutionScope() {}
}
