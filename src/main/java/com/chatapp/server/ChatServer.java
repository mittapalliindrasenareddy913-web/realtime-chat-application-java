package com.chatapp.server;

import com.chatapp.common.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    private static final int PORT = 5000;
    private final ConcurrentHashMap<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private final DatabaseManager db = DatabaseManager.getInstance();

    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.start();
    }

    public void start() {
        System.out.println("[ChatServer] Server starting on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[ChatServer] Server started successfully. Waiting for connections...");
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[ChatServer] New connection from: " + clientSocket.getInetAddress());
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    Thread clientThread = new Thread(handler);
                    clientThread.setDaemon(true);
                    clientThread.start();
                } catch (IOException e) {
                    System.err.println("[ChatServer] Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[ChatServer] Could not start server on port " + PORT + ": " + e.getMessage());
        }
    }

    public void addOnlineUser(String username, ClientHandler handler) {
        onlineUsers.put(username, handler);
        System.out.println("[ChatServer] User '" + username + "' is now online. Total online: " + onlineUsers.size());
    }

    public void removeOnlineUser(String username) {
        onlineUsers.remove(username);
        System.out.println("[ChatServer] User '" + username + "' went offline. Total online: " + onlineUsers.size());
    }

    public ClientHandler getHandler(String username) {
        return onlineUsers.get(username);
    }

    public void broadcastUserStatus() {
        List<String> onlineList = new ArrayList<>(onlineUsers.keySet());
        Message statusMsg = new Message(Message.Type.USER_STATUS_UPDATE);
        statusMsg.setListData(onlineList);

        for (ClientHandler handler : onlineUsers.values()) {
            try {
                handler.sendMessage(statusMsg);
            } catch (Exception e) {
                System.err.println("[ChatServer] broadcastUserStatus error for a handler: " + e.getMessage());
            }
        }
    }

    public void broadcastToGroupMembers(int groupId, Message msg, String excludeUsername) {
        List<Integer> memberIds = db.getGroupMemberIds(groupId);
        for (int memberId : memberIds) {
            String memberUsername = db.getUsernameById(memberId);
            if (memberUsername == null) continue;
            if (memberUsername.equals(excludeUsername)) continue;
            ClientHandler handler = onlineUsers.get(memberUsername);
            if (handler != null) {
                try {
                    handler.sendMessage(msg);
                } catch (Exception e) {
                    System.err.println("[ChatServer] broadcastToGroupMembers error for user '"
                            + memberUsername + "': " + e.getMessage());
                }
            }
        }
    }
}
