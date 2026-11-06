package com.dopplertask.dopplertask.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.persistence.AttributeConverter;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * What steps stored before credentials: it reads what is moved to them, and the address fields of MySQL and Jenkins.
 * Secrets are not kept with this, see {@link CredentialCipher}.
 */
@Component
public class ColumnEncryptor implements AttributeConverter<String, String> {

    private static final String AES = "AES";
    private static final String PUBLIC_DEFAULT_KEY = "KbPeShVmYq3t6v9y";

    private final Key key;

    public ColumnEncryptor(@Value("${dopplertask.encryptionKey:}") String secret) {
        key = new SecretKeySpec((secret.isEmpty() ? PUBLIC_DEFAULT_KEY : secret).getBytes(), AES);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        // A secret is optional, as with SSH without a password.
        if (attribute == null) {
            return null;
        }
        try {
            return Base64.getEncoder().encodeToString(cipher(Cipher.ENCRYPT_MODE).doFinal(attribute.getBytes()));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return new String(cipher(Cipher.DECRYPT_MODE).doFinal(Base64.getDecoder().decode(dbData)));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException(e);
        }
    }

    // One per use, since a Cipher keeps state and runs read and write values at the same time.
    private Cipher cipher(int mode) {
        try {
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(mode, key);
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
