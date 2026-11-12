package android.system;
public class Os {
    public static void symlink(String target, String link) throws ErrnoException {
        try { java.nio.file.Files.createSymbolicLink(java.nio.file.Paths.get(link), java.nio.file.Paths.get(target)); }
        catch (java.io.IOException e) { throw new ErrnoException(e); }
    }
}
