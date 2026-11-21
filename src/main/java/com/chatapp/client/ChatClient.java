package com.chatapp.client;

import com.chatapp.common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

public class ChatClient {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String loggedInUsername;
    private Consumer<Message> messageHandler;
    private volatile boolean running = false;

    public void setMessageHandler(Consumer<Message> handler) {
        this.messageHandler = handler;
    }

    public void connect() throws IOException {
        socket = new Socket(HOST, PORT);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        running = true;
    }

    public void sendMessage(Message msg) {
        try {
            if (out != null) {
                out.writeObject(msg);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            System.err.println("[ChatClient] Failed to send message: " + e.getMessage());
        }
    }

    public void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                while (running && socket != null && !socket.isClosed()) {
                    Object obj = in.readObject();
                    if (obj instanceof Message) {
                        Message msg = (Message) obj;
                        if (messageHandler != null) {
                            messageHandler.accept(msg);
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                if (running) {
                    System.err.println("[ChatClient] Connection lost: " + e.getMessage());
                }
            }
        }, "ChatClient-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void disconnect() {
        running = false;
        try {
            if (socket != null && !socket.isClosed() && loggedInUsername != null) {
                sendMessage(Message.request(Message.Type.LOGOUT));
            }
        } catch (Exception e) {
            System.err.println("[ChatClient] Error during disconnect: " + e.getMessage());
        } finally {
            closeResources();
        }
    }

    private void closeResources() {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {}
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public String getLoggedInUsername() {
        return loggedInUsername;
    }

    public void setLoggedInUsername(String loggedInUsername) {
        this.loggedInUsername = loggedInUsername;
    }
}
