package de.gesellix.docker.client.filesocket;

import static de.gesellix.docker.client.filesocket.FileSocket.SOCKET_MARKER;
import static java.net.InetAddress.getByAddress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.Okio;
import okio.Sink;
import okio.Source;

public class UpgradeClient {

  public static void main(String[] args) throws Exception {
    String pipePath = "\\\\.\\pipe\\java_echo_test";
    NamedPipeSocket client = new NamedPipeSocket();
    client.connect(new InetSocketAddress(getByAddress(
        client.encodeHostname(pipePath.replaceAll("\\\\", "/")), new byte[]{0, 0, 0, 0}),
        0));
//    client.connect(new InetSocketAddress(getByAddress(client.encodeHostname("//./pipe/docker_engine"), new byte[] {0, 0, 0, 0}), 0));
//    client.connect(pipePath);

    OutputStream os = client.getOutputStream();
    os.write("line1\n".getBytes(StandardCharsets.UTF_8));
    os.flush();
    Thread.sleep(1000);
    os.write("line2\n".getBytes(StandardCharsets.UTF_8));
    os.flush();

    InputStream is = client.getInputStream();
    byte[] buf = new byte[1024];
    int read;
    while ((read = is.read(buf)) != -1) {
      System.out.println("Java got: " + new String(buf, 0, read));
    }
  }

  public static void main2(String[] args) throws Exception {
    //    String host = "localhost";
    //    int port = 8080;
    //    HttpUrl url = new HttpUrl.Builder()
    //        .scheme("http")
    //        .host(host)
    //        .port(port)
    //        .build();

    NamedPipeSocketFactory socketFactory = new NamedPipeSocketFactory();
    String socketAddress = "//./pipe/docker_engine";
//    UnixSocketFactory socketFactory = new UnixSocketFactory();
//    String socketAddress = "/var/run/docker.sock";

    // docker run --rm --name hijacking -it alpine:edge cat
    // docker run --rm --name hijacking -it --entrypoint /cat gesellix/echo-server:2025-07-27T22-12-00
    HttpUrl url = new HttpUrl.Builder()
        .scheme("http")
        .host(new HostnameEncoder().encode(socketAddress) + SOCKET_MARKER)
        .addPathSegments("containers/hijacking/attach")
        .encodedQuery("logs=true&stream=true&stdin=true&stdout=true&stderr=true")
        .build();

    OkHttpClient client = new OkHttpClient.Builder()
        .socketFactory(socketFactory)
        .dns(socketFactory)
        .connectTimeout(Duration.ZERO)
        .callTimeout(Duration.ZERO)
        .readTimeout(Duration.ZERO)
        .writeTimeout(Duration.ZERO)
        .build();
    Call call = client.newCall(
        new Request.Builder()
            .url(url)
            .addHeader("Connection", "upgrade")
            .addHeader("Upgrade", "tcp")
            //        .addHeader("Upgrade", "testproto")
            .post(RequestBody.EMPTY)
            .build()
    );
    try (Response response = call.execute()) {
      int code = response.code();
      System.out.println("Response code: " + code);

      okio.Socket socket = response.socket();
      if (socket == null) {
        throw new IllegalStateException("Socket is null");
      }

      // READER (Container -> STDOUT)
      new Thread(() -> {
        try (
            Source source = Okio.buffer(socket.getSource());
            Sink sink = Okio.buffer(Okio.sink(System.out))
        ) {
          Buffer greeting = new Buffer();
          greeting.write("Enter something to see the echo. ^D to disconnect\n".getBytes());
          sink.write(greeting, greeting.size());
          sink.flush();

          Buffer buffer = new Buffer();
          for (long byteCount; (byteCount = source.read(buffer, 8192L)) != -1; ) {
            sink.write(buffer, byteCount);
            sink.flush();
          }
//          while (!source.exhausted()) {
//            System.out.println(source.readUtf8Line());
//          }
        } catch (IOException e) {
          e.printStackTrace();
          System.err.println("Error while reading from socket: " + e.getMessage());
        } finally {
          System.out.println("Disconnected");
        }
      }).start();

      // WRITER (STDIN -> Container)
      try (
          Source source = Okio.buffer(Okio.source(System.in));
          Sink sink = Okio.buffer(socket.getSink())
      ) {
        Buffer buffer = new Buffer();
        for (long byteCount; (byteCount = source.read(buffer, 8192L)) != -1; ) {
          sink.write(buffer, byteCount);
          sink.flush();
        }
//        Scanner scanner = new Scanner(System.in);
//        while (scanner.hasNextLine()) {
//          String input = scanner.nextLine();
//          sink.writeUtf8(input + "\n");
//          sink.flush();
//        }
      } catch (IOException e) {
        e.printStackTrace();
        System.err.println("Error while writing to socket: " + e.getMessage());
      } finally {
        System.out.println("Disconnected");
      }
    }
  }
}
