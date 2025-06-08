package de.gesellix.docker.client.filesocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
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

    // server loop
    serverExecutor.submit(() -> {
      try (Socket sock = serverSocket.accept()) {
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
    BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
    OutputStream out = sock.getOutputStream();

    // 1) HTTP handshake
    String line;
    boolean upgrade = false;
    while (!(line = in.readLine()).isEmpty()) {
      if (line.toLowerCase().startsWith("upgrade: tcp")) {
        upgrade = true;
      }
    }
    if (!upgrade) {
      out.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes(StandardCharsets.UTF_8));
      return;
    }
    out.write(("HTTP/1.1 101 Switching Protocols\r\n" +
        "Connection: Upgrade\r\n" +
        "Upgrade: tcp\r\n\r\n")
        .getBytes(StandardCharsets.UTF_8));
    out.flush();

    // 2) Asynchronous log broadcaster
    ExecutorService logExecutor = Executors.newSingleThreadExecutor();
    PipedOutputStream logPipeOut = new PipedOutputStream();
    PipedInputStream logPipeIn = new PipedInputStream(logPipeOut);

    logExecutor.submit(() -> {
      try (BufferedWriter logWriter = new BufferedWriter(new OutputStreamWriter(logPipeOut, StandardCharsets.UTF_8))) {
        for (int i = 1; i <= 10; i++) {
          logWriter.write("INFO: regular update " + i + "\n");
          logWriter.flush();
          Thread.sleep(100);  // simulate delay
          logWriter.write("ERROR: something went wrong at step " + i + "\n");
          logWriter.flush();
          Thread.sleep(100);
        }
      } catch (IOException | InterruptedException ignored) {
      }
    });

    // 3) Merge client-input and log-feed onto the socket output
    ExecutorService merger = Executors.newFixedThreadPool(2);
    // Forward client input back to client (echo-like)
    merger.submit(() -> {
      try {
        int b;
        while ((b = sock.getInputStream().read()) != -1) {
          out.write(b);
          out.flush();
        }
      } catch (IOException ignored) {
      }
    });
    // Forward log feed to client
    merger.submit(() -> {
      try {
        int b;
        while ((b = logPipeIn.read()) != -1) {
          out.write(b);
          out.flush();
        }
      } catch (IOException ignored) {
      }
    });

    // wait until socket closes
    try {
      sock.getInputStream().read();
    } catch (IOException ignored) {
    } finally {
      logExecutor.shutdownNow();
      merger.shutdownNow();
    }
  }

  @Test
  void testAsyncHijackWithLogFiltering() throws Exception {
    Pattern errorPattern = Pattern.compile("^ERROR:.*step ([0-9]+)$");
    try (Socket client = new Socket("127.0.0.1", port)) {
      InputStream is = client.getInputStream();
      OutputStream os = client.getOutputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

      // 1) Perform HTTP upgrade handshake
      os.write(("GET /containers/fake/hijack HTTP/1.1\r\n" +
          "Host: localhost\r\n" +
          "Connection: Upgrade\r\n" +
          "Upgrade: tcp\r\n\r\n")
          .getBytes(StandardCharsets.UTF_8));
      os.flush();
      String statusLine = reader.readLine();
      Assertions.assertTrue(statusLine.contains("101 Switching Protocols"));
      // consume remaining headers
      while (!reader.readLine().isEmpty()) {
      }

      // 2) Start a reader thread to filter ERROR lines
      BlockingQueue<String> errors = new LinkedBlockingQueue<>();
      Thread filterThread = new Thread(() -> {
        try {
          String ln;
          while ((ln = reader.readLine()) != null) {
            Matcher m = errorPattern.matcher(ln);
            if (m.matches()) {
              errors.add(m.group(1)); // capture step number
            }
          }
        } catch (IOException ignored) {
        }
      });
      filterThread.start();

      // 3) Simulate client sending some commands
      os.write("client: hello\n".getBytes(StandardCharsets.UTF_8));
      os.flush();

      // The client doesn't particularly care about INFO lines but just wants errors
      // Wait for at least one ERROR capture
      String step = errors.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(step, "Should receive at least one error line");
      System.out.println("Captured error at step " + step);

      // 4) Close client side to end the interaction
      client.close();
      filterThread.join(1000);
    }
  }
}
