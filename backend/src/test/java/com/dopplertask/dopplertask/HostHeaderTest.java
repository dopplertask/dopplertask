package com.dopplertask.dopplertask;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.jetty.autoconfigure.servlet.JettyServletWebServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Starts only the embedded web server. An empty dispatcher answers 404 once a request gets past host validation.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {JettyServletWebServerAutoConfiguration.class, DispatcherServletAutoConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HostHeaderTest {

    @LocalServerPort
    private int port;

    @Test
    public void testHostWithUnderscoreSuccess() throws IOException {
        Assert.assertEquals(404, statusForHost("my_host:" + port));
    }

    @Test
    public void testHostWithoutUnderscoreSuccess() throws IOException {
        Assert.assertEquals(404, statusForHost("myhost:" + port));
    }

    // Raw socket because HTTP clients refuse to send a custom Host header.
    private int statusForHost(String host) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            String request = "GET / HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            String statusLine = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }
}
