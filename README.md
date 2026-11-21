# 💬 ChatApp — Real-Time Chat Application

> **Internship Project** | Java Sockets + JavaFX + MySQL

---

## 📋 Features

| Feature | Status |
|---|---|
| User Registration & Login | ✅ |
| Private 1-on-1 Chat | ✅ |
| Group Chat | ✅ |
| Message History | ✅ |
| Online/Offline Status | ✅ |
| Real-Time Updates | ✅ |
| Create Groups & Add Members | ✅ |

---

## 🛠️ Technologies Used

| Layer | Technology |
|---|---|
| **UI** | JavaFX 21 + FXML |
| **Network** | Java Sockets (TCP) |
| **Database** | MySQL 8.x |
| **Build** | Maven 3.x |
| **Language** | Java 17 |

---

## 📁 Project Structure

```
ChatApp/
├── src/main/java/com/chatapp/
│   ├── common/
│   │   └── Message.java          ← Shared protocol object
│   ├── server/
│   │   ├── ChatServer.java       ← Main server (Port 5000)
│   │   ├── ClientHandler.java    ← Per-client thread
│   │   └── DatabaseManager.java  ← MySQL CRUD operations
│   └── client/
│       ├── Main.java             ← JavaFX App entry point
│       ├── ChatClient.java       ← Socket client
│       └── controller/
│           ├── LoginController.java  ← Login/Register UI
│           └── ChatController.java   ← Main Chat UI
├── src/main/resources/com/chatapp/client/
│   ├── view/
│   │   ├── login.fxml            ← Login screen UI
│   │   └── chat.fxml             ← Chat screen UI
│   └── css/
│       └── style.css             ← Modern dark theme
├── database.sql                  ← Database setup script
└── pom.xml                       ← Maven build file
```

---

## ⚙️ Setup & Run

### Step 1 — Prerequisites

- **Java 17+** — [Download](https://adoptium.net/)
- **Maven 3.8+** — [Download](https://maven.apache.org/)
- **MySQL 8.x** — [Download](https://dev.mysql.com/downloads/)

### Step 2 — Database Setup

```bash
# Open MySQL command line
mysql -u root -p

# Run the SQL script
source path/to/ChatApp/database.sql
```

Or import `database.sql` via **MySQL Workbench**.

### Step 3 — Configure Database

Edit `DatabaseManager.java` if your MySQL credentials are different:
```java
private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp?useSSL=false&serverTimezone=UTC";
private static final String DB_USER = "root";
private static final String DB_PASSWORD = "root";   // ← Change this
```

### Step 4 — Build

```bash
cd ChatApp
mvn clean package -DskipTests
```

### Step 5 — Run Server

```bash
# Option 1: Maven
mvn exec:java -Dexec.mainClass="com.chatapp.server.ChatServer"

# Option 2: JAR (after package)
java -jar target/ChatServer.jar
```

You should see:
```
[SERVER] ChatApp Server started on port 5000
[SERVER] Waiting for connections...
```

### Step 6 — Run Client(s)

```bash
# In a new terminal window
mvn javafx:run
```

> 💡 Open **multiple terminal windows** and run the client multiple times to test multi-user chat!

---

## 🎮 Usage

1. **Register** a new account (username, email, password)
2. **Login** with your credentials
3. **Online Users** list shows who is currently online
4. **Click a user** to start a private chat
5. **Click a group** to join group conversation
6. **Create Group** button → enter group name → group created!
7. All messages are **saved to MySQL** and loaded on re-login

---

## 🏗️ Architecture

```
┌──────────────┐     TCP Socket      ┌──────────────────┐
│  JavaFX      │ ◄─────────────────► │   ChatServer     │
│  Client      │   Port 5000         │   (Port 5000)    │
│              │   Java Objects      │                  │
│  ChatClient  │   (Serialized)      │  ClientHandler   │
└──────────────┘                     │  (per client)    │
                                     │       │          │
                                     │       ▼          │
                                     │  DatabaseManager │
                                     │       │          │
                                     │       ▼          │
                                     │    MySQL DB      │
                                     └──────────────────┘
```

### Message Flow (Private Chat)

```
User A                 Server              User B
  │                     │                    │
  │── PRIVATE_MSG ──►   │                    │
  │                     │── save to DB ──►   │
  │                     │── INCOMING_MSG ──► │
  │◄── SUCCESS ─────────│                    │
```

---

## 🔒 Security Notes

- Passwords are hashed with **SHA-256** before storing in DB
- No plain-text passwords are stored or transmitted (only the hash)
- For production: use **BCrypt** (much stronger) instead of SHA-256

---

## 🧪 Test Accounts

After running `database.sql`, these accounts are available:

| Username | Password |
|---|---|
| alice | password123 |
| bob | password123 |

---

## 📝 License

This project is for educational/internship purposes.
