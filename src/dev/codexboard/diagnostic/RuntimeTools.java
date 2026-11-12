package dev.codexboard.diagnostic;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;

/** Stable command names pointing at executables installed from the APK. */
final class RuntimeTools {
    static File prepare(Context context) throws IOException {
        return prepare(context, new File("/system/bin/sh"));
    }
    static File prepare(Context context, File systemShell) throws IOException {
        File nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File directory = new File(context.getFilesDir(), "runtime-tools-" + Integer.toHexString(nativeDir.getAbsolutePath().hashCode()));
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("도구 폴더 생성 실패");
        createAlias(directory, "bash", new File(nativeDir, "libbash.so"));
        createAlias(directory, "rg", new File(nativeDir, "librg.so"));
        createAlias(directory, "sh", systemShell);
        return directory;
    }
    private static void createAlias(File directory, String name, File target) throws IOException {
        if (!target.isFile() || !target.canExecute()) throw new IOException("실행 도구를 찾을 수 없습니다: " + name);
        File alias = new File(directory, name);
        if (Files.exists(alias.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isSymbolicLink(alias.toPath()) || !Files.readSymbolicLink(alias.toPath()).toString().equals(target.getAbsolutePath()))
                throw new IOException("도구 경로 충돌: " + name);
            return;
        }
        try { Os.symlink(target.getAbsolutePath(), alias.getAbsolutePath()); }
        catch (ErrnoException e) { throw new IOException("도구 경로 생성 실패: " + name, e); }
    }
    static String path(File directory, String existing) {
        String fallback = "/system/bin:/system/xbin:/vendor/bin";
        return directory.getAbsolutePath() + ":" + ((existing == null || existing.isEmpty()) ? fallback : existing);
    }
}
