package it.SimoSW.desktop;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

final class EncryptedPatientCache {
    private static final int MAX_BYTES = 5_000_000;
    private static final int IV_BYTES = 12;
    private final Path path;
    private final String backendUrl;

    EncryptedPatientCache(String backendUrl) {
        this.backendUrl = backendUrl;
        String name = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(backendUrl)).substring(0, 16);
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String dataRoot = windows ? System.getenv("APPDATA") : System.getenv("XDG_DATA_HOME");
        if (dataRoot == null || dataRoot.isBlank()) {
            dataRoot = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        }
        path = Path.of(dataRoot, "Fisio-e-Sports", "patients-" + name + ".cache");
    }

    String load(String token) {
        if (!valid(token)) return null;
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) return null;
            byte[] stored = Files.readAllBytes(path);
            if (stored.length <= IV_BYTES + 16) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(token), new GCMParameterSpec(128, stored, 0, IV_BYTES));
            return new String(cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return null;
        }
    }

    boolean save(String token, String json) {
        if (!valid(token) || json == null || json.isBlank() || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES - 64) {
            return false;
        }
        Path temporary = null;
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(token), new GCMParameterSpec(128, iv));
            byte[] plain = json.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = cipher.doFinal(plain);
            byte[] stored = Arrays.copyOf(iv, iv.length + encrypted.length);
            System.arraycopy(encrypted, 0, stored, iv.length, encrypted.length);
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "patients-", ".tmp");
            Files.write(temporary, stored);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception exception) {
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    boolean clear() {
        try {
            return !Files.exists(path) || Files.deleteIfExists(path);
        } catch (IOException exception) {
            return false;
        }
    }

    private SecretKeySpec key(String token) {
        return new SecretKeySpec(sha256("fisio-patients-cache-v1:" + backendUrl + ":" + token), "AES");
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 non disponibile", exception);
        }
    }

    private static boolean valid(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }
}
