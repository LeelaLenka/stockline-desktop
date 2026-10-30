package com.bharat.stockline.db;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Password hashing with PBKDF2 (built into Java), stored as "iterations:salt:hash" */
public final class Passwords {

    private static final int ITERATIONS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {}

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password.toCharArray(), salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return ITERATIONS + ":" + b64.encodeToString(salt) + ":" + b64.encodeToString(hash);
    }

    public static boolean verify(String password, String stored) {
        String[] parts = stored.split(":");
        if (parts.length != 3) return false;
        Base64.Decoder b64 = Base64.getDecoder();
        byte[] salt = b64.decode(parts[1]);
        byte[] expected = b64.decode(parts[2]);
        byte[] actual = derive(password.toCharArray(), salt, Integer.parseInt(parts[0]));
        return java.security.MessageDigest.isEqual(expected, actual); // constant-time compare
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        }
    }
}
