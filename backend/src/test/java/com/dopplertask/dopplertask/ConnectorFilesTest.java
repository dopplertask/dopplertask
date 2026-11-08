package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.RunFiles;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorAction;
import com.dopplertask.dopplertask.domain.action.connector.Connectors;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** A connector sends a file of the run as a file part or as base64, and keeps a file it downloads in the run. */
public class ConnectorFilesTest {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7', 0, (byte) 0xE2, (byte) 0xFF};

    private final VariableExtractorUtil variables = new VariableExtractorUtil(Application.lockedTemplateEngine(-1));
    private HttpServer service;
    private final AtomicReference<byte[]> received = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();

    @Before
    public void startService() throws IOException {
        service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/", exchange -> {
            received.set(exchange.getRequestBody().readAllBytes());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] answer = switch (exchange.getRequestURI().getPath()) {
                case "/report" -> PDF;
                case "/report.csv" -> "a,b\n1,2".getBytes(StandardCharsets.UTF_8);
                case "/attachment" -> ("{\"data\": \"" + Base64.getEncoder().encodeToString(PDF) + "\"}").getBytes(StandardCharsets.UTF_8);
                default -> "{\"ok\": true}".getBytes(StandardCharsets.UTF_8);
            };
            exchange.sendResponseHeaders(200, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        service.start();
        Connectors.INSTANCE.setLocalServerForTests("http://127.0.0.1:" + service.getAddress().getPort());
    }

    @After
    public void stopService() {
        Connectors.INSTANCE.setLocalServerForTests(null);
        service.stop(0);
    }

    @Test
    public void testAFileGoesInItsOwnPartOfAMultipartBodySuccess() {
        TaskExecution run = new TaskExecution();
        RunFiles.INSTANCE.keep(run, "june.pdf", PDF);
        ActionResult result = step("upload", Map.of("document", "june.pdf", "title", "June")).perform(run, variables);
        Assert.assertTrue(result.getErrorMsg(), result.getErrorMsg().isEmpty());
        String boundary = contentType.get().replaceAll(".*boundary=", "");
        Assert.assertTrue(contentType.get(), contentType.get().startsWith("multipart/form-data; boundary="));
        String body = new String(received.get(), StandardCharsets.ISO_8859_1);
        // The parts come in the order of the body: the file, then the title.
        Assert.assertEquals("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"june.pdf\"\r\nContent-Type: application/pdf\r\n\r\n"
                + new String(PDF, StandardCharsets.ISO_8859_1) + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nJune\r\n--" + boundary + "--\r\n", body);
    }

    @Test
    public void testAFileInAJsonBodyIsSentAsBase64Success() {
        TaskExecution run = new TaskExecution();
        RunFiles.INSTANCE.keep(run, "june.pdf", PDF);
        step("attach", Map.of("document", "june.pdf")).perform(run, variables);
        Assert.assertEquals("{\"content\":\"" + Base64.getEncoder().encodeToString(PDF) + "\"}", new String(received.get(), StandardCharsets.UTF_8));
    }

    @Test
    public void testADownloadIsKeptInTheRunWithItsBytesSuccess() {
        TaskExecution run = new TaskExecution();
        ActionResult result = step("download", Map.of()).perform(run, variables);
        Assert.assertArrayEquals(PDF, RunFiles.INSTANCE.read(run, "report.pdf"));
        Assert.assertEquals("application/pdf", result.getOutputVariables().get("mimeType"));
        Assert.assertEquals(PDF.length, result.getOutputVariables().get("size"));
    }

    @Test
    public void testAFileTheRunDoesNotHaveExplainsWhyFail() {
        ActionResult result = step("upload", Map.of("document", "missing.pdf")).perform(new TaskExecution(), variables);
        Assert.assertTrue(result.getErrorMsg(), result.getErrorMsg().contains("no file called \"missing.pdf\""));
    }

    @Test
    public void testABodyThatIsAFileIsTheFilesBytesSuccess() {
        TaskExecution run = new TaskExecution();
        RunFiles.INSTANCE.keep(run, "june.pdf", PDF);
        step("replace", Map.of("document", "june.pdf")).perform(run, variables);
        Assert.assertArrayEquals(PDF, received.get());
        Assert.assertEquals("application/pdf", contentType.get());
    }

    @Test
    public void testARelatedMultipartBodyTypesEachPartInItsOrderSuccess() {
        TaskExecution run = new TaskExecution();
        RunFiles.INSTANCE.keep(run, "june.pdf", PDF);
        step("upload_related", Map.of("document", "june.pdf", "title", "June")).perform(run, variables);
        String boundary = contentType.get().replaceAll(".*boundary=", "");
        Assert.assertTrue(contentType.get(), contentType.get().startsWith("multipart/related; boundary="));
        Assert.assertEquals("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{\"name\":\"June\"}\r\n--" + boundary
                + "\r\nContent-Type: application/pdf\r\n\r\n" + new String(PDF, StandardCharsets.ISO_8859_1) + "\r\n--" + boundary + "--\r\n",
                new String(received.get(), StandardCharsets.ISO_8859_1));
    }

    @Test
    public void testATextBodyAndATextAnswerStayTextSuccess() {
        step("send_csv", Map.of("rows", "a,b\n1,2")).perform(new TaskExecution(), variables);
        Assert.assertEquals("a,b\n1,2", new String(received.get(), StandardCharsets.UTF_8));
        Assert.assertEquals("{\"status\":200,\"csv\":\"a,b\\n1,2\"}", step("read_csv", Map.of()).perform(new TaskExecution(), variables).getOutput());
    }

    @Test
    public void testAScriptKeepsAFileFromTheAnswerForTheRunSuccess() {
        TaskExecution run = new TaskExecution();
        Assert.assertEquals("{\"file\":\"scan.pdf\"}", step("attachment", Map.of()).perform(run, variables).getOutput());
        Assert.assertArrayEquals(PDF, RunFiles.INSTANCE.read(run, "scan.pdf"));
    }

    private static ConnectorAction step(String operation, Map<String, String> settings) {
        ConnectorAction step = ConnectorAction.of("files-test");
        step.setOperation(operation);
        settings.forEach(step::setSetting);
        step.useCredentialValues(Map.of("key", "k"));
        return step;
    }
}
