# 🌐 Multicast Chat — JavaFX UDP Multicast

![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![UDP](https://img.shields.io/badge/Protocol-UDP%20Multicast-green)
![License](https://img.shields.io/badge/License-MIT-yellow)

Ứng dụng **chat realtime trong mạng LAN** sử dụng **UDP Multicast** — hỗ trợ **Unicast**, **Broadcast**, và **Group Chat (Multicast)**.

---

## ✨ TÍNH NĂNG

### 🎯 Chế độ gửi tin
| Chế độ | Mô tả | Demo |
|---|---|---|
| **Unicast** | Gửi riêng cho 1 người | Click vào user → chat riêng |
| **Broadcast** | Gửi cho TẤT CẢ | Nút "Chat với TẤT CẢ" |
| **Group (Multicast)** | Gửi cho nhóm — chỉ member nhận | Nút JOIN/LEAVE |

### 💬 Giao diện
- Bong bóng chat (gửi phải — xanh, nhận trái — xám) như Zalo
- Avatar tròn tự động theo tên
- Divider ngày giờ canh giữa
- "Đang soạn tin..." realtime gần ô nhập
- Emoji picker 40 emoji
- React cảm xúc (❤️ 😆 😮 😢 👍 😡)
- Chuột phải: Copy / Trả lời
- Tự động tìm server qua UDP Discovery
- Không cần nhập IP — chỉ nhập tên

### 🖥️ Server Dashboard
- 3 metric card: Client online, Nhóm, Uptime
- Bảng Client realtime (avatar, IP, port, giờ kết nối)
- Bảng Nhóm realtime (thành viên, số lượng)
- Nhật ký đầy đủ mọi sự kiện

---

## 🏗️ KIẾN TRÚC

```
┌──────────────────────────────┐        ┌──────────────────────────┐
│       CLIENT (JavaFX)        │        │      SERVER (UDP)        │
│  ┌────────────────────────┐  │        │  - Port 2024             │
│  │  Sidebar               │  │◄─UDP──►│  - Discovery 2025        │
│  │  - Users online        │  │  2024  │  - Multicast             │
│  │  - Groups              │  │        │    230.0.0.1:7777        │
│  │  - JOIN / LEAVE        │  │        │                          │
│  └────────────────────────┘  │        │                          │
│  ┌────────────────────────┐  │        │                          │
│  │  Chat Area             │  │        │                          │
│  │  - Bubbles             │  │        │                          │
│  │  - Typing indicator    │  │        │                          │
│  └────────────────────────┘  │        │                          │
└──────────────────────────────┘        └──────────────────────────┘
```

---

## 📁 CẤU TRÚC PROJECT

```
MulticastChatJavaFX/
├── README.md
├── .gitignore
├── LICENSE
├── run-server.bat
├── run-client.bat
├── docs/
│   ├── login.png
│   ├── chat.png
│   └── group.png
├── src/
│   ├── server/
│   │   ├── ChatServerCore.java
│   │   └── ChatServerApp.java
│   └── client/
│       ├── ChatClientCore.java
│       └── ChatClientApp.java
└── lib/  (không push)
```

---

## 🚀 CÀI ĐẶT

### Yêu cầu
- **JDK 21** — [Tải Eclipse Temurin](https://adoptium.net/)
- **JavaFX SDK 21.0.5** — [Tải tại đây](https://gluonhq.com/products/javafx/)
- **Ikonli** (3 file .jar)

### Bước 1: Clone
```bash
git clone https://github.com/YOUR_USERNAME/MulticastChatJavaFX.git
cd MulticastChatJavaFX
```

### Bước 2: Tải thư viện vào `lib/`
- `javafx-sdk-21.0.5/`
- `ikonli-core-12.3.1.jar`
- `ikonli-javafx-12.3.1.jar`
- `ikonli-fontawesome5-pack-12.3.1.jar`

### Bước 3: Cấu hình đường dẫn JDK
Mở `run-server.bat` và `run-client.bat`, sửa dòng:
```bat
set JDK=C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot
```
thành đường dẫn JDK trên máy bạn.

### Bước 4: Compile
```bash
cd /d F:\MulticastChatJavaFX

"C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot\bin\javac" -encoding UTF-8 -d bin --module-path "lib\javafx-sdk-21.0.5\lib" --add-modules javafx.controls,javafx.fxml -cp "lib\ikonli-core-12.3.1.jar;lib\ikonli-javafx-12.3.1.jar;lib\ikonli-fontawesome5-pack-12.3.1.jar" src\server\ChatServerCore.java src\server\ChatServerApp.java src\client\ChatClientCore.java src\client\ChatClientApp.java
```

### Bước 5: Chạy
```bash
# Terminal 1 — Server
run-server.bat

# Terminal 2, 3, 4, ... — Client (mở nhiều cửa sổ)
run-client.bat
```

---

## 🎬 DEMO THEO YÊU CẦU

### Kịch bản: 5 client — 3 join nhóm

| Bước | Thao tác | Kết quả |
|---|---|---|
| 1 | Server khởi động | ✅ Listening port 2024 |
| 2 | Mở 5 client: `an`, `binh`, `cuong`, `dung`, `em` | ✅ Mỗi client thấy đủ 4 user khác |
| 3 | `an`, `binh`, `cuong` nhập `group1` → **JOIN** | ✅ 3 người ở nhóm `group1` |
| 4 | `dung`, `em` KHÔNG join | ✅ Ở ngoài nhóm |
| 5 | `an` click nhóm `group1` → gõ "Chào nhóm" → Gửi | ✅ Chỉ `an`, `binh`, `cuong` nhận |
| 6 | `an` bấm **LEAVE** | ✅ Rời nhóm |
| 7 | `an` gửi lại "Test" | ❌ Server báo lỗi "chưa ở nhóm" |
| 8 | `binh` gửi "Chào 2 người" | ✅ Chỉ `binh`, `cuong` nhận |
| 9 | `an` click user `dung` → gửi "Hello" | ✅ Chỉ `dung` nhận (Unicast) |
| 10 | `an` bấm **Chat TẤT CẢ** → gửi "Thông báo" | ✅ CẢ 5 nhận (Broadcast) |

---

## 📡 GIAO THỨC UDP

### Client → Server
| Lệnh | Format |
|---|---|
| LOGIN | `LOGIN\|username\|recvPort` |
| LOGOUT | `LOGOUT\|username` |
| LIST_USERS | `LIST_USERS` |
| UNICAST | `UNICAST\|from\|to\|msg` |
| BROADCAST | `BROADCAST\|from\|msg` |
| JOIN_GROUP | `JOIN_GROUP\|username\|groupId` |
| LEAVE_GROUP | `LEAVE_GROUP\|username\|groupId` |
| GROUP_MSG | `GROUP_MSG\|from\|groupId\|msg` |
| TYPING | `TYPING\|from\|target` |

### Server → Client
| Lệnh | Format |
|---|---|
| OK | `OK\|message` |
| ERROR | `ERROR\|message` |
| USERLIST | `USERLIST\|user1,user2,...` |
| GROUPLIST | `GROUPLIST\|group1:a,b;group2:x,y;` |
| UNICAST | `MSG\|UNICAST\|from\|fromIp\|msg` |
| BROADCAST | `MSG\|BROADCAST\|from\|fromIp\|msg` |
| TYPING | `TYPING\|from\|target` |

### Multicast (Group)
| Lệnh | Format |
|---|---|
| MCAST | `MCAST\|groupId\|from\|fromIp\|msg` |

---

## 🔧 TROUBLESHOOTING

### Client đầu tiên không thấy user mới?
**Nguyên nhân**: Socket dùng chung cho gửi và nhận.
**Giải pháp**: Đã fix — tách 2 socket riêng.

### Nhóm không chat được?
**Nguyên nhân**: Multicast bị firewall chặn.
**Giải pháp**: Mở CMD admin:
```cmd
netsh advfirewall firewall add rule name="UDP 7777" dir=in action=allow protocol=UDP localport=7777
```

### Không tìm thấy server?
**Nguyên nhân**: Server chưa chạy hoặc khác mạng.
**Giải pháp**: Đảm bảo server đã "Khởi động" và cùng WiFi/LAN.

---

## 🛠️ CÔNG NGHỆ

| Layer | Công nghệ |
|---|---|
| Ngôn ngữ | Java 21 |
| GUI | JavaFX 21 + Ikonli (FontAwesome 5) |
| Network | UDP DatagramSocket + MulticastSocket |
| Discovery | UDP Broadcast (port 2025) |
| Multicast | 230.0.0.1:7777 (Class D) |
| Build | javac + batch script |

---

## 👥 TÁC GIẢ

**Sinh viên thực hiện:**

| STT | Họ và tên | MSSV | Lớp |
|:---:|:---|---:|:---:|
| 1 | **Nguyễn Thị Bích Trâm** | 24IT277 | 24SE1 |
| 2 | **Trần Văn Tri** | 24IT283 | 24SE1 |

**GVHD:** ThS. Nguyễn Thanh Cẩm
**Môn:** Lập trình mạng — Đồ án / Bài tập lớn

---

## 📄 LICENSE

MIT License — xem file [LICENSE](LICENSE)

---

## ⭐ STAR

Nếu project hữu ích, hãy cho mình 1 ⭐ nhé!
