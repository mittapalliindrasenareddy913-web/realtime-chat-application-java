import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebChatServer - Standalone Java HTTP & Real-time Chat Server
 * Runs on http://localhost:8080
 */
public class WebChatServer {

    private static final int PORT = 8080;
    private static final Path PUBLIC_DIR = Paths.get("public").toAbsolutePath();
    private static final Path DATA_FILE = Paths.get("data.json").toAbsolutePath();

    // In-memory thread-safe state (persisted to data.json)
    public static class User {
        public String username;
        public String passwordHash;
        public String email;
        public boolean isOnline;
        public long lastHeartbeat;

        public User(String username, String passwordHash, String email) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.email = email;
            this.isOnline = false;
            this.lastHeartbeat = System.currentTimeMillis();
        }
    }

    public static class ChatMessage {
        public int id;
        public String sender;
        public String targetType; // "private" or "group"
        public String target;     // target username or group id
        public String content;
        public String timestamp;

        public ChatMessage(int id, String sender, String targetType, String target, String content, String timestamp) {
            this.id = id;
            this.sender = sender;
            this.targetType = targetType;
            this.target = target;
            this.content = content;
            this.timestamp = timestamp;
        }
    }

    public static class Group {
        public String id;
        public String name;
        public String creator;

        public Group(String id, String name, String creator) {
            this.id = id;
            this.name = name;
            this.creator = creator;
        }
    }

    private static final Map<String, User> users = new ConcurrentHashMap<>();
    private static final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
    private static final List<Group> groups = new CopyOnWriteArrayList<>();
    private static final AtomicInteger messageIdCounter = new AtomicInteger(1);

    public static void main(String[] args) throws Exception {
        initDefaultData();
        loadDataFromFile();

        // Heartbeat cleanup thread to mark inactive users offline
        Thread heartbeatThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    long now = System.currentTimeMillis();
                    for (User u : users.values()) {
                        if (u.isOnline && (now - u.lastHeartbeat > 15000)) {
                            u.isOnline = false;
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API routes
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/logout", new LogoutHandler());
        server.createContext("/api/heartbeat", new HeartbeatHandler());
        server.createContext("/api/users", new UsersHandler());
        server.createContext("/api/groups", new GroupsHandler());
        server.createContext("/api/groups/create", new CreateGroupHandler());
        server.createContext("/api/messages/send", new SendMessageHandler());
        server.createContext("/api/messages/history", new MessageHistoryHandler());
        server.createContext("/api/messages/poll", new PollMessagesHandler());

        // Static file handler for frontend UI
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // default multithreaded executor
        server.start();

        System.out.println("=================================================");
        System.out.println("  Real-time Chat Application is RUNNING!");
        System.out.println("  Open in Google Chrome: http://localhost:" + PORT);
        System.out.println("=================================================");
    }

    private static void initDefaultData() {
        if (users.isEmpty()) {
            users.put("alice", new User("alice", hashPassword("password123"), "alice@gmail.com"));
            users.put("bob", new User("bob", hashPassword("password123"), "bob@gmail.com"));
            users.put("charlie", new User("charlie", hashPassword("password123"), "charlie@gmail.com"));
        }
        if (groups.isEmpty()) {
            groups.add(new Group("grp-1", "General Chat 🌐", "admin"));
            groups.add(new Group("grp-2", "Developers Hub 💻", "admin"));
        }
    }

    // --- Helper: SHA-256 Hashing ---
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return password;
        }
    }

    // --- Persistence ---
    private static synchronized void saveDataToFile() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"messages\": [\n");
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage m = messages.get(i);
                sb.append("    {\"id\": ").append(m.id)
                  .append(", \"sender\": \"").append(escapeJson(m.sender)).append("\"")
                  .append(", \"targetType\": \"").append(escapeJson(m.targetType)).append("\"")
                  .append(", \"target\": \"").append(escapeJson(m.target)).append("\"")
                  .append(", \"content\": \"").append(escapeJson(m.content)).append("\"")
                  .append(", \"timestamp\": \"").append(escapeJson(m.timestamp)).append("\"")
                  .append("}").append(i < messages.size() - 1 ? "," : "").append("\n");
            }
            sb.append("  ]\n}");
            Files.writeString(DATA_FILE, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private static synchronized void loadDataFromFile() {
        // Keeps previous data or starts fresh
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // --- Handlers ---
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            // Resolve file relative to public dir or jar
            Path filePath = PUBLIC_DIR.resolve(path.substring(1)).normalize();
            if (!filePath.startsWith(PUBLIC_DIR) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendResponse(ex, 404, "text/plain", "File Not Found");
                return;
            }

            String mimeType = "text/html";
            if (path.endsWith(".css")) mimeType = "text/css";
            else if (path.endsWith(".js")) mimeType = "application/javascript";
            else if (path.endsWith(".json")) mimeType = "application/json";
            else if (path.endsWith(".png")) mimeType = "image/png";

            byte[] bytes = Files.readAllBytes(filePath);
            ex.getResponseHeaders().set("Content-Type", mimeType + "; charset=UTF-8");
            ex.sendResponseHeaders(200, bytes.length);
            OutputStream os = ex.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendResponse(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> body = parseJson(readRequestBody(ex));
            String username = body.get("username");
            String password = body.get("password");
            String email = body.get("email");

            if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
                sendResponse(ex, 400, "application/json", "{\"success\":false,\"message\":\"Username and password are required\"}");
                return;
            }
            username = username.trim().toLowerCase();
            if (users.containsKey(username)) {
                sendResponse(ex, 400, "application/json", "{\"success\":false,\"message\":\"Username already taken!\"}");
                return;
            }

            User newUser = new User(username, hashPassword(password), email != null ? email.trim() : "");
            users.put(username, newUser);
            saveDataToFile();

            sendResponse(ex, 200, "application/json", "{\"success\":true,\"message\":\"Account created successfully! Please login.\"}");
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendResponse(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> body = parseJson(readRequestBody(ex));
            String username = body.get("username");
            String password = body.get("password");

            if (username == null || password == null) {
                sendResponse(ex, 400, "application/json", "{\"success\":false,\"message\":\"Invalid credentials\"}");
                return;
            }
            username = username.trim().toLowerCase();
            User u = users.get(username);
            if (u != null && u.passwordHash.equals(hashPassword(password))) {
                u.isOnline = true;
                u.lastHeartbeat = System.currentTimeMillis();
                sendResponse(ex, 200, "application/json", "{\"success\":true,\"username\":\"" + username + "\"}");
            } else {
                sendResponse(ex, 401, "application/json", "{\"success\":false,\"message\":\"Invalid username or password!\"}");
            }
        }
    }

    static class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String, String> body = parseJson(readRequestBody(ex));
            String username = body.get("username");
            if (username != null) {
                User u = users.get(username.toLowerCase());
                if (u != null) {
                    u.isOnline = false;
                }
            }
            sendResponse(ex, 200, "application/json", "{\"success\":true}");
        }
    }

    static class HeartbeatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String, String> body = parseJson(readRequestBody(ex));
            String username = body.get("username");
            if (username != null) {
                User u = users.get(username.toLowerCase());
                if (u != null) {
                    u.isOnline = true;
                    u.lastHeartbeat = System.currentTimeMillis();
                }
            }
            sendResponse(ex, 200, "application/json", "{\"status\":\"ok\"}");
        }
    }

    static class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (User u : users.values()) {
                if (count > 0) sb.append(",");
                sb.append("{\"username\":\"").append(escapeJson(u.username)).append("\"")
                  .append(",\"email\":\"").append(escapeJson(u.email)).append("\"")
                  .append(",\"isOnline\":").append(u.isOnline).append("}");
                count++;
            }
            sb.append("]");
            sendResponse(ex, 200, "application/json", sb.toString());
        }
    }

    static class GroupsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < groups.size(); i++) {
                Group g = groups.get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"id\":\"").append(escapeJson(g.id)).append("\"")
                  .append(",\"name\":\"").append(escapeJson(g.name)).append("\"")
                  .append(",\"creator\":\"").append(escapeJson(g.creator)).append("\"}");
            }
            sb.append("]");
            sendResponse(ex, 200, "application/json", sb.toString());
        }
    }

    static class CreateGroupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String, String> body = parseJson(readRequestBody(ex));
            String name = body.get("name");
            String creator = body.get("creator");
            if (name == null || name.trim().isEmpty()) {
                sendResponse(ex, 400, "application/json", "{\"success\":false,\"message\":\"Group name required\"}");
                return;
            }
            String id = "grp-" + (groups.size() + 1);
            Group g = new Group(id, name.trim(), creator != null ? creator : "anon");
            groups.add(g);
            sendResponse(ex, 200, "application/json", "{\"success\":true,\"id\":\"" + id + "\",\"name\":\"" + escapeJson(name) + "\"}");
        }
    }

    static class SendMessageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String, String> body = parseJson(readRequestBody(ex));
            String sender = body.get("sender");
            String targetType = body.get("targetType"); // "private" or "group"
            String target = body.get("target");
            String content = body.get("content");

            if (sender == null || target == null || content == null || content.trim().isEmpty()) {
                sendResponse(ex, 400, "application/json", "{\"success\":false,\"message\":\"Missing fields\"}");
                return;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
            int id = messageIdCounter.getAndIncrement();
            ChatMessage msg = new ChatMessage(id, sender, targetType != null ? targetType : "private", target, content.trim(), timestamp);
            messages.add(msg);
            saveDataToFile();

            sendResponse(ex, 200, "application/json", "{\"success\":true,\"id\":" + id + ",\"timestamp\":\"" + timestamp + "\"}");
        }
    }

    static class MessageHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String, String> query = parseQuery(ex.getRequestURI().getQuery());
            String currentUser = query.get("user");
            String targetType = query.get("targetType");
            String target = query.get("target");

            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (ChatMessage m : messages) {
                boolean match = false;
                if ("group".equalsIgnoreCase(targetType)) {
                    if ("group".equalsIgnoreCase(m.targetType) && m.target.equalsIgnoreCase(target)) {
                        match = true;
                    }
                } else {
                    // private chat between currentUser and target
                    if ("private".equalsIgnoreCase(m.targetType)) {
                        if ((m.sender.equalsIgnoreCase(currentUser) && m.target.equalsIgnoreCase(target)) ||
                            (m.sender.equalsIgnoreCase(target) && m.target.equalsIgnoreCase(currentUser))) {
                            match = true;
                        }
                    }
                }

                if (match) {
                    if (count > 0) sb.append(",");
                    sb.append("{\"id\":").append(m.id)
                      .append(",\"sender\":\"").append(escapeJson(m.sender)).append("\"")
                      .append(",\"targetType\":\"").append(escapeJson(m.targetType)).append("\"")
                      .append(",\"target\":\"").append(escapeJson(m.target)).append("\"")
                      .append(",\"content\":\"").append(escapeJson(m.content)).append("\"")
                      .append(",\"timestamp\":\"").append(escapeJson(m.timestamp)).append("\"}");
                    count++;
                }
            }
            sb.append("]");
            sendResponse(ex, 200, "application/json", sb.toString());
        }
    }

    static class PollMessagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String, String> query = parseQuery(ex.getRequestURI().getQuery());
            String currentUser = query.get("user");
            int lastId = 0;
            try {
                lastId = Integer.parseInt(query.getOrDefault("lastId", "0"));
            } catch (Exception ignored) {}

            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (ChatMessage m : messages) {
                if (m.id > lastId) {
                    // Include if group msg OR if private msg involving currentUser
                    boolean relevant = "group".equalsIgnoreCase(m.targetType) ||
                                       m.sender.equalsIgnoreCase(currentUser) ||
                                       m.target.equalsIgnoreCase(currentUser);
                    if (relevant) {
                        if (count > 0) sb.append(",");
                        sb.append("{\"id\":").append(m.id)
                          .append(",\"sender\":\"").append(escapeJson(m.sender)).append("\"")
                          .append(",\"targetType\":\"").append(escapeJson(m.targetType)).append("\"")
                          .append(",\"target\":\"").append(escapeJson(m.target)).append("\"")
                          .append(",\"content\":\"").append(escapeJson(m.content)).append("\"")
                          .append(",\"timestamp\":\"").append(escapeJson(m.timestamp)).append("\"}");
                        count++;
                    }
                }
            }
            sb.append("]");
            sendResponse(ex, 200, "application/json", sb.toString());
        }
    }

    // --- Helpers ---
    private static void sendResponse(HttpExchange ex, int status, String contentType, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String readRequestBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (kv.length == 2) {
                    String k = kv[0].trim().replace("\"", "");
                    String v = kv[1].trim().replace("\"", "");
                    map.put(k, v);
                }
            }
        }
        return map;
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                try {
                    result.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8),
                               URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
                } catch (Exception e) {
                    result.put(entry[0], entry[1]);
                }
            }
        }
        return result;
    }
}
