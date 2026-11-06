package com.dopplertask.dopplertask.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Authenticated encryption with a new nonce each time, so that equal secrets look different and a changed one is refused.
 * The key is the one given, or else one made for this installation the first time it is needed.
 */
@Component
public class CredentialCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final String givenKey;
    private final Path keyFile;
    private SecretKeySpec key;

    public CredentialCipher(@Value("${dopplertask.encryptionKey:}") String givenKey,
                            @Value("${dopplertask.keyFile:${user.home}/.dopplertask/encryption.key}") String keyFile) {
        this.givenKey = givenKey;
        this.keyFile = Path.of(keyFile);
    }

    public String encrypt(String text) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("A credential could not be encrypted.", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] bytes = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES));
            return new String(cipher.doFinal(bytes, NONCE_BYTES, bytes.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException e) {
            throw new CredentialProblemException("A credential could not be read. Check that the encryption key is the one it was saved with.");
        }
    }

    private synchronized SecretKeySpec key() throws IOException, GeneralSecurityException {
        if (key == null) {
            byte[] bytes = givenKey.isEmpty()
                    ? keyFromFile()
                    : MessageDigest.getInstance("SHA-256").digest(givenKey.getBytes(StandardCharsets.UTF_8));
            key = new SecretKeySpec(bytes, "AES");
        }
        return key;
    }

    private byte[] keyFromFile() throws IOException {
        if (!Files.exists(keyFile)) {
            byte[] bytes = new byte[KEY_BYTES];
            random.nextBytes(bytes);
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, Base64.getEncoder().encodeToString(bytes));
            if (keyFile.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
            }
        }
        return Base64.getDecoder().decode(Files.readString(keyFile).trim());
    }
}
