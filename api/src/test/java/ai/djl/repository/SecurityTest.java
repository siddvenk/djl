package ai.djl.repository;

import ai.djl.util.ZipUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

public class SecurityTest {

    /** PoC for Symbolic Link Attack (Successful Exploit) */
//    @Test
//    public void testSymbolicLinkBypass() throws
//            IOException {
//        Path baseDir = Paths.get("build/output");
//        Files.createDirectories(baseDir);
//        System.out.println("Using base dir: " + baseDir);
//        Path zipFile = baseDir.resolve("symlink_attack.zip");
//
//        if (!System.getProperty("os.name").toLowerCase().contains("windows"))
//        {
//            // Create a sensitive file outside baseDir
//            Path sensitiveFile =
//                    baseDir.getParent().resolve("sensitive_data.txt");
//            Files.write(sensitiveFile, "Super secret data".getBytes());
//            System.out.println("Wrote sensitive data to " + sensitiveFile);
//
//            // Create a symbolic link inside the zip extraction folder
//            // pointing to sensitive file
//            Path symlink = baseDir.resolve("malicious_symlink");
//            Files.createSymbolicLink(symlink, sensitiveFile);
//            System.out.println("created symlink " + symlink + " to " + sensitiveFile);
//
//            createZip(zipFile, new String[]{"malicious_symlink"}, new
//                    String[]{""});
//
//            try (InputStream is = Files.newInputStream(zipFile)) {
//                ZipUtils.unzip(is, baseDir);
//                System.out.println("[❌] Symbolic link attack succeeded (VULNERABLE!)");
//            } catch (IOException e) {
//                System.out.println("[✅] Symbolic link attack blocked: " +
//                        e.getMessage());
//            }
//        } else {
//            System.out.println("[⚠] Skipping symbolic link test (not supported on Windows)");
//        }
//    }

    /** PoC for Unicode and Encoded Variants (Successful Exploit) */
    @Test
    public void testUnicodeBypass() throws IOException {
        Path baseDir = Files.createTempDirectory("zip-exploit");
        System.out.println("Using base dir: " + baseDir);
        Path zipFile = baseDir.resolve("unicode_traversal.zip");
        String[] payloads = {"%2e%2e/evil.txt", "⠃⠑⠑/evil.txt"};

        for (String name : payloads) {
            createZip(zipFile, new String[]{name}, new String[]{"Malicious content"});
            try (InputStream is = Files.newInputStream(zipFile)) {
                ZipUtils.unzip(is, baseDir);
                System.out.println("[❌] Unicode bypass succeeded (VULNERABLE!): " + name);
            } catch (IOException e) {
                System.out.println("[✅] Unicode bypass blocked: " +
                        e.getMessage());
            }
        }
    }

//    @Test
//    public void testRepoSymlink() throws IOException {
//        Path baseDir = Paths.get("build/output");
//        Files.createDirectories(baseDir);
//        System.out.println("Using base dir: " + baseDir);
//        Path zipFile = baseDir.resolve("repo_symlink.zip");
//        Path sensitiveFile =
//                baseDir.getParent().resolve("sensitive_data.txt");
//        Files.write(sensitiveFile, "Super secret data".getBytes());
//        System.out.println("Wrote sensitive data to " + sensitiveFile);
//
//        // Create a symbolic link inside the zip extraction folder
//        // pointing to sensitive file
//        Path symlink = baseDir.resolve("malicious_symlink");
//        Files.createSymbolicLink(symlink, sensitiveFile);
//        System.out.println(symlink + "is symlink " + Files.isSymbolicLink(symlink));
//        System.out.println(sensitiveFile + " is symlink " + Files.isSymbolicLink(sensitiveFile));
//        System.out.println("created symlink " + symlink + " to " + sensitiveFile);
//        createZip(zipFile, new String[]{"malicious_symlink"}, new
//                String[]{""});
//        SimpleRepository repo = new SimpleRepository("test-repo", baseDir.toUri(), zipFile);
//        List<MRL> mrls = repo.getResources();
//        Artifact artifact = mrls.get(0).getDefaultArtifact();
//        repo.prepare(artifact);
//    }
//
//    @Test
//    public void testRepoUnicode() throws IOException {
//        Path baseDir = Paths.get("build/output");
//        Files.createDirectories(baseDir);
//        System.out.println("Using base dir: " + baseDir);
//        String[] payloads = {"%2e%2e/", "⠃⠑⠑/evil.txt"};
//
//        int i = 0;
//        for (String name : payloads) {
//            Path zipFile = baseDir.resolve("unicode_traversal" + i + ".zip");
//            createZip(zipFile, new String[]{name}, new String[]{"Malicious content"});
//            SimpleRepository repo = new SimpleRepository("test-repo" + i, baseDir.toUri(), zipFile);
//            List<MRL> mrls = repo.getResources();
//            Artifact artifact = mrls.get(0).getDefaultArtifact();
//            repo.prepare(artifact);
//            i += 1;
//        }
//    }

    /** Helper function to create a ZIP file with malicious content */
    private static void createZip(Path zipPath, String[] fileNames,
            String[] contents) throws IOException {
        try (ZipOutputStream zos = new
                ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (int i = 0; i < fileNames.length; i++) {
                ZipEntry entry = new
                        ZipEntry(fileNames[i]);
                System.out.println("writing zip entry " + entry.getName());
                zos.putNextEntry(entry);

                zos.write(contents[i].getBytes());
                zos.closeEntry();
            }
        }
    }

    /** Normalizes Unicode filenames to prevent bypasses */
    private static String normalizeFilename(String name) {
        System.out.println("before normalizeFilename: " + name);
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);
        System.out.println("after normalizeFilename: " + normalized);
        return normalized;
    }
}
