package de.gesellix.docker.client.filesocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.http1.Streams;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

class MockWebServerHijackTest {

  private ServerSocket passthrough;
  private int port;
  private Thread serverThread;

  @BeforeEach
  void setup() throws IOException {
    passthrough = new ServerSocket(0);
    port = passthrough.getLocalPort();
    System.out.println("[Test] Starting passthrough ServerSocket on port " + port);

    serverThread = new Thread(() -> {
      try (Socket socket = passthrough.accept()) {
        System.out.println("[Server] Accepted connection");
        InputStream rawIn = socket.getInputStream();
        OutputStream rawOut = socket.getOutputStream();

        // Read HTTP request headers until blank line
        BufferedReader reader = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.UTF_8));
        String line;
        while (!(line = reader.readLine()).isEmpty()) {
          System.out.println("[Server] Request line: " + line);
        }

        // Send 101 Switching Protocols response
        String resp = "HTTP/1.1 101 Switching Protocols\nConnection: Upgrade\nUpgrade: tcp\n\n";
        rawOut.write(resp.getBytes(StandardCharsets.UTF_8));
        rawOut.flush();
        System.out.println("[Server] Sent 101 response");

        // Wrap okio streams for raw I/O
        BufferedSource in = Okio.buffer(Okio.source(rawIn));
        BufferedSink out = Okio.buffer(Okio.sink(rawOut));

        // Start log broadcaster
        ScheduledExecutorService logExec = Executors.newSingleThreadScheduledExecutor();
        logExec.scheduleAtFixedRate(() -> {
          try {
            String msg = "ERROR: step test\n";
            out.writeUtf8(msg).flush();
            System.out.println("[Server] Sent log: " + msg.trim());
          } catch (IOException e) {
            System.out.println("[Server] logExec shutdown");
            logExec.shutdown();
          }
        }, 0, 100, TimeUnit.MILLISECONDS);

        // Echo client input
        while ((line = in.readUtf8Line()) != null) {
          System.out.println("[Server] Received client line: " + line);
          String echo = "ECHO: " + line + "\n";
          out.writeUtf8(echo).flush();
          System.out.println("[Server] Echoed: " + echo.trim());
        }

        // cleanup
        logExec.shutdownNow();
        System.out.println("[Server] Connection closed");
      } catch (IOException e) {
        System.out.println("[Server] Echo thread ended due to error");
        e.printStackTrace();
      }
    });
    serverThread.start();
  }

  @AfterEach
  void teardown() throws IOException, InterruptedException {
    System.out.println("[Test] Shutting down ServerSocket");
    passthrough.close();
    serverThread.join(1000);
  }

  @Test
  void testTcpUpgradeWithPassthrough() throws Exception {
    // Create client and perform upgrade
    OkHttpClient client = new OkHttpClient();
    Request request = new Request.Builder()
        .url(new URL("http://localhost:" + port + "/containers/fake/hijack"))
        .header("Connection", "Upgrade")
        .header("Upgrade", "tcp")
        .build();

    System.out.println("[Client] Executing request");
    Call call = client.newCall(request);
    Response response = call.execute();
    System.out.println("[Client] Received response code: " + response.code());
    Assertions.assertEquals(101, response.code());

    // Retrieve Streams via response.streams
    Streams streams = response.streams();
    Assertions.assertNotNull(streams, "Streams must be available after upgrade");
    System.out.println("[Client] Obtained Streams");

    BufferedSource reader = streams.getSource();
    BufferedSink writer = streams.getSink();

    BlockingQueue<String> received = new LinkedBlockingQueue<>();

    // Reader thread
    Thread readerThread = new Thread(() -> {
      try {
        String line;
        while ((line = reader.readUtf8Line()) != null) {
          System.out.println("[Client] Received stream: " + line);
          received.add(line);
        }
      } catch (IOException e) {
        System.out.println("[Client] Reader thread ended");
      }
    });
    readerThread.start();

    // Send client message with newline
    String msg = "hello-test\n";
    System.out.println("[Client] Sending: " + msg.trim());
    writer.writeUtf8(msg).flush();
    System.out.println("[Client] Sent: " + msg.trim());

    // Assert both echo and error
    String err = received.poll(2, TimeUnit.SECONDS);
    String echo = received.poll(2, TimeUnit.SECONDS);

    Assertions.assertTrue(err != null && err.startsWith("ERROR:"), "Expected error, got: " + err);
    Assertions.assertTrue(echo != null && echo.startsWith("ECHO:"), "Expected echo, got: " + echo);

    streams.cancel();
    System.out.println("[Client] Cancelled streams and shutting down");
  }
}
