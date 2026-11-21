package com.chatapp.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Message — Shared protocol object between Server and Client.
 * Uses Java Serialization over ObjectStream.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        // ── Authentication ──────────────────────────────
        REGISTER,
        LOGIN,
        LOGOUT,

        // ── Chat ────────────────────────────────────────
        PRIVATE_MSG,
        GROUP_MSG,

        // ── Data Requests ───────────────────────────────
        GET_ONLINE_USERS,
        GET_ALL_USERS,
        GET_GROUPS,
        GET_MY_GROUPS,
        GET_PRIVATE_HISTORY,
        GET_GROUP_HISTORY,

        // ── Group Management ────────────────────────────
        CREATE_GROUP,
        ADD_MEMBER,

        // ── Server Responses ────────────────────────────
        SUCCESS,
        ERROR,
        USER_LIST,
        GROUP_LIST,
        MESSAGE_HISTORY,

        // ── Real-Time Events ────────────────────────────
        USER_STATUS_UPDATE,
        INCOMING_PRIVATE_MSG,
        INCOMING_GROUP_MSG
    }

    // ── Core Fields ─────────────────────────────────────
    private Type    type;

    // Auth / identity
    private String  senderUsername;   // also called "sender" for brevity
    private String  senderPassword;   // login/register only
    private String  senderEmail;      // register only
    private int     senderId;

    // Target
    private String  targetUsername;   // also called "target" for brevity
    private String  targetGroup;
    private int     targetGroupId;    // also called "groupId" for brevity

    // Payload
    private String       content;
    private String       timestamp;
    private List<String> listData;

    // ════════════════════════════════════════════════════
    //  Constructors
    // ════════════════════════════════════════════════════

    /** Default no-arg constructor */
    public Message() {
        listData = new ArrayList<>();
    }

    /**
     * Convenience constructor used by the server for simple responses.
     * Maps: sender → senderUsername, target → targetUsername, content → content
     */
    public Message(Type type, String sender, String target, String content) {
        this();
        this.type           = type;
        this.senderUsername = sender;
        this.targetUsername = target;
        this.content        = content;
    }

    /**
     * Convenience constructor used by ClientHandler for USER_LIST / GROUP_LIST responses.
     */
    public Message(Type type) {
        this();
        this.type = type;
    }

    // ════════════════════════════════════════════════════
    //  Static Factory Helpers (used by client side)
    // ════════════════════════════════════════════════════

    public static Message login(String username, String password) {
        Message m = new Message();
        m.type           = Type.LOGIN;
        m.senderUsername = username;
        m.senderPassword = password;
        return m;
    }

    public static Message register(String username, String password, String email) {
        Message m = new Message();
        m.type           = Type.REGISTER;
        m.senderUsername = username;
        m.senderPassword = password;
        m.senderEmail    = email;
        return m;
    }

    public static Message privateMsg(String sender, String target, String content, String ts) {
        Message m = new Message();
        m.type           = Type.PRIVATE_MSG;
        m.senderUsername = sender;
        m.targetUsername = target;
        m.content        = content;
        m.timestamp      = ts;
        return m;
    }

    public static Message groupMsg(String sender, int groupId, String groupName, String content, String ts) {
        Message m = new Message();
        m.type         = Type.GROUP_MSG;
        m.senderUsername = sender;
        m.targetGroupId  = groupId;
        m.targetGroup    = groupName;
        m.content        = content;
        m.timestamp      = ts;
        return m;
    }

    public static Message success(String content) {
        Message m = new Message();
        m.type    = Type.SUCCESS;
        m.content = content;
        return m;
    }

    public static Message error(String content) {
        Message m = new Message();
        m.type    = Type.ERROR;
        m.content = content;
        return m;
    }

    public static Message request(Type type) {
        Message m = new Message();
        m.type = type;
        return m;
    }

    // ════════════════════════════════════════════════════
    //  Getters & Setters
    //  (Both verbose and short aliases for compatibility)
    // ════════════════════════════════════════════════════

    public Type getType()                    { return type; }
    public void setType(Type type)           { this.type = type; }

    // ── Sender ──
    public String getSenderUsername()        { return senderUsername; }
    public void setSenderUsername(String v)  { this.senderUsername = v; }

    /** Alias for getSenderUsername() — used by server code */
    public String getSender()                { return senderUsername; }
    public void setSender(String v)          { this.senderUsername = v; }

    public String getSenderPassword()        { return senderPassword; }
    public void setSenderPassword(String v)  { this.senderPassword = v; }

    public String getSenderEmail()           { return senderEmail; }
    public void setSenderEmail(String v)     { this.senderEmail = v; }

    public int getSenderId()                 { return senderId; }
    public void setSenderId(int v)           { this.senderId = v; }

    // ── Target ──
    public String getTargetUsername()        { return targetUsername; }
    public void setTargetUsername(String v)  { this.targetUsername = v; }

    /** Alias for getTargetUsername() — used by server code */
    public String getTarget()                { return targetUsername; }
    public void setTarget(String v)          { this.targetUsername = v; }

    public String getTargetGroup()           { return targetGroup; }
    public void setTargetGroup(String v)     { this.targetGroup = v; }

    public int getTargetGroupId()            { return targetGroupId; }
    public void setTargetGroupId(int v)      { this.targetGroupId = v; }

    /** Alias for getTargetGroupId() — used by server code */
    public int getGroupId()                  { return targetGroupId; }
    public void setGroupId(int v)            { this.targetGroupId = v; }

    // ── Payload ──
    public String getContent()               { return content; }
    public void setContent(String v)         { this.content = v; }

    public String getTimestamp()             { return timestamp; }
    public void setTimestamp(String v)       { this.timestamp = v; }

    public List<String> getListData()        { return listData; }
    public void setListData(List<String> v)  { this.listData = v != null ? v : new ArrayList<>(); }

    @Override
    public String toString() {
        return "Message{type=" + type
                + ", from=" + senderUsername
                + ", to=" + targetUsername
                + ", content=" + (content != null ? content.substring(0, Math.min(content.length(), 40)) : "null")
                + "}";
    }
}
