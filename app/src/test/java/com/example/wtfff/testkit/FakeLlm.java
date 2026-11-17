package com.example.wtfff.testkit;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * A scripted OpenAI-compatible endpoint on localhost. Queue replies, point the app at it
 * through the same saved settings the BYOK sheet writes, then read back exactly what the
 * app sent.
 */
public final class FakeLlm implements Closeable {

    private static final class Reply {
        final int status;
        final String body;
        final CountDownLatch gate;

        Reply(int status, String body, CountDownLatch gate) {
            this.status = status;
            this.body = body;
            this.gate = gate;
        }
    }

    private final ServerSocket server;
    private final List<String> requests = Collections.synchronizedList(new ArrayList<String>());
    private final List<String> authHeaders = Collections.synchronizedList(new ArrayList<String>());
    private final LinkedBlockingDeque<Reply> replies = new LinkedBlockingDeque<>();
    private volatile boolean closed;

    public FakeLlm() {
        this(0);
    }

    /** Binds a fixed port (0 = any free port). A fixed port keeps the URL in screenshots stable. */
    public FakeLlm(int port) {
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 50);
        } catch (IOException e) {
            throw new AssertionError("Cannot start FakeLlm on port " + port, e);
        }
        Thread acceptor = new Thread(() -> {
            while (!closed) {
                try {
                    final Socket socket = server.accept();
                    Thread worker = new Thread(() -> serve(socket), "FakeLlm-worker");
                    worker.setDaemon(true);
                    worker.start();
                } catch (IOException e) {
                    return;
                }
            }
        }, "FakeLlm-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void serve(Socket socket) {
        try (Socket s = socket) {
            InputStream in = s.getInputStream();
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            int contentLength = 0;
            boolean chunked = false;
            String auth = "";
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } else if (lower.startsWith("transfer-encoding:") && lower.contains("chunked")) {
                    chunked = true;
                } else if (lower.startsWith("authorization:")) {
                    auth = line.substring(14).trim();
                }
            }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            if (chunked) {
                while (true) {
                    String sizeLine = readLine(in);
                    if (sizeLine == null) break;
                    int semi = sizeLine.indexOf(';');
                    int size = Integer.parseInt((semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim(), 16);
                    if (size == 0) {
                        readLine(in);
                        break;
                    }
                    readFully(in, body, size);
                    readLine(in);
                }
            } else {
                readFully(in, body, contentLength);
            }

            Reply reply = replies.poll();
            authHeaders.add(auth);
            requests.add(new String(body.toByteArray(), StandardCharsets.UTF_8));
            if (reply == null) {
                reply = new Reply(500, "{\"error\":{\"message\":\"FakeLlm: no reply queued\"}}", null);
            }
            if (reply.gate != null) {
                try {
                    reply.gate.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] out = reply.body.getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + reply.status + (reply.status == 200 ? " OK" : " Error") + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + out.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream os = s.getOutputStream();
            os.write(head.getBytes(StandardCharsets.US_ASCII));
            os.write(out);
            os.flush();
        } catch (IOException ignored) {
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        boolean any = false;
        while ((b = in.read()) != -1) {
            any = true;
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
        }
        if (!any) return null;
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void readFully(InputStream in, ByteArrayOutputStream into, int count) throws IOException {
        byte[] chunk = new byte[4096];
        int left = count;
        while (left > 0) {
            int n = in.read(chunk, 0, Math.min(chunk.length, left));
            if (n < 0) break;
            into.write(chunk, 0, n);
            left -= n;
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getLocalPort() + "/v1";
    }

    /** Saves this endpoint as the app's CUSTOM provider, exactly as the BYOK sheet stores it. */
    public FakeLlm install(Context context) {
        context.getSharedPreferences("neural_deck_ai_config", Context.MODE_PRIVATE).edit()
                .putString("provider_id", "custom")
                .putString("base_url_custom", baseUrl())
                .putString("model_custom", "fake-model")
                .commit();
        return this;
    }

    // ---- scripting replies ------------------------------------------------------------

    /** Queues a normal chat completion whose message content is the given text. */
    public void replyContent(String content) {
        replies.add(new Reply(200, completion(content), null));
    }

    /** Like replyContent, but the reply is held until the returned latch is counted down. */
    public CountDownLatch replyContentWhenReleased(String content) {
        CountDownLatch gate = new CountDownLatch(1);
        replies.add(new Reply(200, completion(content), gate));
        return gate;
    }

    /** Queues a reply holding well-formed idea cards with these titles. */
    public void replyIdeas(String... titles) {
        replyContent(ideasJson(titles));
    }

    public CountDownLatch replyIdeasWhenReleased(String... titles) {
        return replyContentWhenReleased(ideasJson(titles));
    }

    public void replyHttpError(int status, String message) {
        try {
            JSONObject err = new JSONObject().put("error", new JSONObject().put("message", message));
            replies.add(new Reply(status, err.toString(), null));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static String ideasJson(String... titles) {
        try {
            JSONArray ideas = new JSONArray();
            for (String title : titles) {
                ideas.put(new JSONObject()
                        .put("title", title)
                        .put("summary", "Summary of " + title + ".")
                        .put("rationale", "Why " + title + ".")
                        .put("tags", new JSONArray().put("tag " + title.toLowerCase(Locale.US))));
            }
            return new JSONObject().put("ideas", ideas).toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String completion(String content) {
        try {
            JSONObject message = new JSONObject().put("role", "assistant").put("content", content);
            JSONArray choices = new JSONArray().put(new JSONObject().put("index", 0).put("message", message));
            return new JSONObject().put("choices", choices).toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ---- reading what the app sent -----------------------------------------------------

    public int requestCount() {
        return requests.size();
    }

    public JSONObject request(int index) {
        try {
            return new JSONObject(requests.get(index));
        } catch (Exception e) {
            throw new AssertionError("Request " + index + " is not JSON: " + requests, e);
        }
    }

    public String systemPrompt(int index) {
        return message(index, 0);
    }

    public String userPrompt(int index) {
        return message(index, 1);
    }

    public double temperature(int index) {
        return request(index).optDouble("temperature");
    }

    public String model(int index) {
        return request(index).optString("model");
    }

    /** The Authorization header of a request, or "" if none was sent. */
    public String authorization(int index) {
        return authHeaders.get(index);
    }

    private String message(int index, int which) {
        try {
            return request(index).getJSONArray("messages").getJSONObject(which).getString("content");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            server.close();
        } catch (IOException ignored) {
        }
    }
}
