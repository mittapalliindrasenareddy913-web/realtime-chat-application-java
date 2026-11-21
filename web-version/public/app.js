// Global state
let currentUser = localStorage.getItem("chat_username") || null;
let activeChat = null; // { type: 'private' | 'group', target: string, name: string }
let lastPolledMsgId = 0;
let pollTimer = null;
let heartbeatTimer = null;

// DOM Elements
const authScreen = document.getElementById("authScreen");
const chatScreen = document.getElementById("chatScreen");
const loginForm = document.getElementById("loginForm");
const registerForm = document.getElementById("registerForm");
const loginTabBtn = document.getElementById("loginTabBtn");
const registerTabBtn = document.getElementById("registerTabBtn");
const usersList = document.getElementById("usersList");
const groupsList = document.getElementById("groupsList");
const messagesContainer = document.getElementById("messagesContainer");
const messageInput = document.getElementById("messageInput");
const sendBtn = document.getElementById("sendBtn");
const targetName = document.getElementById("targetName");
const targetStatus = document.getElementById("targetStatus");
const targetAvatar = document.getElementById("targetAvatar");
const myAvatar = document.getElementById("myAvatar");
const myUsername = document.getElementById("myUsername");
const onlineUsersCount = document.getElementById("onlineUsersCount");
const groupsCount = document.getElementById("groupsCount");

// Initialize on page load
window.addEventListener("DOMContentLoaded", () => {
  if (currentUser) {
    showChatScreen(currentUser);
  } else {
    showAuthScreen();
  }
});

// --- Tab Toggle ---
function switchAuthTab(tab) {
  if (tab === "login") {
    loginForm.style.display = "block";
    registerForm.style.display = "none";
    loginTabBtn.classList.add("active");
    registerTabBtn.classList.remove("active");
  } else {
    loginForm.style.display = "none";
    registerForm.style.display = "block";
    registerTabBtn.classList.add("active");
    loginTabBtn.classList.remove("active");
  }
}

// --- Quick 1-Click Login ---
function quickLogin(uname) {
  document.getElementById("loginUsername").value = uname;
  document.getElementById("loginPassword").value = "password123";
  handleLogin({ preventDefault: () => {} });
}

// --- Auth Handlers ---
async function handleLogin(e) {
  if (e && e.preventDefault) e.preventDefault();
  const username = document.getElementById("loginUsername").value.trim();
  const password = document.getElementById("loginPassword").value;
  const errorEl = document.getElementById("loginError");
  errorEl.style.display = "none";

  try {
    const res = await fetch("/api/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });
    const data = await res.json();
    if (data.success) {
      currentUser = data.username;
      localStorage.setItem("chat_username", currentUser);
      showChatScreen(currentUser);
    } else {
      errorEl.innerText = data.message || "Login failed";
      errorEl.style.display = "block";
    }
  } catch (err) {
    errorEl.innerText = "Cannot connect to server!";
    errorEl.style.display = "block";
  }
}

async function handleRegister(e) {
  if (e && e.preventDefault) e.preventDefault();
  const username = document.getElementById("regUsername").value.trim();
  const email = document.getElementById("regEmail").value.trim();
  const password = document.getElementById("regPassword").value;
  const errorEl = document.getElementById("regError");
  const successEl = document.getElementById("regSuccess");
  errorEl.style.display = "none";
  successEl.style.display = "none";

  try {
    const res = await fetch("/api/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, email, password })
    });
    const data = await res.json();
    if (data.success) {
      successEl.innerText = data.message;
      successEl.style.display = "block";
      setTimeout(() => switchAuthTab("login"), 1500);
    } else {
      errorEl.innerText = data.message;
      errorEl.style.display = "block";
    }
  } catch (err) {
    errorEl.innerText = "Server connection error!";
    errorEl.style.display = "block";
  }
}

async function handleLogout() {
  if (currentUser) {
    await fetch("/api/logout", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: currentUser })
    });
  }
  clearInterval(pollTimer);
  clearInterval(heartbeatTimer);
  currentUser = null;
  activeChat = null;
  localStorage.removeItem("chat_username");
  showAuthScreen();
}

function showAuthScreen() {
  authScreen.style.display = "flex";
  chatScreen.style.display = "none";
}

function showChatScreen(username) {
  authScreen.style.display = "none";
  chatScreen.style.display = "flex";
  myUsername.innerText = username;
  myAvatar.innerText = username.charAt(0).toUpperCase();

  // Load lists
  loadUsers();
  loadGroups();

  // Start Realtime Polling loop (every 1 second)
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(pollNewMessages, 1000);

  // Heartbeat to keep status active (every 8s)
  if (heartbeatTimer) clearInterval(heartbeatTimer);
  heartbeatTimer = setInterval(sendHeartbeat, 8000);
  sendHeartbeat();
}

async function sendHeartbeat() {
  if (!currentUser) return;
  try {
    await fetch("/api/heartbeat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: currentUser })
    });
    loadUsers(); // Refresh online indicators
  } catch (ignored) {}
}

// --- Load Users & Groups ---
async function loadUsers() {
  try {
    const res = await fetch("/api/users");
    const users = await res.json();
    usersList.innerHTML = "";
    let onlineCount = 0;

    users.forEach(u => {
      if (u.username.toLowerCase() === currentUser.toLowerCase()) return;
      if (u.isOnline) onlineCount++;

      const item = document.createElement("div");
      item.className = `contact-item ${activeChat && activeChat.type === 'private' && activeChat.target === u.username ? 'active' : ''}`;
      item.onclick = () => selectChat("private", u.username, u.username, u.isOnline);

      item.innerHTML = `
        <div class="avatar">${u.username.charAt(0).toUpperCase()}</div>
        <div class="contact-info">
          <div class="contact-name">${u.username}</div>
          <div class="contact-sub">${u.isOnline ? 'Active now' : 'Offline'}</div>
        </div>
        <div class="status-pill ${u.isOnline ? 'online' : ''}"></div>
      `;
      usersList.appendChild(item);
    });
    onlineUsersCount.innerText = onlineCount;
  } catch (err) {}
}

async function loadGroups() {
  try {
    const res = await fetch("/api/groups");
    const groups = await res.json();
    groupsList.innerHTML = "";
    groupsCount.innerText = groups.length;

    groups.forEach(g => {
      const item = document.createElement("div");
      item.className = `contact-item ${activeChat && activeChat.type === 'group' && activeChat.target === g.id ? 'active' : ''}`;
      item.onclick = () => selectChat("group", g.id, g.name);

      item.innerHTML = `
        <div class="avatar" style="background:#0284c7;">#</div>
        <div class="contact-info">
          <div class="contact-name">${g.name}</div>
          <div class="contact-sub">Group Channel</div>
        </div>
      `;
      groupsList.appendChild(item);
    });
  } catch (err) {}
}

// --- Select a Conversation ---
async function selectChat(type, target, name, isOnline = true) {
  activeChat = { type, target, name };

  // Update Header
  targetName.innerText = name;
  targetAvatar.innerText = type === "group" ? "#" : name.charAt(0).toUpperCase();
  targetStatus.innerText = type === "group" ? "Group channel • Everyone here" : (isOnline ? "🟢 Online" : "⚫ Offline");

  // Enable Inputs
  messageInput.disabled = false;
  sendBtn.disabled = false;
  messageInput.focus();

  // Highlight in sidebar
  document.querySelectorAll(".contact-item").forEach(el => el.classList.remove("active"));
  loadUsers();
  loadGroups();

  // Load History
  await loadMessageHistory();
}

async function loadMessageHistory() {
  if (!activeChat) return;
  try {
    const url = `/api/messages/history?user=${encodeURIComponent(currentUser)}&targetType=${activeChat.type}&target=${encodeURIComponent(activeChat.target)}`;
    const res = await fetch(url);
    const msgs = await res.json();

    messagesContainer.innerHTML = "";
    if (msgs.length === 0) {
      messagesContainer.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">💬</div>
          <h3>No messages yet</h3>
          <p>Send a message below to start the conversation!</p>
        </div>
      `;
    } else {
      msgs.forEach(m => {
        appendMessage(m, false);
        if (m.id > lastPolledMsgId) lastPolledMsgId = m.id;
      });
      scrollToBottom();
    }
  } catch (err) {}
}

// --- Send Message ---
async function handleSendMessage(e) {
  if (e && e.preventDefault) e.preventDefault();
  const text = messageInput.value.trim();
  if (!text || !activeChat) return;

  messageInput.value = "";

  try {
    const res = await fetch("/api/messages/send", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        sender: currentUser,
        targetType: activeChat.type,
        target: activeChat.target,
        content: text
      })
    });
    const data = await res.json();
    if (data.success) {
      // Clear empty state if visible
      const emptyState = messagesContainer.querySelector(".empty-state");
      if (emptyState) emptyState.remove();

      appendMessage({
        id: data.id,
        sender: currentUser,
        content: text,
        timestamp: data.timestamp
      }, true);
      lastPolledMsgId = Math.max(lastPolledMsgId, data.id);
      scrollToBottom();
    }
  } catch (err) {}
}

// --- Append message bubble ---
function appendMessage(msg, isNew = true) {
  const isMe = msg.sender.toLowerCase() === currentUser.toLowerCase();
  const row = document.createElement("div");
  row.className = `msg-row ${isMe ? 'me' : 'other'}`;
  row.dataset.msgId = msg.id;

  row.innerHTML = `
    ${!isMe ? `<span class="msg-sender">${msg.sender}</span>` : ''}
    <div class="msg-bubble">${escapeHtml(msg.content)}</div>
    <span class="msg-time">${msg.timestamp}</span>
  `;

  messagesContainer.appendChild(row);
  if (isNew) scrollToBottom();
}

// --- Realtime Poll for incoming messages ---
async function pollNewMessages() {
  if (!currentUser) return;
  try {
    const url = `/api/messages/poll?user=${encodeURIComponent(currentUser)}&lastId=${lastPolledMsgId}`;
    const res = await fetch(url);
    const newMsgs = await res.json();

    if (newMsgs && newMsgs.length > 0) {
      newMsgs.forEach(m => {
        if (m.id > lastPolledMsgId) {
          lastPolledMsgId = m.id;
          // Check if message belongs to active chat
          if (activeChat) {
            let belongs = false;
            if (activeChat.type === 'group' && m.targetType === 'group' && m.target === activeChat.target) {
              belongs = true;
            } else if (activeChat.type === 'private' && m.targetType === 'private') {
              if (m.sender.toLowerCase() === activeChat.target.toLowerCase() && m.target.toLowerCase() === currentUser.toLowerCase()) {
                belongs = true;
              }
            }

            if (belongs) {
              const emptyState = messagesContainer.querySelector(".empty-state");
              if (emptyState) emptyState.remove();
              appendMessage(m, true);
            }
          }
        }
      });
    }
  } catch (ignored) {}
}

function scrollToBottom() {
  messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.innerText = str;
  return div.innerHTML;
}

// --- Create Group Modal ---
function openCreateGroupModal() {
  document.getElementById("createGroupModal").style.display = "flex";
  document.getElementById("newGroupName").value = "";
  document.getElementById("newGroupName").focus();
}

function closeCreateGroupModal() {
  document.getElementById("createGroupModal").style.display = "none";
}

async function confirmCreateGroup() {
  const name = document.getElementById("newGroupName").value.trim();
  if (!name) return;

  try {
    const res = await fetch("/api/groups/create", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, creator: currentUser })
    });
    const data = await res.json();
    if (data.success) {
      closeCreateGroupModal();
      await loadGroups();
      selectChat("group", data.id, data.name);
    }
  } catch (err) {}
}
