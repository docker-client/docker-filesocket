package de.gesellix.docker.client.filesocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.net.InetAddress.getByAddress;

class NamedPipeSocketTest {

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void canConnect() throws IOException {
    try (NamedPipeSocket namedPipeSocket = new NamedPipeSocket()) {
      namedPipeSocket.connect(new InetSocketAddress(getByAddress(namedPipeSocket.encodeHostname("//./pipe/docker_engine"), new byte[] {0, 0, 0, 0}), 0));
    }
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void testPlainHijacked() throws Exception {
    String namedPipeName = "//./pipe/hijack_test";

    Process namedPipeServer = null;
    if (!new File(namedPipeName).exists()) {
      namedPipeServer = createNamedPipeServer();
    }

    String containerId = "hijacking";

    NamedPipeSocket namedPipeSocket = new NamedPipeSocket();
    namedPipeSocket.connect(new InetSocketAddress(getByAddress(namedPipeSocket.encodeHostname(namedPipeName), new byte[] {0, 0, 0, 0}), 0));
    namedPipeSocket.setKeepAlive(true);

    OutputStream outputStream = namedPipeSocket.getOutputStream();
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
    } catch (IOException e) {
      e.printStackTrace();
    }

    Object lock = new Object();

    final AtomicBoolean readCanBeFinished = new AtomicBoolean(false);
    Thread readerThread = new Thread(() -> {
      InputStream inputStream = namedPipeSocket.getInputStream();

      byte[] buf = new byte[1024];
      while (!readCanBeFinished.get()) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignored) {
          // ignored
        }
        int read = 0;
        try {
          read = inputStream.read(buf, 0, 1024);
        } catch (IOException e) {
          e.printStackTrace();
        }
        if (read > -1) {
          System.out.println("<<|" + new String(buf, 0, read));
        }
      }
      System.out.println("// reader done //");
    });

    Thread writerThread = new Thread(() -> {

      String content = "yeah";
      int n = 5;
      while (n > 0) {
        try {
          System.out.println(">>: " + n);
          outputStream.write((n + " " + content + " - " + new Date()).getBytes(StandardCharsets.UTF_8));

          Thread.sleep(500);
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          n--;
        }
      }

      System.out.println("// writer done //");
      readCanBeFinished.set(true);
      synchronized (lock) {
        lock.notify();
      }
    });

    synchronized (lock) {
      readerThread.start();
      Thread.sleep(1000);
      writerThread.start();
      lock.wait(10000);
      System.out.println("// lock done //");
    }
    namedPipeSocket.close();

    if (namedPipeServer != null) {
      if (namedPipeServer.isAlive()) {
        namedPipeServer.destroy();
      }
      boolean createNamedPipeFinishedBeforeTimeout = namedPipeServer.waitFor(10, TimeUnit.MINUTES);
      int createNamedPipeExitValue = namedPipeServer.exitValue();
    }
  }

  private Process createNamedPipeServer() throws InterruptedException, IOException {
    Process namedPipeServer;
    String npipeImage = "gesellix/npipe:2025-06-08T23-00-00";
    exec(5, TimeUnit.MINUTES, "docker", "pull", npipeImage);
    exec(1, TimeUnit.MINUTES, "docker", "create", "--name", "npipe", npipeImage);
    exec(1, TimeUnit.MINUTES, "docker", "cp", "npipe:/npipe.exe", "./npipe.exe");
    exec(1, TimeUnit.MINUTES, "docker", "rm", "npipe");
    exec(1, TimeUnit.MINUTES, "docker", "rmi", npipeImage);
    namedPipeServer = exec("./npipe.exe", "\\\\.\\pipe\\hijack_test");
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
