package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.integration.JsonApi;
import com.dopplertask.dopplertask.domain.action.integration.mail.Emails;
import org.junit.Assert;
import org.junit.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * An email reads into what mailparser gives, the reader of the originals. Each sample in resources/emails was read by
 * mailparser with tools/connector-harness, and the json beside it is what mailparser gave.
 */
public class EmailsTest {

    @Test
    public void testAnEmailWithPartsAndAnAttachmentReadsAsMailparserReadsItSuccess() throws IOException {
        ObjectNode read = (ObjectNode) Emails.INSTANCE.read(resource("multipart.eml"));
        JsonNode attachment = read.remove("attachments").get(0);
        Assert.assertEquals(json("multipart.json"), read);
        Assert.assertEquals("invoice.pdf", attachment.path("filename").asText());
        Assert.assertEquals("application/pdf", attachment.path("contentType").asText());
        Assert.assertEquals("%PDF-1.7 test", new String(Base64.getDecoder().decode(attachment.path("base64").asText()), StandardCharsets.UTF_8));
    }

    @Test
    public void testAPlainEmailWithFoldedHeadersAndLinksReadsAsMailparserReadsItSuccess() throws IOException {
        ObjectNode read = (ObjectNode) Emails.INSTANCE.read(resource("plain.eml"));
        Assert.assertEquals(0, read.remove("attachments").size());
        Assert.assertEquals(json("plain.json"), read);
    }

    @Test
    public void testAnEmailInBase64UrlAsGmailGivesItReadsTheSameSuccess() throws IOException {
        String raw = resource("plain.eml");
        Assert.assertEquals(Emails.INSTANCE.read(raw), Emails.INSTANCE.readEncodedOrPlain(Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8))));
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = EmailsTest.class.getResourceAsStream("/emails/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonNode json(String name) throws IOException {
        return JsonApi.INSTANCE.getMapper().readTree(resource(name));
    }
}
