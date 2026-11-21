package com.chatapp.client.controller;

import com.chatapp.client.ChatClient;
import com.chatapp.client.Main;
import com.chatapp.common.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML private TextField loginUsernameField;
    @FXML private PasswordField loginPasswordField;
    @FXML private Button loginBtn;
    @FXML private Label loginErrorLabel;

    @FXML private TextField regUsernameField;
    @FXML private TextField regEmailField;
    @FXML private PasswordField regPasswordField;
    @FXML private PasswordField regConfirmPasswordField;
    @FXML private Button registerBtn;
    @FXML private Label registerErrorLabel;

    @FXML private Button loginTabBtn;
    @FXML private Button registerTabBtn;

    @FXML private javafx.scene.layout.VBox loginSection;
    @FXML private javafx.scene.layout.VBox registerSection;

    @FXML private ProgressIndicator loadingIndicator;

    private ChatClient chatClient;
    private boolean awaitingResponse = false;

    @FXML
    public void initializeUI() {
        showLoginTab();
        loadingIndicator.setVisible(false);
    }

    @FXML
    private void showLoginTab() {
        loginSection.setVisible(true);
        loginSection.setManaged(true);
        registerSection.setVisible(false);
        registerSection.setManaged(false);
        loginTabBtn.getStyleClass().add("tab-btn-active");
        loginTabBtn.getStyleClass().remove("tab-btn");
        registerTabBtn.getStyleClass().remove("tab-btn-active");
        registerTabBtn.getStyleClass().add("tab-btn");
        clearErrors();
    }

    @FXML
    private void showRegisterTab() {
        loginSection.setVisible(false);
        loginSection.setManaged(false);
        registerSection.setVisible(true);
        registerSection.setManaged(true);
        registerTabBtn.getStyleClass().add("tab-btn-active");
        registerTabBtn.getStyleClass().remove("tab-btn");
        loginTabBtn.getStyleClass().remove("tab-btn-active");
        loginTabBtn.getStyleClass().add("tab-btn");
        clearErrors();
    }

    @FXML
    private void handleLogin() {
        String username = loginUsernameField.getText().trim();
        String password = loginPasswordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showLoginError("Username and password cannot be empty.");
            return;
        }

        if (awaitingResponse) return;
        awaitingResponse = true;
        setLoading(true);

        new Thread(() -> {
            try {
                chatClient = new ChatClient();
                chatClient.connect();

                chatClient.setMessageHandler(msg -> Platform.runLater(() -> handleServerResponse(msg, username)));
                chatClient.startListening();
                chatClient.sendMessage(Message.login(username, password));

            } catch (IOException e) {
                Platform.runLater(() -> {
                    showLoginError("Could not connect to server. Is it running?");
                    setLoading(false);
                    awaitingResponse = false;
                });
            }
        }, "LoginThread").start();
    }

    @FXML
    private void handleRegister() {
        String username = regUsernameField.getText().trim();
        String email    = regEmailField.getText().trim();
        String password = regPasswordField.getText().trim();
        String confirm  = regConfirmPasswordField.getText().trim();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showRegisterError("All fields are required.");
            return;
        }
        if (!password.equals(confirm)) {
            showRegisterError("Passwords do not match.");
            return;
        }
        if (!email.contains("@")) {
            showRegisterError("Please enter a valid email address.");
            return;
        }
        if (password.length() < 6) {
            showRegisterError("Password must be at least 6 characters.");
            return;
        }

        if (awaitingResponse) return;
        awaitingResponse = true;
        setLoading(true);

        new Thread(() -> {
            try {
                chatClient = new ChatClient();
                chatClient.connect();

                chatClient.setMessageHandler(msg -> Platform.runLater(() -> handleServerResponse(msg, username)));
                chatClient.startListening();
                chatClient.sendMessage(Message.register(username, password, email));

            } catch (IOException e) {
                Platform.runLater(() -> {
                    showRegisterError("Could not connect to server. Is it running?");
                    setLoading(false);
                    awaitingResponse = false;
                });
            }
        }, "RegisterThread").start();
    }

    private void handleServerResponse(Message msg, String username) {
        setLoading(false);
        awaitingResponse = false;

        if (msg.getType() == Message.Type.SUCCESS) {
            chatClient.setLoggedInUsername(username);
            Stage stage = (Stage) loginBtn.getScene().getWindow();
            Main.showChatScreen(stage, username, chatClient);
        } else if (msg.getType() == Message.Type.ERROR) {
            String errContent = msg.getContent() != null ? msg.getContent() : "Unknown error.";
            if (loginSection.isVisible()) {
                showLoginError(errContent);
            } else {
                showRegisterError(errContent);
            }
            if (chatClient != null) {
                chatClient.disconnect();
                chatClient = null;
            }
        }
    }

    private void showLoginError(String message) {
        loginErrorLabel.setText(message);
        loginErrorLabel.setVisible(true);
    }

    private void showRegisterError(String message) {
        registerErrorLabel.setText(message);
        registerErrorLabel.setVisible(true);
    }

    private void clearErrors() {
        loginErrorLabel.setText("");
        loginErrorLabel.setVisible(false);
        registerErrorLabel.setText("");
        registerErrorLabel.setVisible(false);
    }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        loginBtn.setDisable(loading);
        registerBtn.setDisable(loading);
    }
}
