package com.chatapp.server;

import com.chatapp.common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ClientHandler — One thread per connected client.
 * Reads Message objects from the client, processes them, and sends responses.
 */
public class ClientHandler implements Runnable {

    private final Socket       socket;
    private ObjectInputStream  in;
    private ObjectOutputStream out;
    private String             username;
    private int                userId = -1;
    private final ChatServer      server;
    private final DatabaseManager db = DatabaseManager.getInstance();

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Main loop
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            // ObjectOutputStream MUST be created before ObjectInputStream
            // to avoid deadlock on the other side.
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());

            Message message;
            while ((message = (Message) in.readObject()) != null) {
                handleMessage(message);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[ClientHandler] Connection closed"
                    + (username != null ? " for '" + username + "'" : "")
                    + ": " + e.getMessage());
        } finally {
            logout();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Dispatch
    // ─────────────────────────────────────────────────────────────────

    private void handleMessage(Message msg) {
        switch (msg.getType()) {

            // ── REGISTER ──────────────────────────────────────────
            case REGISTER: {
                String uname = msg.getSenderUsername();
                String pwd   = msg.getSenderPassword();   // raw password (server will hash)
                String email = msg.getSenderEmail();
                if (uname == null || pwd == null || email == null) {
                    sendMessage(Message.error("Missing registration fields."));
                    break;
                }
                boolean ok = db.registerUser(uname, pwd, email);
                if (ok) {
                    sendMessage(Message.success("Registration successful! You can now login."));
                } else {
                    sendMessage(Message.error("Registration failed. Username or email already exists."));
                }
                break;
            }

            // ── LOGIN ─────────────────────────────────────────────
            case LOGIN: {
                String uname = msg.getSenderUsername();
                String pwd   = msg.getSenderPassword();
                if (uname == null || pwd == null) {
                    sendMessage(Message.error("Missing login credentials."));
                    break;
                }
                int uid = db.authenticateUser(uname, pwd);
                if (uid != -1) {
                    this.username = uname;
                    this.userId   = uid;
                    db.setOnlineStatus(uid, true);
                    server.addOnlineUser(username, this);

                    Message successMsg = Message.success(username);
                    sendMessage(successMsg);
                    server.broadcastUserStatus();
                    System.out.println("[Server] User '" + username + "' logged in.");
                } else {
                    sendMessage(Message.error("Invalid username or password."));
                }
                break;
            }

            // ── LOGOUT ────────────────────────────────────────────
            case LOGOUT: {
                logout();
                break;
            }

            // ── PRIVATE MESSAGE ───────────────────────────────────
            case PRIVATE_MSG: {
                if (username == null) break;
                String targetName = msg.getTargetUsername();
                String content    = msg.getContent();
                String ts         = currentTimestamp();

                int targetId = db.getUserId(targetName);
                if (targetId == -1) {
                    sendMessage(Message.error("User '" + targetName + "' not found."));
                    break;
                }

                // Save to DB
                db.savePrivateMessage(userId, targetId, content, ts);

                // Forward to target if online
                ClientHandler targetHandler = server.getHandler(targetName);
                if (targetHandler != null) {
                    // Build a forwarded incoming message
                    Message forward = new Message(Message.Type.PRIVATE_MSG,
                            username, targetName, content);
                    forward.setTimestamp(ts);
                    targetHandler.sendMessage(forward);
                }
                break;
            }

            // ── GROUP MESSAGE ─────────────────────────────────────
            case GROUP_MSG: {
                if (username == null) break;
                int    groupId = msg.getGroupId();
                String content = msg.getContent();
                String ts      = currentTimestamp();

                if (!db.isGroupMember(groupId, userId)) {
                    sendMessage(Message.error("You are not a member of this group."));
                    break;
                }

                db.saveGroupMessage(groupId, userId, content, ts);

                // Broadcast to all online group members (excluding sender)
                Message broadcast = new Message(Message.Type.GROUP_MSG,
                        username, msg.getTargetGroup(), content);
                broadcast.setGroupId(groupId);
                broadcast.setTimestamp(ts);
                server.broadcastToGroupMembers(groupId, broadcast, username);
                break;
            }

            // ── GET ONLINE USERS ──────────────────────────────────
            case GET_ONLINE_USERS: {
                List<String> onlineUsers = db.getOnlineUsers();
                Message resp = new Message(Message.Type.USER_LIST);
                resp.setListData(onlineUsers);
                sendMessage(resp);
                break;
            }

            // ── GET ALL USERS ─────────────────────────────────────
            case GET_ALL_USERS: {
                List<String> allUsers = db.getAllUsers();
                Message resp = new Message(Message.Type.USER_LIST);
                resp.setListData(allUsers);
                sendMessage(resp);
                break;
            }

            // ── GET ALL GROUPS ────────────────────────────────────
            case GET_GROUPS: {
                List<String> allGroups = db.getAllGroups();
                Message resp = new Message(Message.Type.GROUP_LIST);
                resp.setListData(allGroups);
                sendMessage(resp);
                break;
            }

            // ── GET MY GROUPS ─────────────────────────────────────
            case GET_MY_GROUPS: {
                if (username == null) break;
                List<String> myGroups = db.getUserGroups(userId);
                Message resp = new Message(Message.Type.GROUP_LIST);
                resp.setListData(myGroups);
                sendMessage(resp);
                break;
            }

            // ── GET PRIVATE HISTORY ───────────────────────────────
            case GET_PRIVATE_HISTORY: {
                if (username == null) break;
                String targetName = msg.getTargetUsername();
                int targetId = db.getUserId(targetName);
                if (targetId == -1) {
                    sendMessage(Message.error("User '" + targetName + "' not found."));
                    break;
                }
                List<String> history = db.getPrivateHistory(userId, targetId);
                Message resp = new Message(Message.Type.MESSAGE_HISTORY);
                resp.setListData(history);
                sendMessage(resp);
                break;
            }

            // ── GET GROUP HISTORY ─────────────────────────────────
            case GET_GROUP_HISTORY: {
                if (username == null) break;
                int groupId = msg.getGroupId();
                List<String> history = db.getGroupHistory(groupId);
                Message resp = new Message(Message.Type.MESSAGE_HISTORY);
                resp.setListData(history);
                sendMessage(resp);
                break;
            }

            // ── CREATE GROUP ──────────────────────────────────────
            case CREATE_GROUP: {
                if (username == null) break;
                String groupName = msg.getContent();
                if (groupName == null || groupName.trim().isEmpty()) {
                    sendMessage(Message.error("Group name cannot be empty."));
                    break;
                }
                int groupId = db.createGroup(groupName.trim(), userId);
                if (groupId != -1) {
                    db.addGroupMember(groupId, userId);
                    Message resp = Message.success("Group '" + groupName + "' created successfully!");
                    resp.setGroupId(groupId);
                    sendMessage(resp);
                } else {
                    sendMessage(Message.error("Failed to create group '" + groupName + "'."));
                }
                break;
            }

            // ── ADD MEMBER ────────────────────────────────────────
            case ADD_MEMBER: {
                if (username == null) break;
                int    groupId     = msg.getGroupId();
                String memberName  = msg.getTargetUsername();
                int    memberId    = db.getUserId(memberName);
                if (memberId == -1) {
                    sendMessage(Message.error("User '" + memberName + "' not found."));
                    break;
                }
                boolean added = db.addGroupMember(groupId, memberId);
                if (added) {
                    sendMessage(Message.success("User '" + memberName + "' added to group."));
                } else {
                    sendMessage(Message.error("Could not add '" + memberName + "' to group."));
                }
                break;
            }

            default:
                System.out.println("[ClientHandler] Unknown message type: " + msg.getType());
                break;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Send (thread-safe)
    // ─────────────────────────────────────────────────────────────────

    public synchronized void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // prevent ObjectOutputStream from caching references
        } catch (IOException e) {
            System.err.println("[ClientHandler] sendMessage error"
                    + (username != null ? " for '" + username + "'" : "")
                    + ": " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Logout & cleanup
    // ─────────────────────────────────────────────────────────────────

    public void logout() {
        if (username != null) {
            db.setOnlineStatus(userId, false);
            server.removeOnlineUser(username);
            server.broadcastUserStatus();
            System.out.println("[ClientHandler] User '" + username + "' logged out.");
            username = null;
            userId   = -1;
        }
        closeConnection();
    }

    private void closeConnection() {
        try { if (in     != null) in.close();                    } catch (IOException ignored) {}
        try { if (out    != null) out.close();                   } catch (IOException ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────

    private String currentTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FMT);
    }
}
