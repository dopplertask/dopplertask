package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.service.CredentialCipher;
import com.dopplertask.dopplertask.service.CredentialProblemException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;

public class CredentialCipherTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private CredentialCipher withKey(String key) {
        return new CredentialCipher(key, folder.getRoot() + "/unused.key");
    }

    @Test
    public void testTheSameSecretLooksDifferentEachTimeAndReadsBackSuccess() {
        CredentialCipher cipher = withKey("a key");
        String first = cipher.encrypt("the secret");
        Assert.assertNotEquals(first, cipher.encrypt("the secret"));
        Assert.assertEquals("the secret", cipher.decrypt(first));
    }

    @Test
    public void testAnotherKeyOrAChangedValueIsRefusedFail() {
        String stored = withKey("a key").encrypt("the secret");
        Assert.assertThrows(CredentialProblemException.class, () -> withKey("another key").decrypt(stored));
        byte[] bytes = Base64.getDecoder().decode(stored);
        bytes[bytes.length - 1] ^= 1;
        Assert.assertThrows(CredentialProblemException.class, () -> withKey("a key").decrypt(Base64.getEncoder().encodeToString(bytes)));
    }

    @Test
    public void testWithoutAKeyOneIsMadeThatOnlyTheOwnerCanReadAndThatLastsSuccess() throws IOException {
        Path keyFile = folder.getRoot().toPath().resolve("deep/er/encryption.key");
        String stored = new CredentialCipher("", keyFile.toString()).encrypt("the secret");
        Assert.assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(keyFile)));
        Assert.assertEquals("the secret", new CredentialCipher("", keyFile.toString()).decrypt(stored));
    }
}
