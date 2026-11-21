package com.chatapp.server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static DatabaseManager instance;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";

    private DatabaseManager() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("[DatabaseManager] MySQL JDBC Driver loaded successfully.");
        } catch (ClassNotFoundException e) {
            System.err.println("[DatabaseManager] MySQL JDBC Driver not found: " + e.getMessage());
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // -------------------------------------------------------------------------
    // Password hashing
    // -------------------------------------------------------------------------

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("[DatabaseManager] SHA-256 algorithm not found: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // User operations
    // -------------------------------------------------------------------------

    public boolean registerUser(String username, String password, String email) {
        String sql = "INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String hashedPassword = hashPassword(password);
            if (hashedPassword == null) return false;
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);
            stmt.setString(3, email);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] registerUser error: " + e.getMessage());
            return false;
        }
    }

    public int authenticateUser(String username, String password) {
        String sql = "SELECT user_id FROM users WHERE username = ? AND password_hash = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String hashedPassword = hashPassword(password);
            if (hashedPassword == null) return -1;
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("user_id");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] authenticateUser error: " + e.getMessage());
        }
        return -1;
    }

    public int getUserId(String username) {
        String sql = "SELECT user_id FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("user_id");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] getUserId error: " + e.getMessage());
        }
        return -1;
    }

    public void setOnlineStatus(int userId, boolean online) {
        String sql = "UPDATE users SET is_online = ? WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, online);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] setOnlineStatus error: " + e.getMessage());
        }
    }

    public List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM users ORDER BY username";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] getAllUsers error: " + e.getMessage());
        }
        return users;
    }

    public List<String> getOnlineUsers() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM users WHERE is_online = TRUE ORDER BY username";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] getOnlineUsers error: " + e.getMessage());
        }
        return users;
    }

    // -------------------------------------------------------------------------
    // Private messaging
    // -------------------------------------------------------------------------

    public void savePrivateMessage(int senderId, int receiverId, String message, String timestamp) {
        String sql = "INSERT INTO private_messages (sender_id, receiver_id, message, sent_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);
            stmt.setString(3, message);
            stmt.setString(4, timestamp);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] savePrivateMessage error: " + e.getMessage());
        }
    }

    public List<String> getPrivateHistory(int userId1, int userId2) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT pm.sent_at, u.username, pm.message " +
                     "FROM private_messages pm " +
                     "JOIN users u ON pm.sender_id = u.user_id " +
                     "WHERE (pm.sender_id = ? AND pm.receiver_id = ?) " +
                     "   OR (pm.sender_id = ? AND pm.receiver_id = ?) " +
                     "ORDER BY pm.sent_at ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId1);
            stmt.setInt(2, userId2);
            stmt.setInt(3, userId2);
            stmt.setInt(4, userId1);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String entry = rs.getString("sent_at") + "|" +
                                   rs.getString("username") + "|" +
                                   rs.getString("message");
                    history.add(entry);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] getPrivateHistory error: " + e.getMessage());
        }
        return history;
    }

    // -------------------------------------------------------------------------
    // Group operations
    // -------------------------------------------------------------------------

    public int createGroup(String groupName, int creatorId) {
        String sql = "INSERT INTO groups (group_name, creator_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, groupName);
            stmt.setInt(2, creatorId);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] createGroup error: " + e.getMessage());
        }
        return -1;
    }

    public boolean addGroupMember(int groupId, int userId) {
        String sql = "INSERT IGNORE INTO group_members (group_id, user_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            stmt.setInt(2, userId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] addGroupMember error: " + e.getMessage());
            return false;
        }
    }

    public List<String> getAllGroups() {
        List<String> groups = new ArrayList<>();
        String sql = "SELECT group_id, group_name FROM groups ORDER BY group_name";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                groups.add(rs.getInt("group_id") + "|" + rs.getString("group_name"));
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] getAllGroups error: " + e.getMessage());
        }
        return groups;
    }

    public List<String> getUserGroups(int userId) {
        List<String> groups = new ArrayList<>();
        String sql = "SELECT g.group_id, g.group_name " +
                     "FROM groups g " +
                     "JOIN group_members gm ON g.group_id = gm.group_id " +
                     "WHERE gm.user_id = ? " +
                     "ORDER BY g.group_name";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    groups.add(rs.getInt("group_id") + "|" + rs.getString("group_name"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] getUserGroups error: " + e.getMessage());
        }
        return groups;
    }

    public void saveGroupMessage(int groupId, int senderId, String message, String timestamp) {
        String sql = "INSERT INTO group_messages (group_id, sender_id, message, sent_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            stmt.setInt(2, senderId);
            stmt.setString(3, message);
            stmt.setString(4, timestamp);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] saveGroupMessage error: " + e.getMessage());
        }
    }

    public List<String> getGroupHistory(int groupId) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT gm.sent_at, u.username, gm.message " +
                     "FROM group_messages gm " +
                     "JOIN users u ON gm.sender_id = u.user_id " +
                     "WHERE gm.group_id = ? " +
                     "ORDER BY gm.sent_at ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String entry = rs.getString("sent_at") + "|" +
                                   rs.getString("username") + "|" +
                                   rs.getString("message");
                    history.add(entry);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] getGroupHistory error: " + e.getMessage());
        }
        return history;
    }

    public boolean isGroupMember(int groupId, int userId) {
        String sql = "SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] isGroupMember error: " + e.getMessage());
        }
        return false;
    }

    public List<Integer> getGroupMemberIds(int groupId) {
        List<Integer> memberIds = new ArrayList<>();
        String sql = "SELECT user_id FROM group_members WHERE group_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    memberIds.add(rs.getInt("user_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] getGroupMemberIds error: " + e.getMessage());
        }
        return memberIds;
    }

    public String getUsernameById(int userId) {
        String sql = "SELECT username FROM users WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("username");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] getUsernameById error: " + e.getMessage());
        }
        return null;
    }
}
