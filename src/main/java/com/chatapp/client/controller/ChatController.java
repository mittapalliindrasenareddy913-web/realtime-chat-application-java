package com.chatapp.client.controller;

import com.chatapp.client.ChatClient;
import com.chatapp.common.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatController {

    // ── Left sidebar ───────────────────────────────────────────────────
    @FXML private ListView<String> onlineUsersListView;
    @FXML private ListView<String> groupsListView;
    @FXML private Button           createGroupBtn;
    @FXML private Label            currentUserLabel;    // fx:id in chat.fxml

    // ── Centre chat panel ──────────────────────────────────────────────
    @FXML private Label      chatTargetLabel;
    @FXML private Label      statusLabel;
    @FXML private Label      onlineIndicator;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox       messagesBox;
    @FXML private VBox       placeholderBox;
    @FXML private TextField  messageInput;
    @FXML private Button     sendBtn;

    // ── Injected by Main ──────────────────────────────────────────────
    private ChatClient chatClient;
    private String currentUsername;

    // ── State ─────────────────────────────────────────────────────────
    private String  currentChatTarget = null;   // username for DM, group name for group
    private boolean isGroupChat       = false;
    private int     currentGroupId    = -1;

    private final Set<String>         onlineUsers   = new LinkedHashSet<>();
    private final Map<String, Integer> groupNameToId = new LinkedHashMap<>(); // groupName → groupId (int)

    // Message cache: key = "dm:<user>" or "grp:<groupId>"
    private final Map<String, List<MessageBubble>> messageCache = new HashMap<>();

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    // ─────────────────────────────────────────────────────────────────
    //  Setters (called by Main.showChatScreen before initialize)
    // ─────────────────────────────────────────────────────────────────

    public void setChatClient(ChatClient client) {
        this.chatClient = client;
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    // ─────────────────────────────────────────────────────────────────
    //  initialize  (called manually by Main after setters)
    // ─────────────────────────────────────────────────────────────────

    public void initialize() {
        if (currentUserLabel != null) {
            currentUserLabel.setText("👤 " + currentUsername);
        }

        setupMessageHandler();
        setupUIListeners();
        requestOnlineUsers();
        requestMyGroups();
    }

    // ─────────────────────────────────────────────────────────────────
    //  Message handler (runs on background thread → dispatches to FX)
    // ─────────────────────────────────────────────────────────────────

    private void setupMessageHandler() {
        chatClient.setMessageHandler(msg -> Platform.runLater(() -> handleIncoming(msg)));
    }

    private void handleIncoming(Message msg) {
        switch (msg.getType()) {

            case USER_LIST:
                updateUserList(msg.getListData());
                break;

            case GROUP_LIST:
                updateGroupList(msg.getListData());
                break;

            case MESSAGE_HISTORY:
                displayHistory(msg);
                break;

            case PRIVATE_MSG:
            case INCOMING_PRIVATE_MSG:
                handleIncomingPrivateMsg(msg);
                break;

            case GROUP_MSG:
            case INCOMING_GROUP_MSG:
                handleIncomingGroupMsg(msg);
                break;

            case USER_STATUS_UPDATE:
                requestOnlineUsers();
                break;

            case SUCCESS:
                // After group created → refresh group list
                String cnt = msg.getContent();
                if (cnt != null && (cnt.startsWith("Group") || cnt.contains("created"))) {
                    requestMyGroups();
                }
                break;

            case ERROR:
                showAlert("Server Error", msg.getContent());
                break;

            default:
                break;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  UI listeners
    // ─────────────────────────────────────────────────────────────────

    private void setupUIListeners() {
        // Auto-scroll to bottom when new messages added
        messagesBox.heightProperty().addListener((obs, oldVal, newVal) ->
                chatScrollPane.setVvalue(1.0));

        // Enable send button only when there is text
        messageInput.textProperty().addListener((obs, oldText, newText) ->
                sendBtn.setDisable(newText.trim().isEmpty() || currentChatTarget == null));
    }

    // ─────────────────────────────────────────────────────────────────
    //  FXML event handlers (match onAction="#..." in chat.fxml)
    // ─────────────────────────────────────────────────────────────────

    @FXML
    private void handleUserClick() {
        String selected = onlineUsersListView.getSelectionModel().getSelectedItem();
        if (selected != null && !selected.equals(currentUsername)) {
            openPrivateChat(selected);
            groupsListView.getSelectionModel().clearSelection();
        }
    }

    @FXML
    private void handleGroupClick() {
        String selected = groupsListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Integer groupId = groupNameToId.get(selected);
            if (groupId != null) {
                openGroupChat(selected, groupId);
                onlineUsersListView.getSelectionModel().clearSelection();
            }
        }
    }

    @FXML
    private void handleSendMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty() || currentChatTarget == null) return;

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Message msg;
        if (isGroupChat) {
            msg = Message.groupMsg(currentUsername, currentGroupId, currentChatTarget, text, ts);
        } else {
            msg = Message.privateMsg(currentUsername, currentChatTarget, text, ts);
        }

        chatClient.sendMessage(msg);
        appendMessage(currentUsername, text, ts, true);
        messageInput.clear();
        sendBtn.setDisable(true);
    }

    @FXML
    private void handleCreateGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Group");
        dialog.setHeaderText("Enter a name for the new group:");
        dialog.setContentText("Group name:");
        dialog.showAndWait().ifPresent(name -> {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                Message msg = new Message();
                msg.setType(Message.Type.CREATE_GROUP);
                msg.setSenderUsername(currentUsername);
                msg.setContent(trimmed);
                chatClient.sendMessage(msg);
            }
        });
    }

    @FXML
    private void handleLogout() {
        chatClient.disconnect();
        // Return to login screen
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) sendBtn.getScene().getWindow();
            com.chatapp.client.Main main = new com.chatapp.client.Main();
            main.start(stage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Open chats
    // ─────────────────────────────────────────────────────────────────

    private void openPrivateChat(String username) {
        currentChatTarget = username;
        isGroupChat       = false;
        currentGroupId    = -1;
        chatTargetLabel.setText("💬 " + username);
        boolean online = onlineUsers.contains(username);
        statusLabel.setText(online ? "🟢 Online" : "⚫ Offline");
        if (onlineIndicator != null) {
            onlineIndicator.setVisible(true);
            onlineIndicator.setManaged(true);
            onlineIndicator.setText(online ? "● Online" : "● Offline");
        }

        // Update send button state
        sendBtn.setDisable(messageInput.getText().trim().isEmpty());

        // Show messages
        if (placeholderBox != null) {
            placeholderBox.setVisible(false);
            placeholderBox.setManaged(false);
        }
        messagesBox.getChildren().removeIf(n -> n != placeholderBox);

        String cacheKey = "dm:" + username;
        List<MessageBubble> cached = messageCache.get(cacheKey);
        if (cached != null) {
            cached.forEach(b -> messagesBox.getChildren().add(b.node));
        } else {
            requestPrivateHistory(username);
        }
    }

    private void openGroupChat(String groupName, int groupId) {
        currentChatTarget = groupName;
        isGroupChat       = true;
        currentGroupId    = groupId;
        chatTargetLabel.setText("👥 " + groupName);
        statusLabel.setText("Group Chat");
        if (onlineIndicator != null) {
            onlineIndicator.setVisible(false);
            onlineIndicator.setManaged(false);
        }

        sendBtn.setDisable(messageInput.getText().trim().isEmpty());

        if (placeholderBox != null) {
            placeholderBox.setVisible(false);
            placeholderBox.setManaged(false);
        }
        messagesBox.getChildren().removeIf(n -> n != placeholderBox);

        String cacheKey = "grp:" + groupId;
        List<MessageBubble> cached = messageCache.get(cacheKey);
        if (cached != null) {
            cached.forEach(b -> messagesBox.getChildren().add(b.node));
        } else {
            requestGroupHistory(groupId);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Server requests
    // ─────────────────────────────────────────────────────────────────

    private void requestOnlineUsers() {
        Message msg = Message.request(Message.Type.GET_ONLINE_USERS);
        msg.setSenderUsername(currentUsername);
        chatClient.sendMessage(msg);
    }

    private void requestMyGroups() {
        Message msg = Message.request(Message.Type.GET_MY_GROUPS);
        msg.setSenderUsername(currentUsername);
        chatClient.sendMessage(msg);
    }

    private void requestPrivateHistory(String targetUser) {
        Message msg = Message.request(Message.Type.GET_PRIVATE_HISTORY);
        msg.setSenderUsername(currentUsername);
        msg.setTargetUsername(targetUser);
        chatClient.sendMessage(msg);
    }

    private void requestGroupHistory(int groupId) {
        Message msg = Message.request(Message.Type.GET_GROUP_HISTORY);
        msg.setSenderUsername(currentUsername);
        msg.setTargetGroupId(groupId);
        chatClient.sendMessage(msg);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Update lists
    // ─────────────────────────────────────────────────────────────────

    private void updateUserList(List<String> users) {
        onlineUsers.clear();
        if (users != null) onlineUsers.addAll(users);

        // Filter out self from display
        List<String> others = users != null
                ? users.stream().filter(u -> !u.equals(currentUsername)).toList()
                : Collections.emptyList();
        onlineUsersListView.getItems().setAll(others);

        // Update status indicator if currently in DM
        if (!isGroupChat && currentChatTarget != null) {
            boolean online = onlineUsers.contains(currentChatTarget);
            statusLabel.setText(online ? "🟢 Online" : "⚫ Offline");
        }
    }

    // listData format: "groupId|groupName" per entry (matches DatabaseManager output)
    private void updateGroupList(List<String> groups) {
        groupNameToId.clear();
        List<String> names = new ArrayList<>();
        if (groups != null) {
            for (String entry : groups) {
                // Try "|" separator first (from DatabaseManager), then ":"
                String[] parts = entry.contains("|") ? entry.split("\\|", 2) : entry.split(":", 2);
                if (parts.length == 2) {
                    try {
                        int gid = Integer.parseInt(parts[0].trim());
                        groupNameToId.put(parts[1].trim(), gid);
                        names.add(parts[1].trim());
                    } catch (NumberFormatException e) {
                        groupNameToId.put(entry, -1);
                        names.add(entry);
                    }
                } else {
                    groupNameToId.put(entry, -1);
                    names.add(entry);
                }
            }
        }
        groupsListView.getItems().setAll(names);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Display message history
    // ─────────────────────────────────────────────────────────────────

    // History entries format: "timestamp|senderUsername|message" (from DatabaseManager)
    private void displayHistory(Message msg) {
        List<String> history = msg.getListData();
        if (history == null) return;

        // Clear existing messages (keep placeholder hidden)
        messagesBox.getChildren().removeIf(n -> n != placeholderBox);
        List<MessageBubble> bubbles = new ArrayList<>();

        for (String entry : history) {
            String[] parts = entry.split("\\|", 3);
            if (parts.length < 2) continue;
            String ts      = parts[0];
            String sender  = parts[1];
            String content = parts.length == 3 ? parts[2] : "";
            boolean isMine = sender.equals(currentUsername);

            MessageBubble bubble = createBubble(sender, content, ts, isMine);
            bubbles.add(bubble);
            messagesBox.getChildren().add(bubble.node);
        }

        // Cache
        if (!isGroupChat && currentChatTarget != null) {
            messageCache.put("dm:" + currentChatTarget, bubbles);
        } else if (isGroupChat && currentGroupId != -1) {
            messageCache.put("grp:" + currentGroupId, bubbles);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Real-time incoming messages
    // ─────────────────────────────────────────────────────────────────

    private void handleIncomingPrivateMsg(Message msg) {
        String sender   = msg.getSenderUsername();
        String content  = msg.getContent();
        String ts       = msg.getTimestamp() != null ? msg.getTimestamp() : "";

        // Don't show our own sent messages (server echoes back)
        if (currentUsername.equals(sender)) return;

        String cacheKey = "dm:" + sender;
        MessageBubble bubble = createBubble(sender, content, ts, false);
        messageCache.computeIfAbsent(cacheKey, k -> new ArrayList<>()).add(bubble);

        if (!isGroupChat && sender.equals(currentChatTarget)) {
            messagesBox.getChildren().add(bubble.node);
        } else {
            showUnreadHint(sender);
        }
    }

    private void handleIncomingGroupMsg(Message msg) {
        String sender   = msg.getSenderUsername();
        int    groupId  = msg.getGroupId();
        String groupName = msg.getTargetGroup();
        String content  = msg.getContent();
        String ts       = msg.getTimestamp() != null ? msg.getTimestamp() : "";
        String cacheKey = "grp:" + groupId;

        boolean isMine = sender.equals(currentUsername);
        MessageBubble bubble = createBubble(sender, content, ts, isMine);
        messageCache.computeIfAbsent(cacheKey, k -> new ArrayList<>()).add(bubble);

        if (isGroupChat && groupId == currentGroupId) {
            messagesBox.getChildren().add(bubble.node);
        } else {
            showUnreadHint(groupName != null ? groupName : String.valueOf(groupId));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Message bubble helpers
    // ─────────────────────────────────────────────────────────────────

    private void appendMessage(String sender, String content, String ts, boolean isMine) {
        MessageBubble bubble = createBubble(sender, content, ts, isMine);
        messagesBox.getChildren().add(bubble.node);

        String cacheKey = isGroupChat ? "grp:" + currentGroupId : "dm:" + currentChatTarget;
        messageCache.computeIfAbsent(cacheKey, k -> new ArrayList<>()).add(bubble);
    }

    private MessageBubble createBubble(String sender, String content, String ts, boolean isMine) {
        // Shorten timestamp to HH:mm
        String timeDisplay = "";
        if (ts != null && !ts.isEmpty()) {
            try {
                timeDisplay = ts.length() >= 16 ? ts.substring(11, 16) : ts;
            } catch (Exception ignored) {
                timeDisplay = ts;
            }
        }

        // Sender name label (shown only on received messages)
        Label senderLbl = new Label(isMine ? "" : sender);
        senderLbl.setStyle("-fx-font-size:10px; -fx-text-fill:#9e9e9e; -fx-padding:0 0 2 0;");
        senderLbl.setVisible(!isMine);
        senderLbl.setManaged(!isMine);

        // Message text
        Text contentText = new Text(content != null ? content : "");
        contentText.setWrappingWidth(300);
        TextFlow flow = new TextFlow(contentText);
        flow.setPadding(new Insets(8, 12, 8, 12));
        flow.setMaxWidth(360);

        // Timestamp label
        Label timeLbl = new Label(timeDisplay);
        timeLbl.setStyle("-fx-font-size:9px; -fx-text-fill:#bdbdbd; -fx-padding:2 4 0 0;");

        VBox bubble = new VBox(2, senderLbl, flow, timeLbl);
        bubble.setMaxWidth(380);
        bubble.setPadding(new Insets(2));

        if (isMine) {
            flow.getStyleClass().add("message-sent");
            contentText.setStyle("-fx-fill: white;");
            timeLbl.setAlignment(Pos.CENTER_RIGHT);
            bubble.setAlignment(Pos.CENTER_RIGHT);
        } else {
            flow.getStyleClass().add("message-received");
            contentText.setStyle("-fx-fill: #e0e0e0;");
            timeLbl.setAlignment(Pos.CENTER_LEFT);
            bubble.setAlignment(Pos.CENTER_LEFT);
        }

        HBox row = new HBox(bubble);
        row.setPadding(new Insets(3, 10, 3, 10));
        row.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        return new MessageBubble(row);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────

    private void showUnreadHint(String from) {
        System.out.println("[ChatApp] New message from: " + from);
        // A full implementation would show a badge/notification
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "An unknown error occurred.");
        alert.showAndWait();
    }

    // ─────────────────────────────────────────────────────────────────
    //  Inner class
    // ─────────────────────────────────────────────────────────────────

    private static class MessageBubble {
        final javafx.scene.Node node;
        MessageBubble(javafx.scene.Node node) { this.node = node; }
    }
}
