package com.fixhua.diagnostics;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Internal root bridge. No arbitrary user-supplied commands are accepted. */
final class RootShell {
    static final class Result {
        final boolean finished;
        final int exitCode;
        final String output;

        Result(boolean finished, int exitCode, String output) {
            this.finished = finished;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output.trim();
        }

        boolean ok() {
            return finished && exitCode == 0;
        }
    }

    private RootShell() {}

    static boolean hasRoot() {
        Result result = run("id", 2500L);
        return result.ok() && result.output.contains("uid=0");
    }

    static Result run(String fixedCommand, long timeoutMs) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", fixedCommand)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                process.waitFor(250L, TimeUnit.MILLISECONDS);
                if (process.isAlive()) process.destroyForcibly();
                return new Result(false, -1, readSmall(process.getInputStream()));
            }
            return new Result(true, process.exitValue(), readSmall(process.getInputStream()));
        } catch (Throwable t) {
            return new Result(false, -1, t.getClass().getSimpleName());
        } finally {
            if (process != null) {
                try {
                    process.getInputStream().close();
                } catch (Throwable ignored) {
                }
                try {
                    process.getOutputStream().close();
                } catch (Throwable ignored) {
                }
                try {
                    process.getErrorStream().close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String readSmall(InputStream input) {
        if (input == null) return "";
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int total = 0;
            int n;
            while ((n = input.read(buffer)) >= 0 && total < 16384) {
                int write = Math.min(n, 16384 - total);
                out.write(buffer, 0, write);
                total += write;
                if (write < n) break;
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Throwable ignored) {
            return "";
        }
    }
}
