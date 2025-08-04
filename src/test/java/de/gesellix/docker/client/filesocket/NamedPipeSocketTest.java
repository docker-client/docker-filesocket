package de.gesellix.docker.client.filesocket;

import static com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE;
import static com.sun.jna.platform.win32.WinBase.PIPE_ACCESS_DUPLEX;
import static com.sun.jna.platform.win32.WinBase.PIPE_READMODE_BYTE;
import static com.sun.jna.platform.win32.WinBase.PIPE_TYPE_BYTE;
import static com.sun.jna.platform.win32.WinBase.PIPE_WAIT;
import static com.sun.jna.platform.win32.WinError.ERROR_PIPE_CONNECTED;
import static java.net.InetAddress.getByAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

class NamedPipeSocketTest {

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void canConnect() throws IOException {
    try (NamedPipeSocket namedPipeSocket = new NamedPipeSocket()) {
      namedPipeSocket.connect(new InetSocketAddress(
          getByAddress(namedPipeSocket.encodeHostname("//./pipe/docker_engine"), new byte[]{0, 0, 0, 0}),
          0));
    }
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  public void testInProcessServerClient() throws Exception {
    String pipePath = "\\\\.\\pipe\\filesocket_test";

    Thread serverThread = new Thread(() -> {
      WinNT.HANDLE pipeHandle = Kernel32.INSTANCE.CreateNamedPipe(
          pipePath,
          PIPE_ACCESS_DUPLEX,
          PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT,
          1, // max instances
          4096, // out buffer size
          4096, // in buffer size
          0,    // default timeout
          null  // security attributes
      );
      assertNotEquals(INVALID_HANDLE_VALUE, pipeHandle, "Invalid pipe handle");

      boolean connected = Kernel32.INSTANCE.ConnectNamedPipe(pipeHandle, null)
          || Kernel32.INSTANCE.GetLastError() == ERROR_PIPE_CONNECTED;
      assertTrue(connected, "Client failed to connect");

      byte[] readBuffer = new byte[4096];
      IntByReference bytesRead = new IntByReference();
      boolean ok = NamedPipeUtils.readToBuffer(pipeHandle, readBuffer, bytesRead);
      assertTrue(ok, "Read failed");
      assertTrue(bytesRead.getValue() > 0, "Server did not receive data");

      String echo = "Echo: " + new String(readBuffer);
      IntByReference written = new IntByReference();
      ok = NamedPipeUtils.writeFromBuffer(pipeHandle, echo.getBytes(StandardCharsets.UTF_8), echo.length(), written);
      assertTrue(ok, "Write failed");
      Kernel32.INSTANCE.DisconnectNamedPipe(pipeHandle);
      NamedPipeUtils.closeHandle(pipeHandle);
    });

    serverThread.start();
    Thread.sleep(300);

    NamedPipeSocket client = new NamedPipeSocket();
    client.connect(pipePath);

    try (OutputStream os = client.getOutputStream();
         InputStream is = client.getInputStream()) {

      os.write("Hello NamedPipe OKIO Test!\n".getBytes(StandardCharsets.UTF_8));
      os.flush();

      byte[] buf = new byte[1024];
      int read = is.read(buf);
      assertTrue(read > 0, "No response received");

      String resp = new String(buf, 0, read, StandardCharsets.UTF_8);
      assertTrue(resp.contains("Echo:"), "Response should contain Echo");
    }

    client.close();
    serverThread.join();
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  public void testCloseDuringPendingRead_DoesNotHang() throws Exception {
    String pipePath = "\\\\.\\pipe\\filesocket_test_abort";

    CountDownLatch pipeCreated = new CountDownLatch(1);

    Thread serverThread = new Thread(() -> {
      WinNT.HANDLE pipeHandle = Kernel32.INSTANCE.CreateNamedPipe(
          pipePath,
          PIPE_ACCESS_DUPLEX,
          PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT,
          1, 4096, 4096, 0, null
      );
      assertNotEquals(INVALID_HANDLE_VALUE, pipeHandle, "Invalid pipe handle");

      // Signal that pipe is ready to accept connections
      pipeCreated.countDown();

      boolean connected = Kernel32.INSTANCE.ConnectNamedPipe(pipeHandle, null) ||
          Kernel32.INSTANCE.GetLastError() == ERROR_PIPE_CONNECTED;
      assertTrue(connected, "Client failed to connect");

      // Keep the pipe open without sending data
      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
      } catch (InterruptedException ignored) {
      }

      Kernel32.INSTANCE.DisconnectNamedPipe(pipeHandle);
      NamedPipeUtils.closeHandle(pipeHandle);
    });

    serverThread.start();

    // Wait until server has created the named pipe before connecting
    assertTrue(pipeCreated.await(2, TimeUnit.SECONDS), "Server did not create pipe in time");

    NamedPipeSocket client = new NamedPipeSocket();
    client.connect(pipePath);

    InputStream is = client.getInputStream();

    // Thread that will block on read
    Thread readerThread = new Thread(() -> {
      try {
        byte[] buf = new byte[1024];
        int read = is.read(buf); // should unblock when close() calls CancelIoEx
        assertEquals(-1, read, "Read should indicate EOF after CancelIoEx");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    readerThread.start();

    // Give the reader a moment to start blocking
    Thread.sleep(300);

    long start = System.currentTimeMillis();
    client.close(); // should trigger CancelIoEx and not hang
    long duration = System.currentTimeMillis() - start;

    assertTrue(duration < 2000, "Close took too long, possibly hung");

    readerThread.join();
    serverThread.join();
  }

  @Disabled
  @Test
  @EnabledOnOs(OS.WINDOWS)
  void testPlainHijacked() throws Exception {
//    String pipeName = "docker_engine";
    String pipeName = "hijack_test";
//    String pipeName = "java_echo_test";
    String namedPipeName = "//./pipe/" + pipeName;

    // Always start the npipe server fresh for this test
//    Process namedPipeServer = null;
    Process namedPipeServer = createNamedPipeServer(pipeName);

    // Wait for the pipe to appear
    boolean found = false;
    long timeoutMs = 5000;
    long start = System.currentTimeMillis();

    while (System.currentTimeMillis() - start < timeoutMs) {
      List<String> pipes = WindowsNamedPipeLister.listPipes();
      if (pipes.contains(pipeName)) {
        found = true;
        break;
      }
      Thread.sleep(100);
    }

    // If not found, print available pipes for debugging
    if (!found) {
      List<String> available = WindowsNamedPipeLister.listPipes();
      System.err.println("Available pipes: " + available);
    }

    assertTrue(found, "Named pipe '" + pipeName + "' not found in system pipe list");

    // docker run --rm --name hijacking -it alpine:edge cat
    // docker run --rm --name hijacking -it --entrypoint /cat gesellix/echo-server:2025-07-27T22-12-00
    String containerId = "hijacking";

    // Connect to the Go npipe server
    NamedPipeSocket namedPipeSocket = new NamedPipeSocket();
    namedPipeSocket.connect(new InetSocketAddress(
        getByAddress(namedPipeSocket.encodeHostname(namedPipeName), new byte[]{0, 0, 0, 0}),
        0));
    namedPipeSocket.setKeepAlive(true);

    OutputStream outputStream = namedPipeSocket.getOutputStream();

    // Send hijack upgrade request
    List<String> headers = new ArrayList<>();
    headers.add("POST /containers/" + containerId + "/attach?logs=true&stream=true&stdin=true&stdout=true&stderr=true HTTP/1.1");
    headers.add("Host: localhost");
    headers.add("Content-Length: 0");
    headers.add("Connection: Upgrade");
    headers.add("Upgrade: tcp");

    try {
      for (String line : headers) {
        System.out.println(">>|" + line);
        outputStream.write(line.getBytes(StandardCharsets.UTF_8));
        outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
      }
      outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
      outputStream.flush();
      System.out.println("[TEST] Sent hijack upgrade request");
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Reader thread - will likely block until connection closes
    AtomicBoolean readFinished = new AtomicBoolean(false);
    List<String> receivedLines = Collections.synchronizedList(new ArrayList<>());

    Thread readerThread = new Thread(() -> {
      try (InputStream inputStream = namedPipeSocket.getInputStream()) {
        byte[] buf = new byte[1024];
        int read;
        while ((read = inputStream.read(buf)) != -1) {
          String chunk = new String(buf, 0, read, StandardCharsets.UTF_8);
          System.out.println("[TEST] Reader got: " + chunk);
          receivedLines.add(chunk);
        }
      } catch (IOException e) {
        System.out.println("[TEST] Reader closed: " + e);
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        readFinished.set(true);
        System.out.println("[TEST] Reader thread finished");
      }
    });

    // Writer thread - sends data periodically
    Thread writerThread = new Thread(() -> {
      String content = "yeah";
      for (int n = 5; n > 0; n--) {
        try {
          String msg = n + " " + content + " - " + new Date() + "\n";
          System.out.println("[TEST] Writer sending: " + msg.trim());
          outputStream.write(msg.getBytes(StandardCharsets.UTF_8));
          outputStream.flush();
          Thread.sleep(500);
        } catch (IOException | InterruptedException e) {
          System.out.println("[TEST] Writer stopped: " + e);
          break;
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      System.out.println("[TEST] Writer finished");
    });

    readerThread.start();
    Thread.sleep(1000); // let reader start
    writerThread.start();

    // Wait for writer to finish
    writerThread.join();

    // Close socket - this may trigger Docker to deliver buffered stdin to container
    namedPipeSocket.close();

    // Wait a little for reader to drain
    readerThread.join(2000);

    // Check if we got anything at all
    if (receivedLines.isEmpty()) {
      System.out.println("[TEST] No data was received before close - this is EXPECTED for Docker hijack over npipe.");
    } else {
      System.out.println("[TEST] Data was received: " + receivedLines);
    }

    // Cleanup
    if (namedPipeServer != null) {
      if (namedPipeServer.isAlive()) {
        namedPipeServer.destroy();
      }
      namedPipeServer.waitFor(5, TimeUnit.SECONDS);
      boolean createNamedPipeFinishedBeforeTimeout = namedPipeServer.waitFor(10, TimeUnit.MINUTES);
      int createNamedPipeExitValue = namedPipeServer.exitValue();
    }
  }

  private Process createNamedPipeServer(String pipeName) throws InterruptedException, IOException {
    Process namedPipeServer;
    String npipeImage = "gesellix/npipe:2025-07-27T22-12-00";
    exec(5, TimeUnit.MINUTES, "docker", "pull", npipeImage);
    exec(1, TimeUnit.MINUTES, "docker", "create", "--name", "npipe", npipeImage);
    exec(1, TimeUnit.MINUTES, "docker", "cp", "npipe:/npipe.exe", "./npipe.exe");
    exec(1, TimeUnit.MINUTES, "docker", "rm", "npipe");
    exec(1, TimeUnit.MINUTES, "docker", "rmi", npipeImage);
    namedPipeServer = exec("./npipe.exe", "\\\\.\\pipe\\" + pipeName);
    return namedPipeServer;
  }

  /**
   * Run the command and wait for the process to finish or until the timeout is reached.
   */
  private Process exec(long timeout, TimeUnit timeoutUnit, String... command) throws InterruptedException, IOException {
    Process process = new ProcessBuilder(command)
//        .inheritIO()
        .start();
    Executors.newSingleThreadExecutor().submit(new StreamReader(process.getInputStream(), System.out::println));
    Executors.newSingleThreadExecutor().submit(new StreamReader(process.getErrorStream(), System.err::println));
    boolean processFinishedBeforeTimeout = process.waitFor(timeout, timeoutUnit);
    int processExitValue = process.exitValue();
    return process;
  }

  /**
   * Run the command and don't wait for the process to finish.
   */
  private Process exec(String... command) throws IOException {
    Process process = new ProcessBuilder(command)
//        .inheritIO()
        .start();
    Executors.newSingleThreadExecutor().submit(new StreamReader(process.getInputStream(), System.out::println));
    Executors.newSingleThreadExecutor().submit(new StreamReader(process.getErrorStream(), System.err::println));
    return process;
  }

  static class StreamReader implements Runnable {

    private final BufferedReader reader;
    private final Consumer<String> consumer;

    public StreamReader(InputStream inputStream, Consumer<String> consumer) {
      this.reader = new BufferedReader(new InputStreamReader(inputStream));
      this.consumer = consumer;
    }

    @Override
    public void run() {
      reader.lines().forEach(consumer);
    }
  }
}
