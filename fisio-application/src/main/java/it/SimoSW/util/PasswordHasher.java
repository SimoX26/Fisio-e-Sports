package it.SimoSW.util;

import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Pattern;

public class PasswordHasher {

    private static final Pattern LEGACY_SHA256_HEX = Pattern.compile("^[a-f0-9]{64}$");
    private static final int BCRYPT_COST = 12;

    private PasswordHasher() {}

    public static String hash(String password) {
        return hashSha256(password);
    }

    public static String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hashing algorithm not available", e);
        }
    }

    public static String hashPassword(String plainPassword) {
        Objects.requireNonNull(plainPassword, "plainPassword");
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_COST));
    }

    public static boolean verifyPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }

        if (isLegacySha256Hash(storedHash)) {
            return Objects.equals(hashSha256(plainPassword), storedHash);
        }

        if (isBcryptHash(storedHash)) {
            try {
                return BCrypt.checkpw(plainPassword, storedHash);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }

        return false;
    }

    public static boolean isLegacySha256Hash(String storedHash) {
        return storedHash != null && LEGACY_SHA256_HEX.matcher(storedHash).matches();
    }

    private static boolean isBcryptHash(String storedHash) {
        return storedHash.startsWith("$2a$")
                || storedHash.startsWith("$2b$")
                || storedHash.startsWith("$2y$");
    }
}
