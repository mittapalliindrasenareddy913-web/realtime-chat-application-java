package com.chatapp.client;

import com.chatapp.client.controller.ChatController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("ChatApp - Real Time Messenger");
        showLoginScreen(primaryStage);
    }

    private void showLoginScreen(Stage stage) throws IOException {
        URL fxmlUrl = getClass().getResource("/com/chatapp/client/view/login.fxml");
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        Scene scene = new Scene(root, 450, 600);
        URL cssUrl = getClass().getResource("/com/chatapp/client/css/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void showChatScreen(Stage stage, String username, ChatClient client) {
        try {
            URL fxmlUrl = Main.class.getResource("/com/chatapp/client/view/chat.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            ChatController controller = loader.getController();
            controller.setCurrentUsername(username);
            controller.setChatClient(client);
            controller.initialize();

            Scene scene = new Scene(root, 1000, 650);
            URL cssUrl = Main.class.getResource("/com/chatapp/client/css/style.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            stage.setTitle("ChatApp - Real Time Messenger  |  " + username);
            stage.setScene(scene);
            stage.setResizable(true);
            stage.setMinWidth(800);
            stage.setMinHeight(550);

            stage.setOnCloseRequest(e -> client.disconnect());

            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[Main] Failed to load chat screen: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
