package it.SimoSW.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class SecureTokenStore {
    private static final String ATTRIBUTE = "fisio-e-sports-desktop";
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final boolean LINUX = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    private final String namespace;

    SecureTokenStore(String backendUrl) {
        if (BackendEndpoint.LOCAL_URL.equals(backendUrl)) {
            namespace = "";
        } else {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(backendUrl.getBytes(StandardCharsets.UTF_8));
                namespace = "-" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 non disponibile", exception);
            }
        }
    }

    String load() {
        try {
            if (WINDOWS) {
                Path path = windowsPath();
                if (!Files.isRegularFile(path)) return "";
                String encrypted = Files.readString(path, StandardCharsets.US_ASCII);
                String token = runPowerShell("$data=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim());"
                        + "$plain=[Security.Cryptography.ProtectedData]::Unprotect($data,$null,"
                        + "[Security.Cryptography.DataProtectionScope]::CurrentUser);"
                        + "[Console]::Out.Write([Text.Encoding]::UTF8.GetString($plain))", encrypted);
                return valid(token) ? token : null;
            }
            if (LINUX) {
                String token = run(new String[]{"/usr/bin/secret-tool", "lookup", "app", ATTRIBUTE + namespace}, "");
                return token == null || token.isBlank() ? "" : valid(token.trim()) ? token.trim() : null;
            }
        } catch (IOException exception) {
            return null;
        }
        return null;
    }

    boolean save(String token) {
        if (!valid(token)) return false;
        clear();
        try {
            if (WINDOWS) {
                String encrypted = runPowerShell("$plain=[Text.Encoding]::UTF8.GetBytes([Console]::In.ReadToEnd().Trim());"
                        + "$data=[Security.Cryptography.ProtectedData]::Protect($plain,$null,"
                        + "[Security.Cryptography.DataProtectionScope]::CurrentUser);"
                        + "[Console]::Out.Write([Convert]::ToBase64String($data))", token);
                if (encrypted == null || encrypted.isBlank()) return false;
                Path path = windowsPath();
                Files.createDirectories(path.getParent());
                Path temporary = Files.createTempFile(path.getParent(), "remember-", ".tmp");
                try {
                    Files.writeString(temporary, encrypted.trim(), StandardCharsets.US_ASCII);
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
                return true;
            }
            if (LINUX) {
                return run(new String[]{"/usr/bin/secret-tool", "store", "--label=Fisio e Sports", "app", ATTRIBUTE + namespace}, token) != null;
            }
        } catch (IOException exception) {
            return false;
        }
        return false;
    }

    boolean clear() {
        try {
            if (WINDOWS) {
                Files.deleteIfExists(windowsPath());
                return true;
            }
            if (LINUX) return run(new String[]{"/usr/bin/secret-tool", "clear", "app", ATTRIBUTE + namespace}, "") != null;
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    private static boolean valid(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private Path windowsPath() throws IOException {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) throw new IOException("APPDATA non disponibile");
        return Path.of(appData, "Fisio-e-Sports", "remember-token" + namespace + ".dpapi");
    }

    private static String runPowerShell(String script, String input) {
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) return null;
        String powershell = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
        return run(new String[]{powershell, "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded}, input);
    }

    private static String run(String[] command, String input) {
        try {
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (var output = process.getOutputStream()) {
                output.write(input.getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) return null;
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
