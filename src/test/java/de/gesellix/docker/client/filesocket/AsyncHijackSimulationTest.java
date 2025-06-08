package de.gesellix.docker.client.filesocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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

    // HTTP Upgrade handshake
    System.out.println("[Server] Starting HTTP handshake");
    String header;
    boolean upgrade = false;
    while (!(header = httpIn.readLine()).isEmpty()) {
      System.out.println("[Server] > " + header);
      if (header.toLowerCase().startsWith("upgrade: tcp")) {
        upgrade = true;
      }
    }
    if (!upgrade) {
      out.write("HTTP/1.1 400 Bad Request\r\nContent-Length:0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
      System.out.println("[Server] Sent 400 Bad Request");
      return;
    }
    String switching = "HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: tcp\r\n\r\n";
    out.write(switching.getBytes(StandardCharsets.UTF_8));
    out.flush();
    System.out.println("[Server] Sent 101 Switching Protocols");

    // Concurrent log broadcaster
    ScheduledExecutorService logExec = Executors.newSingleThreadScheduledExecutor();
    Runnable broadcastLogs = new Runnable() {
      private int i = 1;

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
          System.out.println("[Server] Log broadcaster shutting down: " + e.getMessage());
          logExec.shutdown();
        }
      }
    };
    logExec.scheduleAtFixedRate(broadcastLogs, 0, 100, TimeUnit.MILLISECONDS);

    // Echo client messages
    ExecutorService echoExec = Executors.newSingleThreadExecutor();
    echoExec.submit(() -> {
      try (BufferedReader clientReader = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))) {
        String clientLine;
        while ((clientLine = clientReader.readLine()) != null) {
          String echo = "ECHO: " + clientLine + "\n";
          out.write(echo.getBytes(StandardCharsets.UTF_8));
          out.flush();
          System.out.println("[Server] Echoed: " + echo.trim());
        }
      } catch (IOException ignored) {
      }
    });

    // Wait for log broadcaster to finish
    try {
      logExec.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException ignored) {
    } finally {
      echoExec.shutdownNow();
      System.out.println("[Server] Cleaning up after logs complete");
    }
  }

  @Test
  void testAsyncHijackWithLogFilteringAndEcho() throws Exception {
    Pattern errorPattern = Pattern.compile("^ERROR:.*step ([0-9]+)$");
    try (Socket client = new Socket("127.0.0.1", port)) {
      System.out.println("[Client] Connected to server on port " + port);
      BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
      OutputStream os = client.getOutputStream();

      // HTTP Upgrade
      String req = "GET /containers/fake/hijack HTTP/1.1\r\n" +
          "Host: localhost\r\n" +
          "Connection: Upgrade\r\n" +
          "Upgrade: tcp\r\n\r\n";
      os.write(req.getBytes(StandardCharsets.UTF_8));
      os.flush();
      System.out.println("[Client] Sent HTTP upgrade request");
      String status = reader.readLine();
      System.out.println("[Client] Received: " + status);
      Assertions.assertTrue(status.contains("101 Switching Protocols"));
      String headerLine;
      while (!(headerLine = reader.readLine()).isEmpty()) {
        System.out.println("[Client] > " + headerLine);
      }

      // Queues for ERROR and ECHO
      BlockingQueue<String> errors = new LinkedBlockingQueue<>();
      BlockingQueue<String> echoes = new LinkedBlockingQueue<>();

      // Reader thread
      Thread readerThread = new Thread(() -> {
        try {
          String ln;
          while ((ln = reader.readLine()) != null) {
            System.out.println("[Client] Received stream: " + ln);
            Matcher m = errorPattern.matcher(ln);
            if (m.matches()) {
              System.out.println("[Client] Matched ERROR, step=" + m.group(1));
              errors.add(m.group(1));
            } else if (ln.startsWith("ECHO: ")) {
              String msg = ln.substring(6);
              System.out.println("[Client] Captured echo: " + msg);
              echoes.add(msg);
            }
          }
        } catch (IOException ignored) {
        }
      });
      readerThread.start();

      // Sender thread
      Thread senderThread = new Thread(() -> {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
          for (int i = 1; i <= 5; i++) {
            String msg = "client-message-" + i;
            writer.write(msg + "\n");
            writer.flush();
            System.out.println("[Client] Sent: " + msg);
            Thread.sleep(150);
          }
        } catch (IOException | InterruptedException ignored) {
        }
      });
      senderThread.start();

      // Assert ERROR
      String step = errors.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(step, "Expected at least one ERROR log");
      System.out.println("[Client] ERROR at step " + step);

      // Assert ECHO
      String echoMsg = echoes.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(echoMsg, "Expected at least one ECHO");
      System.out.println("[Client] ECHO received: " + echoMsg);

      // Close
      System.out.println("[Client] Closing connection");
      client.close();
      readerThread.join(1000);
      senderThread.join(1000);
    }
  }
}
