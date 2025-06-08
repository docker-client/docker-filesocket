package de.gesellix.docker.client.filesocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AsyncHijackSimulationTest {

  private static ExecutorService serverExecutor;
  private ServerSocket serverSocket;
  private int port;

  @BeforeAll
  static void setupExecutor() {
    serverExecutor = Executors.newSingleThreadExecutor();
  }

  @AfterAll
  static void teardownExecutor() {
    serverExecutor.shutdownNow();
  }

  @BeforeEach
  void startFakeEngine() throws IOException {
    serverSocket = new ServerSocket(0);
    port = serverSocket.getLocalPort();
    System.out.println("[Server] Listening on port " + port);

    // accept one connection
    serverExecutor.submit(() -> {
      try (Socket sock = serverSocket.accept()) {
        System.out.println("[Server] Accepted connection from " + sock.getRemoteSocketAddress());
        handleConnection(sock);
      } catch (IOException ignored) {
      }
    });
  }

  @AfterEach
  void stopFakeEngine() throws IOException {
    serverSocket.close();
  }

  private void handleConnection(Socket sock) throws IOException {
    BufferedReader httpIn = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
    OutputStream out = sock.getOutputStream();

    // --- 1) HTTP Upgrade handshake ---
    System.out.println("[Server] Starting HTTP handshake");
    String line;
    boolean upgrade = false;
    while (!(line = httpIn.readLine()).isEmpty()) {
      System.out.println("[Server] > " + line);
      if (line.toLowerCase().startsWith("upgrade: tcp")) {
        upgrade = true;
      }
    }
    if (!upgrade) {
      out.write("HTTP/1.1 400 Bad Request\r\nContent-Length:0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
      System.out.println("[Server] Sent 400 Bad Request");
      return;
    }
    String switching = "HTTP/1.1 101 Switching Protocols\r\n" +
        "Connection: Upgrade\r\n" +
        "Upgrade: tcp\r\n\r\n";
    out.write(switching.getBytes(StandardCharsets.UTF_8));
    out.flush();
    System.out.println("[Server] Sent 101 Switching Protocols");

    // --- 2) Concurrent log broadcaster ---
    ScheduledExecutorService logExec = Executors.newSingleThreadScheduledExecutor();
    Runnable broadcast = new Runnable() {
      private int i = 1;

      @Override
      public void run() {
        try {
          String msg = (i % 2 == 1)
              ? "INFO: regular update " + ((i + 1) / 2) + "\n"
              : "ERROR: something went wrong at step " + (i / 2) + "\n";
          out.write(msg.getBytes(StandardCharsets.UTF_8));
          out.flush();
          System.out.println("[Server] Sent log: " + msg.trim());
          i++;
          if (i > 20) {
            System.out.println("[Server] Finished sending logs");
            logExec.shutdown();
          }
        } catch (IOException e) {
          System.out.println("[Server] Log broadcaster shutting down due to " + e.getMessage());
          logExec.shutdown();
        }
      }
    };
    logExec.scheduleAtFixedRate(broadcast, 0, 100, TimeUnit.MILLISECONDS);

    // --- 3) Wait for socket close to exit ---
    try {
      sock.getInputStream().read();
    } catch (IOException ignored) {
    } finally {
      System.out.println("[Server] Socket closed, cleaning up");
      logExec.shutdownNow();
    }
  }

  @Test
  void testAsyncHijackWithLogFiltering() throws Exception {
    Pattern errorPattern = Pattern.compile("^ERROR:.*step ([0-9]+)$");
    try (Socket client = new Socket("127.0.0.1", port)) {
      System.out.println("[Client] Connected to server on port " + port);
      BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
      OutputStream os = client.getOutputStream();

      // 1) Perform HTTP Upgrade
      String req = "GET /containers/fake/hijack HTTP/1.1\r\n" +
          "Host: localhost\r\n" +
          "Connection: Upgrade\r\n" +
          "Upgrade: tcp\r\n\r\n";
      os.write(req.getBytes(StandardCharsets.UTF_8));
      os.flush();
      System.out.println("[Client] Sent HTTP upgrade request");

      // read status + headers
      String status = reader.readLine();
      System.out.println("[Client] Received: " + status);
      Assertions.assertTrue(status.contains("101 Switching Protocols"));
      while (!(status = reader.readLine()).isEmpty()) {
        System.out.println("[Client] > " + status);
      }

      // 2) Start filtering thread
      BlockingQueue<String> errors = new LinkedBlockingQueue<>();
      Thread filterThread = new Thread(() -> {
        try {
          String ln;
          while ((ln = reader.readLine()) != null) {
            System.out.println("[Client] Received log: " + ln);
            Matcher m = errorPattern.matcher(ln);
            if (m.matches()) {
              System.out.println("[Client] Matched ERROR pattern, step=" + m.group(1));
              errors.add(m.group(1));
            }
          }
        } catch (IOException ignored) {
        }
      });
      filterThread.start();

      // 3) Let logs flow and wait for first ERROR
      String step = errors.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(step, "Expected at least one ERROR log");
      System.out.println("[Client] Captured error at step " + step);

      // 4) Close client to terminate the server
      System.out.println("[Client] Closing connection");
      client.close();
      filterThread.join(1000);
    }
  }
}
