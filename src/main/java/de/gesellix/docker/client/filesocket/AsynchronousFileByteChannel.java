package de.gesellix.docker.client.filesocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

// Copy from https://github.com/docker-java/docker-java/blob/1da0e3ce211d963248b64484fc38101869a5a61e/docker-java-transport/src/main/java/com/github/dockerjava/transport/NamedPipeSocket.java#L88
class AsynchronousFileByteChannel implements AsynchronousByteChannel {

  private final AsynchronousFileChannel fileChannel;

  AsynchronousFileByteChannel(AsynchronousFileChannel fileChannel) {
    this.fileChannel = fileChannel;
  }

  @Override
  public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
    fileChannel.read(dst, 0, attachment, new CompletionHandler<Integer, A>() {
      @Override
      public void completed(Integer read, A attachment) {
        handler.completed(read > 0 ? read : -1, attachment);
      }

      @Override
      public void failed(Throwable exc, A attachment) {
        if (exc instanceof AsynchronousCloseException) {
          handler.completed(-1, attachment);
          return;
        }
        handler.failed(exc, attachment);
      }
    });
  }

  @Override
  public Future<Integer> read(ByteBuffer dst) {
    CompletableFutureHandler future = new CompletableFutureHandler();
    fileChannel.read(dst, 0, null, future);
    return future;
  }

  @Override
  public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
    fileChannel.write(src, 0, attachment, handler);
  }

  @Override
  public Future<Integer> write(ByteBuffer src) {
    return fileChannel.write(src, 0);
  }

  @Override
  public void close() throws IOException {
    fileChannel.close();
  }

  @Override
  public boolean isOpen() {
    return fileChannel.isOpen();
  }

  private static class CompletableFutureHandler extends CompletableFuture<Integer> implements CompletionHandler<Integer, Object> {

    @Override
    public void completed(Integer read, Object attachment) {
      complete(read > 0 ? read : -1);
    }

    @Override
    public void failed(Throwable exc, Object attachment) {
      if (exc instanceof AsynchronousCloseException) {
        complete(-1);
        return;
      }
      completeExceptionally(exc);
    }
  }
}
