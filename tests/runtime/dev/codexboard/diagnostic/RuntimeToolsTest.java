package dev.codexboard.diagnostic;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class RuntimeToolsTest {
    private static final class FakeContext extends Context {
        final ApplicationInfo info = new ApplicationInfo(); final File files;
        FakeContext(Path lib, Path files) { info.nativeLibraryDir=lib.toString(); this.files=files.toFile(); }
        public ApplicationInfo getApplicationInfo(){return info;}
        public File getFilesDir(){return files;}
    }
    private static void check(boolean ok, String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("codex-runtime-test"), files=Files.createDirectory(root.resolve("files"));
        Path lib=Files.createDirectory(root.resolve("lib")), systemShell=Files.createFile(root.resolve("android-sh"));
        for(String name:Arrays.asList("libbash.so","librg.so"))check(Files.createFile(lib.resolve(name)).toFile().setExecutable(true),"set executable");
        check(systemShell.toFile().setExecutable(true),"set system shell executable");
        FakeContext context=new FakeContext(lib,files);
        File dir=RuntimeTools.prepare(context,systemShell.toFile());
        check(Files.readSymbolicLink(dir.toPath().resolve("bash")).equals(lib.resolve("libbash.so")),"bash target");
        check(Files.readSymbolicLink(dir.toPath().resolve("rg")).equals(lib.resolve("librg.so")),"rg target");
        check(Files.readSymbolicLink(dir.toPath().resolve("sh")).equals(systemShell),"sh target");
        check(RuntimeTools.prepare(context,systemShell.toFile()).equals(dir),"idempotent");
        check(RuntimeTools.path(dir,null).endsWith(":/system/bin:/system/xbin:/vendor/bin"),"fallback PATH");
        Path newer=Files.createDirectory(root.resolve("new-lib"));
        for(String name:Arrays.asList("libbash.so","librg.so"))check(Files.createFile(newer.resolve(name)).toFile().setExecutable(true),"new lib executable");
        File newDir=RuntimeTools.prepare(new FakeContext(newer,files),systemShell.toFile());
        check(!newDir.equals(dir),"updated APK gets distinct aliases");
        Files.delete(newDir.toPath().resolve("bash")); Files.write(newDir.toPath().resolve("bash"),new byte[]{42});
        try { RuntimeTools.prepare(new FakeContext(newer,files),systemShell.toFile());throw new AssertionError("collision accepted"); }
        catch(IOException expected){check(Files.readAllBytes(newDir.toPath().resolve("bash"))[0]==42,"collision must not overwrite file");}
        System.out.println("RuntimeTools: symlink targets, update, PATH and conflict checks passed");
    }
}
