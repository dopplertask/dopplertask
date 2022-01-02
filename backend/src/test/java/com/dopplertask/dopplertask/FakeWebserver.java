package com.dopplertask.dopplertask;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class FakeWebserver {

    private HttpServer server;

    public FakeWebserver(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        HttpContext context = server.createContext("/");
        context.setHandler(exchange -> handleRequest(exchange));
        server.start();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String response = "<input type=\"text\" id=\"exampleInput\">";
        exchange.getResponseHeaders().add("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, response.getBytes().length);//response code and length
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    public void close() {
        server.stop(0);
    }
}
