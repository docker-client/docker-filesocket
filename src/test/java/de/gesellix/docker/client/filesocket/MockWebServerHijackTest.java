package de.gesellix.docker.client.filesocket;

import static okhttp3.internal.http.HttpStatusCodesKt.HTTP_SWITCHING_PROTOCOLS;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.SocketHandler;
import mockwebserver3.junit5.StartStop;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Socket;

class MockWebServerHijackTest {

  @StartStop
  public final MockWebServer mockServer = new MockWebServer();

  @Test
  void testTcpUpgradeWithStreamHandler() throws Exception {
    // Prepare a MockResponse with SocketHandler for TCP upgrade
    System.out.println("[Test] Enqueueing upgrade response with socketHandler");
    SocketHandler mockSocketHandler = socket -> {
      System.out.println("[Server] socketHandler invoked");
      BufferedSource in = Okio.buffer(socket.getSource());
      BufferedSink out = Okio.buffer(socket.getSink());

      // Start log broadcaster
      ScheduledExecutorService logExec = Executors.newSingleThreadScheduledExecutor();
      logExec.scheduleAtFixedRate(() -> {
        try {
          String msg = "ERROR: step test" + System.lineSeparator();
          out.writeUtf8(msg);
          out.flush();
          System.out.println("[Server] Sent log: " + msg.trim());
        } catch (IOException e) {
          System.out.println("[Server] logExec shutdown");
          logExec.shutdown();
        }
      }, 0, 100, TimeUnit.MILLISECONDS);

      // Echo client input
      CountDownLatch echoSeen = new CountDownLatch(1);
      Executors.newSingleThreadExecutor().submit(() -> {
        try {
          String line;
          while ((line = in.readUtf8Line()) != null) {
            System.out.println("[Server] Received client line: " + line);
            String echo = "ECHO: " + line + "\n";
            out.writeUtf8(echo);
            out.flush();
            System.out.println("[Server] Echoed: " + echo.trim());
            echoSeen.countDown();           // signal we saw it
          }
          System.out.println("[Server] Echo thread loop ended");
        } catch (IOException e) {
          System.out.println("[Server] Echo thread ended due to error");
        }
      });

      // Keep open briefly then cancel
      try {
// keep logs going…
        logExec.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("[Server] Cancelling stream");
// wait up to 2s for that echo
        // Wait until we see an echo, or 2s, whichever comes first
        echoSeen.await(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      } finally {
        socket.cancel();
      }
    };

    mockServer.enqueue(new MockResponse.Builder()
        .code(HTTP_SWITCHING_PROTOCOLS)
        .headers(Headers.of(
            "Connection",
            "upgrade",
            "Upgrade",
            "tcp",
            "Content-Type",
//            "text/plain; charset=UTF-8"
            "application/vnd.docker.raw-stream"
        ))
        // keep the TCP connection open and hand it to our streamHandler
//        .socketPolicy(SocketPolicy.KeepOpen.INSTANCE)
        .socketHandler(mockSocketHandler)
        .build());
    mockServer.start();
    System.out.println("[Test] MockWebServer started at: " + mockServer.getPort());

    // Create client and perform upgrade
    OkHttpClient client = new OkHttpClient();
    Request request = new Request.Builder()
        .url(mockServer.url("/containers/fake/hijack"))
        .header("Connection", "Upgrade")
        .header("Upgrade", "tcp")
        .build();

    System.out.println("[Client] Executing request");
    Call call = client.newCall(request);
    Response response = call.execute();
    System.out.println("[Client] Received response code: " + response.code());
    Assertions.assertEquals(101, response.code());

    // Retrieve Socket via response.socket
    Socket socket = response.socket();
    Assertions.assertNotNull(socket, "Socket must be available after upgrade");
    System.out.println("[Client] Obtained Socket");

    BufferedSource reader = Okio.buffer(socket.getSource());
    BufferedSink writer = Okio.buffer(socket.getSink());

    BlockingQueue<String> received = new LinkedBlockingQueue<>();

    // Reader thread
    Thread readerThread = new Thread(() -> {
      try {
        String line;
        while ((line = reader.readUtf8Line()) != null) {
          System.out.println("[Client] Received stream: " + line);
          received.add(line);
        }
      } catch (IOException ignored) {
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

    socket.cancel();
    System.out.println("[Client] Cancelled streams and shutting down");
  }
}
