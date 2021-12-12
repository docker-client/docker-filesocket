package de.gesellix.docker.client.filesocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.net.InetAddress.getByAddress;

class NamedPipeSocketTest {

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void canConnect() throws IOException {
    NamedPipeSocket namedPipeSocket = new NamedPipeSocket();
    namedPipeSocket.connect(new InetSocketAddress(getByAddress(namedPipeSocket.encodeHostname("//./pipe/docker_engine"), new byte[] {0, 0, 0, 0}), 0));
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void testPlainHijacked() throws Exception {
    String namedPipeName = "//./pipe/hijack_test";
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
    }
    catch (IOException e) {
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
        }
        catch (InterruptedException ignored) {
          // ignored
        }
        int read = 0;
        try {
          read = inputStream.read(buf, 0, 1024);
        }
        catch (IOException e) {
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
        }
        catch (Exception e) {
          e.printStackTrace();
        }
        finally {
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
  }
}
