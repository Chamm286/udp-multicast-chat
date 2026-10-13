package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatClientApp extends Application {

    private static final String BG_APP       = "#F1F5F9";
    private static final String BG_WHITE     = "#FFFFFF";
    private static final String BORDER       = "#E2E8F0";
    private static final String BORDER_SOFT  = "#F1F5F9";
    private static final String TEXT_DARK    = "#0F172A";
    private static final String TEXT_GRAY    = "#64748B";
    private static final String BLUE         = "#2563EB";
    private static final String BLUE_LIGHT   = "#EFF6FF";
    private static final String GREEN        = "#10B981";
    private static final String GREEN_BG     = "#ECFDF5";
    private static final String RED          = "#EF4444";
    private static final String RED_BG       = "#FEF2F2";
    private static final String ORANGE       = "#EA580C";
    private static final String ORANGE_BG    = "#FFF7ED";
    private static final String CYAN         = "#0891B2";
    private static final String PINK         = "#DB2777";
    private static final String PURPLE       = "#7C3AED";

    private ChatClientCore core;
    private String myUsername = null;
    private String myGroup    = null;
    private String myIP       = "unknown";
    private String serverIP   = null;
    private int    serverPort = 2024;

    private Stage primaryStage;
    private TextField loginUserField;
    private Label loginStatusLabel;
    private Button  loginConnectBtn;

    private TextField msgField;
    private TextField groupNameField;
    private ListView<String> userList;
    private ListView<String> groupList;
    private Label  groupLabel, chatHeaderNameLbl, chatHeaderSubLbl, chatHeaderAvatarLbl;
    private Button joinBtn, leaveBtn;
    private VBox   sidebar;
    private VBox   messagesBox;
    private ScrollPane chatScroll;
    private Label  typingLabel;   // ⭐ Đặt GẦN ô nhập tin

    private String currentChatType = "BROADCAST";
    private String currentChatTarget = null;
    private String currentChatKey = "broadcast";

    private final Map<String, List<Message>> messagesByChat = new HashMap<>();

    private static class Message {
        String from, text;
        boolean isMine;
        LocalDateTime time;
        Message(String f, String t, boolean m, LocalDateTime ti) {
            from = f; text = t; isMine = m; time = ti;
        }
    }

    private Thread listenThread, mcastThread, pingThread;
    private boolean running = true;
    private long lastTypingSent = 0;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        myIP = detectMyIP();
        showLoginScreen();
    }

    // ═════════════════════════════════════════════════════════════════
    //           LOGIN
    // ═════════════════════════════════════════════════════════════════
    private void showLoginScreen() {
        VBox leftPanel = new VBox(24);
        leftPanel.setPrefWidth(520);
        leftPanel.setMinWidth(520);
        leftPanel.setMaxWidth(520);
        leftPanel.setAlignment(Pos.CENTER);
        leftPanel.setPadding(new Insets(60));
        leftPanel.setStyle("-fx-background-color:" + BLUE + ";");

        FontIcon bigLogo = new FontIcon(FontAwesomeSolid.COMMENTS);
        bigLogo.setIconSize(72);
        bigLogo.setIconColor(Color.WHITE);
        StackPane logoCircle = new StackPane(bigLogo);
        logoCircle.setStyle("-fx-background-color:rgba(255,255,255,0.2); "
                + "-fx-background-radius:40; -fx-padding:28;");
        logoCircle.setMaxSize(140, 140);
        logoCircle.setMinSize(140, 140);

        Label bigTitle = new Label("Multicast Chat");
        bigTitle.setStyle("-fx-text-fill:white; -fx-font-size:40px; -fx-font-weight:bold;");

        Label bigSub = new Label("Unicast • Broadcast • Group Chat");
        bigSub.setStyle("-fx-text-fill:rgba(255,255,255,0.9); -fx-font-size:15px;");

        HBox badges = new HBox(10);
        badges.setAlignment(Pos.CENTER);
        badges.getChildren().addAll(
                makeBadge("🌐", "Broadcast"),
                makeBadge("👤", "Unicast"),
                makeBadge("👥", "Group")
        );

        leftPanel.getChildren().addAll(logoCircle, bigTitle, bigSub, badges);

        VBox rightPanel = new VBox(24);
        rightPanel.setAlignment(Pos.CENTER);
        rightPanel.setPadding(new Insets(60));
        rightPanel.setStyle("-fx-background-color:" + BG_WHITE + ";");
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        Label welcome = new Label("Chào mừng!");
        welcome.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:30px; -fx-font-weight:bold;");

        Label hint = new Label("Nhập tên để bắt đầu");
        hint.setStyle("-fx-text-fill:" + TEXT_GRAY + "; -fx-font-size:13.5px;");

        VBox titleBox = new VBox(6, welcome, hint);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setMaxWidth(400);

        Label nameLbl = new Label("TÊN ĐĂNG NHẬP");
        nameLbl.setStyle("-fx-text-fill:" + TEXT_GRAY + "; -fx-font-size:11px; -fx-font-weight:bold;");

        loginUserField = new TextField();
        loginUserField.setPromptText("VD: an, binh, cuong...");
        loginUserField.setPrefHeight(52);
        loginUserField.setStyle("-fx-background-color:#F8FAFC; -fx-border-color:" + BORDER + "; "
                + "-fx-border-radius:10; -fx-background-radius:10; "
                + "-fx-padding:0 18; -fx-font-size:14.5px;");
        loginUserField.setOnAction(e -> doLogin());

        VBox nameBox = new VBox(8, nameLbl, loginUserField);
        nameBox.setMaxWidth(400);

        loginConnectBtn = new Button("BẮT ĐẦU CHAT");
        loginConnectBtn.setPrefWidth(400);
        loginConnectBtn.setPrefHeight(52);
        loginConnectBtn.setStyle("-fx-background-color:" + BLUE + "; "
                + "-fx-text-fill:white; -fx-font-size:15px; -fx-font-weight:bold; "
                + "-fx-background-radius:10; -fx-cursor:hand;");
        loginConnectBtn.setOnAction(e -> doLogin());

        loginStatusLabel = new Label("");
        loginStatusLabel.setStyle("-fx-text-fill:" + RED + "; -fx-font-size:12.5px; -fx-font-weight:bold;");
        loginStatusLabel.setWrapText(true);
        loginStatusLabel.setMaxWidth(400);
        loginStatusLabel.setAlignment(Pos.CENTER);

        VBox formBox = new VBox(16, nameBox, loginConnectBtn, loginStatusLabel);
        formBox.setAlignment(Pos.CENTER);

        rightPanel.getChildren().addAll(titleBox, formBox);

        HBox root = new HBox(leftPanel, rightPanel);

        Scene scene = new Scene(root, 1100, 720);
        primaryStage.setTitle("Multicast Chat");
        primaryStage.setScene(scene);
        primaryStage.show();
        Platform.runLater(() -> loginUserField.requestFocus());
    }

    private VBox makeBadge(String icon, String text) {
        Label icLbl = new Label(icon);
        icLbl.setStyle("-fx-font-size:15px;");
        Label txtLbl = new Label(text);
        txtLbl.setStyle("-fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:bold;");
        HBox row = new HBox(6, icLbl, txtLbl);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(8, 16, 8, 16));
        row.setStyle("-fx-background-color:rgba(255,255,255,0.18); -fx-background-radius:20;");
        return new VBox(row);
    }

    private void doLogin() {
        String u = loginUserField.getText().trim();
        if (u.isEmpty()) {
            loginStatusLabel.setText("⚠ Chưa nhập tên!");
            return;
        }

        loginConnectBtn.setDisable(true);
        loginConnectBtn.setText("ĐANG TÌM SERVER...");
        loginStatusLabel.setStyle("-fx-text-fill:" + BLUE + "; -fx-font-size:12.5px; -fx-font-weight:bold;");
        loginStatusLabel.setText("📡 Đang tìm server...");

        myUsername = u;

        new Thread(() -> {
            String[] server = ChatClientCore.discoverServer(3000);
            if (server == null) {
                Platform.runLater(() -> {
                    loginStatusLabel.setStyle("-fx-text-fill:" + RED + "; -fx-font-size:12.5px; -fx-font-weight:bold;");
                    loginStatusLabel.setText("❌ Không tìm thấy Server!");
                    loginConnectBtn.setDisable(false);
                    loginConnectBtn.setText("BẮT ĐẦU CHAT");
                });
                return;
            }

            serverIP = server[0];
            try { serverPort = Integer.parseInt(server[1]); }
            catch (Exception e) { serverPort = 2024; }

            Platform.runLater(() -> {
                loginStatusLabel.setStyle("-fx-text-fill:" + GREEN + "; -fx-font-size:12.5px; -fx-font-weight:bold;");
                loginStatusLabel.setText("✅ Tìm thấy Server tại " + serverIP);
            });

            try {
                core = new ChatClientCore(serverIP, serverPort);
                Platform.runLater(() -> {
                    loginStatusLabel.setText("✅ Đang vào chat...");
                    new Thread(() -> {
                        try { Thread.sleep(300); } catch (Exception ignored) {}
                        Platform.runLater(() -> showChatScreen());
                    }).start();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loginStatusLabel.setStyle("-fx-text-fill:" + RED + "; -fx-font-size:12.5px; -fx-font-weight:bold;");
                    loginStatusLabel.setText("❌ Lỗi: " + ex.getMessage());
                    loginConnectBtn.setDisable(false);
                    loginConnectBtn.setText("BẮT ĐẦU CHAT");
                });
            }
        }).start();
    }

    // ═════════════════════════════════════════════════════════════════
    //           CHAT SCREEN
    // ═════════════════════════════════════════════════════════════════
    private void showChatScreen() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + BG_APP + ";");
        root.setLeft(buildSidebar());
        root.setCenter(buildChatArea());

        Scene scene = new Scene(root, 1300, 820);
        primaryStage.setTitle("Multicast Chat — " + myUsername);
        primaryStage.setScene(scene);

        startListening();
        startMulticastListening();
        startPingThread();

        // ⭐ Gửi LOGIN kèm recvPort để server biết trả lời về đâu
        new Thread(() -> {
            try {
                Thread.sleep(300);
                core.sendOnly("LOGIN|" + myUsername + "|" + core.getRecvPort());
                Thread.sleep(500);
                core.sendOnly("LIST_USERS");
            } catch (Exception ignored) {}
        }).start();

        selectBroadcast();
    }

    // ═════════════════════════════════════════════════════════════════
    //           SIDEBAR
    // ═════════════════════════════════════════════════════════════════
    private VBox buildSidebar() {
        sidebar = new VBox(0);
        sidebar.setPrefWidth(320);
        sidebar.setMinWidth(320);
        sidebar.setMaxWidth(320);
        sidebar.setStyle("-fx-background-color:" + BG_WHITE + "; "
                + "-fx-border-color:" + BORDER + "; -fx-border-width:0 1 0 0;");

        Label myAvatar = new Label(myUsername.substring(0, 1).toUpperCase());
        myAvatar.setStyle("-fx-text-fill:white; -fx-font-size:16px; -fx-font-weight:bold; "
                + "-fx-background-color:rgba(255,255,255,0.25); "
                + "-fx-min-width:44; -fx-min-height:44; -fx-max-width:44; -fx-max-height:44; "
                + "-fx-alignment:center; -fx-background-radius:22;");

        Label nameLbl = new Label(myUsername);
        nameLbl.setStyle("-fx-text-fill:white; -fx-font-size:14px; -fx-font-weight:bold;");

        Label ipLbl = new Label("IP: " + myIP);
        ipLbl.setStyle("-fx-text-fill:rgba(255,255,255,0.85); -fx-font-size:11px;");

        Label statusLbl = new Label("● Online • Server " + serverIP);
        statusLbl.setStyle("-fx-text-fill:rgba(255,255,255,0.75); -fx-font-size:10.5px;");

        VBox nameInfo = new VBox(3, nameLbl, ipLbl, statusLbl);
        nameInfo.setAlignment(Pos.CENTER_LEFT);

        HBox userInfo = new HBox(12, myAvatar, nameInfo);
        userInfo.setAlignment(Pos.CENTER_LEFT);
        userInfo.setPadding(new Insets(18, 20, 18, 20));
        userInfo.setStyle("-fx-background-color:" + BLUE + ";");

        Button broadcastBtn = new Button();
        FontIcon bIc = new FontIcon(FontAwesomeSolid.BULLHORN);
        bIc.setIconSize(13);
        bIc.setIconColor(Color.web(BLUE));
        Label bLbl = new Label("Chat với TẤT CẢ");
        bLbl.setGraphic(bIc);
        bLbl.setGraphicTextGap(10);
        bLbl.setStyle("-fx-text-fill:" + BLUE + "; -fx-font-weight:bold; -fx-font-size:13px;");
        broadcastBtn.setGraphic(bLbl);
        broadcastBtn.setPrefWidth(280);
        broadcastBtn.setPrefHeight(42);
        broadcastBtn.setStyle("-fx-background-color:" + BLUE_LIGHT + "; "
                + "-fx-background-radius:10; -fx-cursor:hand;");
        broadcastBtn.setOnAction(e -> selectBroadcast());

        VBox broadcastBox = new VBox(broadcastBtn);
        broadcastBox.setPadding(new Insets(14, 20, 8, 20));

        Label usersTitle = new Label("MỌI NGƯỜI");
        usersTitle.setStyle("-fx-text-fill:" + TEXT_GRAY + "; -fx-font-size:10.5px; -fx-font-weight:bold;");

        Label usersCount = new Label("0");
        usersCount.setId("userCount");
        usersCount.setStyle("-fx-text-fill:" + BLUE + "; -fx-font-size:11px; -fx-font-weight:bold; "
                + "-fx-background-color:" + BLUE_LIGHT + "; -fx-padding:3 10; -fx-background-radius:6;");
        Region sp1 = new Region(); HBox.setHgrow(sp1, Priority.ALWAYS);
        HBox usersHeader = new HBox(8, usersTitle, sp1, usersCount);
        usersHeader.setAlignment(Pos.CENTER_LEFT);
        usersHeader.setPadding(new Insets(12, 20, 8, 20));

        userList = new ListView<>();
        userList.setPrefHeight(200);
        userList.setMinHeight(140);
        userList.setStyle("-fx-background-color:transparent; -fx-border-color:transparent;");
        userList.setCellFactory(lv -> new UserCell());
        VBox.setVgrow(userList, Priority.ALWAYS);
        userList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && !n.equals(myUsername)) selectUnicast(n);
        });

        Label groupsTitle = new Label("NHÓM CHAT");
        groupsTitle.setStyle("-fx-text-fill:" + TEXT_GRAY + "; -fx-font-size:10.5px; -fx-font-weight:bold;");

        Label groupsCount = new Label("0");
        groupsCount.setId("groupCount");
        groupsCount.setStyle("-fx-text-fill:" + ORANGE + "; -fx-font-size:11px; -fx-font-weight:bold; "
                + "-fx-background-color:" + ORANGE_BG + "; -fx-padding:3 10; -fx-background-radius:6;");
        Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);
        HBox groupsHeader = new HBox(8, groupsTitle, sp2, groupsCount);
        groupsHeader.setAlignment(Pos.CENTER_LEFT);
        groupsHeader.setPadding(new Insets(14, 20, 8, 20));

        Button createGroupBtn = new Button();
        FontIcon cIc = new FontIcon(FontAwesomeSolid.PLUS);
        cIc.setIconSize(12);
        cIc.setIconColor(Color.WHITE);
        Label cLbl = new Label("Tạo nhóm mới");
        cLbl.setGraphic(cIc);
        cLbl.setGraphicTextGap(10);
        cLbl.setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:12.5px;");
        createGroupBtn.setGraphic(cLbl);
        createGroupBtn.setPrefWidth(280);
        createGroupBtn.setPrefHeight(42);
        createGroupBtn.setStyle("-fx-background-color:" + ORANGE + "; "
                + "-fx-background-radius:10; -fx-cursor:hand;");
        createGroupBtn.setOnAction(e -> showCreateGroupDialog());

        VBox createGroupBox = new VBox(createGroupBtn);
        createGroupBox.setPadding(new Insets(0, 20, 8, 20));

        groupList = new ListView<>();
        groupList.setPrefHeight(90);
        groupList.setMinHeight(70);
        groupList.setStyle("-fx-background-color:transparent; -fx-border-color:transparent;");
        groupList.setCellFactory(lv -> new GroupCell());
        VBox.setVgrow(groupList, Priority.ALWAYS);
        groupList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) selectGroup(n);
        });

        groupNameField = new TextField();
        groupNameField.setPromptText("Tên nhóm để JOIN");
        groupNameField.setPrefHeight(40);
        groupNameField.setStyle("-fx-background-color:#F8FAFC; -fx-border-color:" + BORDER
                + "; -fx-border-radius:8; -fx-background-radius:8; -fx-padding:0 14; -fx-font-size:12.5px;");
        VBox groupNameBox = new VBox(groupNameField);
        groupNameBox.setPadding(new Insets(0, 20, 8, 20));

        joinBtn = new Button();
        FontIcon jIc = new FontIcon(FontAwesomeSolid.SIGN_IN_ALT);
        jIc.setIconSize(12);
        jIc.setIconColor(Color.WHITE);
        Label jLbl = new Label("JOIN");
        jLbl.setGraphic(jIc);
        jLbl.setGraphicTextGap(8);
        jLbl.setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:13px;");
        joinBtn.setGraphic(jLbl);
        joinBtn.setPrefWidth(134);
        joinBtn.setPrefHeight(44);
        joinBtn.setStyle("-fx-background-color:" + GREEN + "; "
                + "-fx-background-radius:10; -fx-cursor:hand;");
        joinBtn.setOnAction(e -> doJoinGroup());

        leaveBtn = new Button();
        FontIcon lIc = new FontIcon(FontAwesomeSolid.SIGN_OUT_ALT);
        lIc.setIconSize(12);
        lIc.setIconColor(Color.WHITE);
        Label lLbl = new Label("LEAVE");
        lLbl.setGraphic(lIc);
        lLbl.setGraphicTextGap(8);
        lLbl.setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:13px;");
        leaveBtn.setGraphic(lLbl);
        leaveBtn.setPrefWidth(134);
        leaveBtn.setPrefHeight(44);
        leaveBtn.setDisable(true);
        leaveBtn.setStyle("-fx-background-color:" + RED + "; "
                + "-fx-background-radius:10; -fx-cursor:hand;");
        leaveBtn.setOnAction(e -> doLeaveGroup());

        HBox joinLeaveBox = new HBox(12, joinBtn, leaveBtn);
        joinLeaveBox.setAlignment(Pos.CENTER);
        joinLeaveBox.setPadding(new Insets(0, 20, 10, 20));

        groupLabel = new Label("Chưa ở nhóm nào");
        groupLabel.setStyle("-fx-text-fill:" + ORANGE + "; -fx-font-size:11.5px; -fx-font-weight:bold; "
                + "-fx-background-color:" + ORANGE_BG + "; -fx-padding:8 14; -fx-background-radius:8;");

        VBox groupControls = new VBox(10, groupLabel);
        groupControls.setPadding(new Insets(0, 20, 20, 20));

        sidebar.getChildren().addAll(
                userInfo,
                broadcastBox,
                usersHeader, userList,
                groupsHeader,
                createGroupBox,
                groupList,
                groupNameBox,
                joinLeaveBox,
                groupControls
        );
        return sidebar;
    }

    private void showCreateGroupDialog() {
        if (userList.getItems().size() <= 1) {
            addSystemMessage("⚠ Cần ít nhất 1 người khác online!");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(primaryStage);
        dialog.setTitle("Tạo nhóm mới");

        Label nameLbl = new Label("Tên nhóm");
        nameLbl.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:12.5px; -fx-font-weight:bold;");
        TextField nameField = new TextField();
        nameField.setPromptText("VD: DEADLINE, team1...");
        nameField.setPrefHeight(42);
        nameField.setStyle("-fx-background-color:#F8FAFC; -fx-border-color:" + BORDER
                + "; -fx-border-radius:8; -fx-background-radius:8; -fx-padding:0 14; -fx-font-size:13.5px;");

        Label membersLbl = new Label("Chọn thành viên");
        membersLbl.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:12.5px; -fx-font-weight:bold;");

        VBox membersBox = new VBox(8);
        membersBox.setPadding(new Insets(12));
        membersBox.setStyle("-fx-background-color:#F8FAFC; -fx-border-color:" + BORDER
                + "; -fx-border-radius:8; -fx-background-radius:8;");

        Map<String, CheckBox> checkboxes = new HashMap<>();
        for (String u : userList.getItems()) {
            if (u.equals(myUsername)) continue;
            CheckBox cb = new CheckBox(u);
            cb.setStyle("-fx-font-size:13px;");
            checkboxes.put(u, cb);
            membersBox.getChildren().add(cb);
        }

        ScrollPane scroll = new ScrollPane(membersBox);
        scroll.setPrefHeight(200);
        scroll.setFitToWidth(true);

        VBox content = new VBox(12, nameLbl, nameField, membersLbl, scroll);
        content.setPadding(new Insets(24));
        content.setPrefWidth(420);

        dialog.getDialogPane().setContent(content);
        ButtonType createType = new ButtonType("Tạo nhóm", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType,
                new ButtonType("Huỷ", ButtonBar.ButtonData.CANCEL_CLOSE));

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == createType) {
            String gname = nameField.getText().trim();
            if (gname.isEmpty()) {
                addSystemMessage("⚠ Chưa nhập tên nhóm!");
                return;
            }

            try { core.joinMulticast(); } catch (Exception ignored) {}
            myGroup = gname;
            core.sendOnly("JOIN_GROUP|" + myUsername + "|" + gname);

            int count = 1;
            for (Map.Entry<String, CheckBox> e : checkboxes.entrySet()) {
                if (e.getValue().isSelected()) {
                    core.sendOnly("JOIN_GROUP|" + e.getKey() + "|" + gname);
                    count++;
                }
            }

            groupLabel.setText("📌 Đang ở nhóm: " + gname);
            groupLabel.setStyle("-fx-text-fill:" + GREEN + "; -fx-font-size:11.5px; -fx-font-weight:bold; "
                    + "-fx-background-color:" + GREEN_BG + "; -fx-padding:8 14; -fx-background-radius:8;");

            joinBtn.setDisable(true);
            leaveBtn.setDisable(false);

            if (!groupList.getItems().contains(gname)) {
                groupList.getItems().add(gname);
            }
            updateGroupCount();

            final String gf = gname;
            final int countFinal = count;
            new Thread(() -> {
                try { Thread.sleep(300); } catch (Exception ignored) {}
                Platform.runLater(() -> {
                    groupList.getSelectionModel().select(gf);
                    addSystemMessage("✅ Đã tạo nhóm " + gf + " với " + countFinal + " thành viên");
                });
            }).start();
        }
    }

    private void doJoinGroup() {
        String gname = groupNameField.getText().trim();
        if (gname.isEmpty()) {
            String selected = groupList.getSelectionModel().getSelectedItem();
            if (selected != null) gname = selected;
        }
        if (gname.isEmpty()) {
            addSystemMessage("⚠ Nhập tên nhóm hoặc chọn nhóm!");
            return;
        }
        if (myGroup != null) {
            addSystemMessage("⚠ Bạn đang ở nhóm '" + myGroup + "'. Hãy LEAVE trước!");
            return;
        }

        try { core.joinMulticast(); } catch (Exception e) {
            addSystemMessage("❌ Lỗi join multicast: " + e.getMessage());
        }

        myGroup = gname;
        core.sendOnly("JOIN_GROUP|" + myUsername + "|" + gname);

        groupLabel.setText("📌 Đang ở nhóm: " + gname);
        groupLabel.setStyle("-fx-text-fill:" + GREEN + "; -fx-font-size:11.5px; -fx-font-weight:bold; "
                + "-fx-background-color:" + GREEN_BG + "; -fx-padding:8 14; -fx-background-radius:8;");

        joinBtn.setDisable(true);
        leaveBtn.setDisable(false);

        if (!groupList.getItems().contains(gname)) {
            groupList.getItems().add(gname);
        }
        updateGroupCount();

        groupList.getSelectionModel().select(gname);
        selectGroup(gname);
        addSystemMessage("✅ Bạn đã tham gia nhóm: " + gname);
    }

    private void doLeaveGroup() {
        if (myGroup == null) {
            addSystemMessage("⚠ Bạn chưa ở nhóm nào!");
            return;
        }

        String leftGroup = myGroup;
        core.sendOnly("LEAVE_GROUP|" + myUsername + "|" + leftGroup);
        try { core.leaveMulticast(); } catch (Exception ignored) {}

        myGroup = null;
        groupLabel.setText("Chưa ở nhóm nào");
        groupLabel.setStyle("-fx-text-fill:" + ORANGE + "; -fx-font-size:11.5px; -fx-font-weight:bold; "
                + "-fx-background-color:" + ORANGE_BG + "; -fx-padding:8 14; -fx-background-radius:8;");

        joinBtn.setDisable(false);
        leaveBtn.setDisable(true);

        if ("GROUP".equals(currentChatType) && leftGroup.equals(currentChatTarget)) {
            selectBroadcast();
        }

        addSystemMessage("➖ Bạn đã rời nhóm: " + leftGroup);
    }

    // ═════════════════════════════════════════════════════════════════
    //           CHAT AREA — Typing label GẦN ô nhập tin
    // ═════════════════════════════════════════════════════════════════
    private VBox buildChatArea() {
        VBox box = new VBox(0);
        box.setStyle("-fx-background-color:" + BG_APP + ";");

        chatHeaderAvatarLbl = new Label("🌐");
        chatHeaderAvatarLbl.setStyle("-fx-text-fill:white; -fx-font-size:17px; -fx-font-weight:bold; "
                + "-fx-background-color:" + BLUE + "; "
                + "-fx-min-width:46; -fx-min-height:46; -fx-max-width:46; -fx-max-height:46; "
                + "-fx-alignment:center; -fx-background-radius:23;");

        chatHeaderNameLbl = new Label("Tất cả");
        chatHeaderNameLbl.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:15px; -fx-font-weight:bold;");

        chatHeaderSubLbl = new Label("Mọi người đều nhận tin nhắn");
        chatHeaderSubLbl.setStyle("-fx-text-fill:" + TEXT_GRAY + "; -fx-font-size:12px;");

        VBox info = new VBox(2, chatHeaderNameLbl, chatHeaderSubLbl);
        info.setAlignment(Pos.CENTER_LEFT);

        HBox headerBar = new HBox(14, chatHeaderAvatarLbl, info);
        headerBar.setAlignment(Pos.CENTER_LEFT);
        headerBar.setPadding(new Insets(16, 24, 16, 24));
        headerBar.setStyle("-fx-background-color:" + BG_WHITE + "; -fx-border-color:" + BORDER
                + "; -fx-border-width:0 0 1 0;");

        // Message area (không có typing label nữa)
        messagesBox = new VBox(8);
        messagesBox.setPadding(new Insets(20, 24, 20, 24));
        messagesBox.setStyle("-fx-background-color:" + BG_APP + ";");

        chatScroll = new ScrollPane(messagesBox);
        chatScroll.setFitToWidth(true);
        chatScroll.setStyle("-fx-background-color:transparent; -fx-background:transparent; "
                + "-fx-border-color:transparent;");
        chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        box.getChildren().addAll(headerBar, chatScroll);

        // ⭐ TYPING LABEL — ĐẶT GẦN Ô NHẬP TIN (góc trái)
        typingLabel = new Label("");
        typingLabel.setStyle("-fx-text-fill:" + BLUE + "; -fx-font-size:12px; -fx-font-style:italic; "
                + "-fx-padding:0 24 6 24; -fx-font-weight:bold;");
        typingLabel.setMinHeight(20);

        // Input bar
        Button emojiBtn = new Button("😊");
        emojiBtn.setPrefHeight(48);
        emojiBtn.setPrefWidth(48);
        emojiBtn.setStyle("-fx-background-color:" + BG_WHITE + "; -fx-border-color:" + BORDER
                + "; -fx-border-radius:24; -fx-background-radius:24; -fx-cursor:hand; -fx-font-size:20px;");
        emojiBtn.setOnAction(e -> showEmojiPicker(emojiBtn));

        msgField = new TextField();
        msgField.setPromptText("Nhập tin nhắn...");
        msgField.setPrefHeight(48);
        msgField.setStyle("-fx-background-color:" + BG_WHITE + "; -fx-border-color:" + BORDER
                + "; -fx-border-radius:24; -fx-background-radius:24; "
                + "-fx-padding:0 22; -fx-font-size:13.5px;");
        HBox.setHgrow(msgField, Priority.ALWAYS);
        msgField.setOnAction(e -> doSend());

        msgField.textProperty().addListener((obs, o, n) -> {
            if (n == null || n.isEmpty()) return;
            long now = System.currentTimeMillis();
            if (now - lastTypingSent > 1500) {
                lastTypingSent = now;
                sendTyping();
            }
        });

        Button sendBtn = new Button();
        FontIcon sendIc = new FontIcon(FontAwesomeSolid.PAPER_PLANE);
        sendIc.setIconSize(14);
        sendIc.setIconColor(Color.WHITE);
        Label sendLbl = new Label("Gửi");
        sendLbl.setGraphic(sendIc);
        sendLbl.setGraphicTextGap(10);
        sendLbl.setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:13px;");
        sendBtn.setGraphic(sendLbl);
        sendBtn.setPrefHeight(48);
        sendBtn.setPrefWidth(110);
        sendBtn.setStyle("-fx-background-color:" + BLUE + "; "
                + "-fx-background-radius:24; -fx-cursor:hand; -fx-font-size:13px;");
        sendBtn.setOnAction(e -> doSend());

        HBox inputBar = new HBox(10, emojiBtn, msgField, sendBtn);
        inputBar.setPadding(new Insets(0, 24, 16, 24));
        inputBar.setAlignment(Pos.CENTER_LEFT);

        // ⭐ Box bao gồm typing + input bar — ở dưới cùng
        VBox bottomBox = new VBox(0, typingLabel, inputBar);
        bottomBox.setStyle("-fx-background-color:" + BG_WHITE + "; "
                + "-fx-border-color:" + BORDER + "; -fx-border-width:1 0 0 0;");
        bottomBox.setPadding(new Insets(10, 0, 0, 0));

        box.getChildren().add(bottomBox);
        return box;
    }

    private void showEmojiPicker(Button anchor) {
        String[] emojis = {
            "😀","😂","😍","🥰","😎","🤔","😢","😡","❤️","👍",
            "🎉","🔥","✨","💯","👏","🙏","😴","🤣","😇","🥳",
            "😘","🤗","🤩","😜","🤪","😳","🥺","😭","😤","🤯",
            "🌹","🎁","☕","🍕","⚽","🎵","💡","📌","⭐","🌈"
        };

        Stage popup = new Stage();
        popup.initOwner(primaryStage);
        popup.initStyle(javafx.stage.StageStyle.TRANSPARENT);

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setPadding(new Insets(12));
        grid.setStyle("-fx-background-color:white; -fx-background-radius:14; "
                + "-fx-border-color:" + BORDER + "; -fx-border-radius:14; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 16, 0, 0, 4);");

        int col = 0, row = 0;
        for (String em : emojis) {
            Button b = new Button(em);
            b.setStyle("-fx-background-color:transparent; -fx-cursor:hand; -fx-font-size:22px; -fx-padding:4 6;");
            b.setOnAction(e -> {
                msgField.appendText(em);
                msgField.requestFocus();
                popup.close();
            });
            grid.add(b, col, row);
            col++;
            if (col >= 8) { col = 0; row++; }
        }

        Scene sc = new Scene(grid);
        sc.setFill(Color.TRANSPARENT);
        popup.setScene(sc);

        javafx.geometry.Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.setX(bounds.getMinX() - 100);
        popup.setY(bounds.getMinY() - 280);
        popup.show();
        popup.focusedProperty().addListener((obs, o, n) -> { if (!n) popup.close(); });
    }

    private class UserCell extends ListCell<String> {
        @Override protected void updateItem(String u, boolean empty) {
            super.updateItem(u, empty);
            if (empty || u == null) { setText(null); setGraphic(null); return; }

            String av = u.substring(0, 1).toUpperCase();
            String color = pickColor(u);

            Label avatar = new Label(av);
            avatar.setStyle("-fx-text-fill:white; -fx-font-size:14px; -fx-font-weight:bold; "
                    + "-fx-background-color:" + color + "; "
                    + "-fx-min-width:42; -fx-min-height:42; -fx-max-width:42; -fx-max-height:42; "
                    + "-fx-alignment:center; -fx-background-radius:21;");

            Label name = new Label(u + (u.equals(myUsername) ? " (bạn)" : ""));
            name.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:13.5px; -fx-font-weight:bold;");

            Label sub = new Label(u.equals(myUsername) ? "Đang hoạt động" : "● Online");
            sub.setStyle("-fx-text-fill:" + (u.equals(myUsername) ? ORANGE : GREEN) + "; -fx-font-size:11px;");

            VBox textBox = new VBox(2, name, sub);

            HBox h = new HBox(12, avatar, textBox);
            h.setAlignment(Pos.CENTER_LEFT);
            h.setPadding(new Insets(8, 16, 8, 16));

            setGraphic(h);
            setText(null);
        }
    }

    private class GroupCell extends ListCell<String> {
        @Override protected void updateItem(String g, boolean empty) {
            super.updateItem(g, empty);
            if (empty || g == null) { setText(null); setGraphic(null); return; }

            Label avatar = new Label();
            FontIcon fi = new FontIcon(FontAwesomeSolid.USERS);
            fi.setIconSize(16);
            fi.setIconColor(Color.web(ORANGE));
            avatar.setGraphic(fi);
            avatar.setStyle("-fx-background-color:" + ORANGE_BG + "; "
                    + "-fx-min-width:42; -fx-min-height:42; -fx-max-width:42; -fx-max-height:42; "
                    + "-fx-alignment:center; -fx-background-radius:21;");

            Label name = new Label(g);
            name.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:13.5px; -fx-font-weight:bold;");

            Label sub = new Label(g.equals(myGroup) ? "● Đang ở nhóm này" : "Nhấn để vào");
            sub.setStyle("-fx-text-fill:" + (g.equals(myGroup) ? GREEN : TEXT_GRAY) + "; -fx-font-size:11px;");

            VBox textBox = new VBox(2, name, sub);

            HBox h = new HBox(12, avatar, textBox);
            h.setAlignment(Pos.CENTER_LEFT);
            h.setPadding(new Insets(8, 16, 8, 16));

            setGraphic(h);
            setText(null);
        }
    }

    private void selectBroadcast() {
        currentChatType = "BROADCAST";
        currentChatTarget = null;
        currentChatKey = "broadcast";
        updateHeader("Tất cả", "Mọi người đều nhận tin nhắn", "🌐", "Tất cả");
        msgField.setPromptText("Nhắn cho tất cả...");
        userList.getSelectionModel().clearSelection();
        groupList.getSelectionModel().clearSelection();
        renderCurrentChat();
        msgField.requestFocus();
    }

    private void selectUnicast(String username) {
        currentChatType = "UNICAST";
        currentChatTarget = username;
        currentChatKey = "user:" + username;
        updateHeader(username, "● Online", username.substring(0, 1).toUpperCase(), username);
        msgField.setPromptText("Nhắn cho " + username + "...");
        groupList.getSelectionModel().clearSelection();
        renderCurrentChat();
        msgField.requestFocus();
    }

    private void selectGroup(String group) {
        currentChatType = "GROUP";
        currentChatTarget = group;
        currentChatKey = "group:" + group;
        updateHeader("Nhóm " + group, "Chat nhóm (chỉ thành viên nhận)", "👥", "Nhóm " + group);
        msgField.setPromptText("Nhắn vào nhóm " + group + "...");
        userList.getSelectionModel().clearSelection();
        renderCurrentChat();
        msgField.requestFocus();
    }

    private void updateHeader(String name, String sub, String avatarTxt, String colorSeed) {
        chatHeaderAvatarLbl.setText(avatarTxt);
        chatHeaderAvatarLbl.setStyle("-fx-text-fill:white; -fx-font-size:17px; -fx-font-weight:bold; "
                + "-fx-background-color:" + pickColor(colorSeed) + "; "
                + "-fx-min-width:46; -fx-min-height:46; -fx-max-width:46; -fx-max-height:46; "
                + "-fx-alignment:center; -fx-background-radius:23;");
        chatHeaderNameLbl.setText(name);
        chatHeaderSubLbl.setText(sub);
    }

    private void renderCurrentChat() {
        messagesBox.getChildren().clear();
        List<Message> msgs = messagesByChat.getOrDefault(currentChatKey, new ArrayList<>());
        String lastDate = "";
        for (Message m : msgs) {
            String d = m.time.format(DATE_FMT);
            if (!d.equals(lastDate)) {
                lastDate = d;
                messagesBox.getChildren().add(buildTimeDivider(m.time));
            }
            messagesBox.getChildren().add(buildBubble(m));
        }
        // Xóa typing khi đổi chat
        if (typingLabel != null) typingLabel.setText("");
        scrollToBottom();
    }

    private HBox buildTimeDivider(LocalDateTime time) {
        String label = time.format(DATE_FMT) + " • " + time.format(TIME_FMT);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill:" + TEXT_GRAY + "; -fx-font-size:11px; -fx-font-weight:bold; "
                + "-fx-background-color:" + BG_WHITE + "; -fx-padding:6 18; -fx-background-radius:20; "
                + "-fx-border-color:" + BORDER + "; -fx-border-radius:20;");

        HBox wrap = new HBox(lbl);
        wrap.setAlignment(Pos.CENTER);
        wrap.setPadding(new Insets(12, 0, 8, 0));
        return wrap;
    }

    private HBox buildBubble(Message m) {
        String av = m.from.substring(0, 1).toUpperCase();
        Label avatar = new Label(av);
        avatar.setStyle("-fx-text-fill:white; -fx-font-size:13px; -fx-font-weight:bold; "
                + "-fx-background-color:" + (m.isMine ? BLUE : pickColor(m.from)) + "; "
                + "-fx-min-width:40; -fx-min-height:40; -fx-max-width:40; -fx-max-height:40; "
                + "-fx-alignment:center; -fx-background-radius:20;");

        Label nameLbl = new Label(m.from);
        nameLbl.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:12px; -fx-font-weight:bold;");

        Label timeLbl = new Label(m.time.format(TIME_FMT));
        timeLbl.setStyle("-fx-text-fill:" + TEXT_GRAY + "; -fx-font-size:10.5px;");

        HBox nameRow = new HBox(6, nameLbl, timeLbl);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        Label msgLbl = new Label(m.text);
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(460);
        msgLbl.setStyle("-fx-text-fill:" + (m.isMine ? "white" : TEXT_DARK) + "; -fx-font-size:13.5px;");

        StackPane bubble = new StackPane(msgLbl);
        bubble.setPadding(new Insets(11, 16, 11, 16));
        if (m.isMine) {
            bubble.setStyle("-fx-background-color:" + BLUE + "; -fx-background-radius:16 16 4 16;");
        } else {
            bubble.setStyle("-fx-background-color:" + BORDER_SOFT + "; -fx-background-radius:16 16 16 4;");
        }
        bubble.setMaxWidth(480);

        VBox bubbleBox = new VBox(nameRow, bubble);
        bubbleBox.setSpacing(4);
        bubbleBox.setAlignment(m.isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Button reactBtn = new Button("😊");
        reactBtn.setStyle("-fx-background-color:white; -fx-border-color:" + BORDER + "; "
                + "-fx-border-radius:20; -fx-background-radius:20; -fx-cursor:hand; "
                + "-fx-font-size:11px; -fx-padding:2 8; -fx-opacity:0;");
        reactBtn.setOnAction(e -> showReactionPicker(bubbleBox, bubble, m.isMine));

        ContextMenu menu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(m.text);
            Clipboard.getSystemClipboard().setContent(cc);
        });
        MenuItem replyItem = new MenuItem("Trả lời");
        replyItem.setOnAction(e -> {
            msgField.setText("↩️ " + m.from + ": " + m.text + " | ");
            msgField.requestFocus();
        });
        menu.getItems().addAll(copyItem, replyItem);
        bubble.setOnContextMenuRequested(e -> menu.show(bubble, e.getScreenX(), e.getScreenY()));

        HBox bubbleWithReact = new HBox(6);
        if (m.isMine) bubbleWithReact.getChildren().addAll(reactBtn, bubbleBox);
        else          bubbleWithReact.getChildren().addAll(bubbleBox, reactBtn);
        bubbleWithReact.setAlignment(m.isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        bubbleWithReact.setOnMouseEntered(e -> reactBtn.setOpacity(1));
        bubbleWithReact.setOnMouseExited(e -> {
            if (bubble.getUserData() == null) reactBtn.setOpacity(0);
        });

        HBox row = new HBox(10);
        if (m.isMine) row.getChildren().addAll(bubbleWithReact, avatar);
        else          row.getChildren().addAll(avatar, bubbleWithReact);
        row.setAlignment(m.isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 0, 3, 0));
        return row;
    }

    private void showReactionPicker(VBox bubbleBox, StackPane bubble, boolean isMine) {
        String[] emojis = {"❤️", "😆", "😮", "😢", "👍", "😡"};
        HBox row = new HBox(4);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-background-color:white; -fx-background-radius:24; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 12, 0, 0, 3);");

        Stage popup = new Stage();
        popup.initOwner(primaryStage);
        popup.initStyle(javafx.stage.StageStyle.TRANSPARENT);

        for (String em : emojis) {
            Button b = new Button(em);
            b.setStyle("-fx-background-color:transparent; -fx-cursor:hand; -fx-font-size:20px; -fx-padding:2 4;");
            b.setOnAction(ev -> {
                Label reaction = new Label(em);
                reaction.setStyle("-fx-font-size:16px; -fx-background-color:white; "
                        + "-fx-padding:1 6; -fx-background-radius:12; "
                        + "-fx-border-color:" + BORDER + "; -fx-border-radius:12;");
                Node parent = bubbleBox.getParent();
                if (parent instanceof HBox) {
                    HBox hbox = (HBox) parent;
                    hbox.getChildren().add(1, reaction);
                }
                bubble.setUserData("reacted");
                popup.close();
            });
            row.getChildren().add(b);
        }

        Scene sc = new Scene(row);
        sc.setFill(Color.TRANSPARENT);
        popup.setScene(sc);

        javafx.geometry.Bounds bounds = bubble.localToScreen(bubble.getBoundsInLocal());
        popup.setX(bounds.getMinX() + bounds.getWidth() / 2 - 110);
        popup.setY(bounds.getMinY() - 55);
        popup.show();
        popup.focusedProperty().addListener((obs, o, n) -> { if (!n) popup.close(); });
    }

    private void doSend() {
        String msg = msgField.getText().trim();
        if (msg.isEmpty()) return;

        if ("UNICAST".equals(currentChatType)) {
            if (currentChatTarget == null) return;
            core.sendOnly("UNICAST|" + myUsername + "|" + currentChatTarget + "|" + msg);
            addMessage(currentChatKey, new Message(myUsername, msg, true, LocalDateTime.now()));
        } else if ("GROUP".equals(currentChatType)) {
            if (myGroup == null) { addSystemMessage("⚠ Bạn chưa vào nhóm!"); return; }
            core.sendOnly("GROUP_MSG|" + myUsername + "|" + myGroup + "|" + msg);
            addMessage(currentChatKey, new Message(myUsername, msg, true, LocalDateTime.now()));
        } else {
            core.sendOnly("BROADCAST|" + myUsername + "|" + msg);
            addMessage("broadcast", new Message(myUsername, msg, true, LocalDateTime.now()));
        }

        msgField.clear();
        msgField.requestFocus();
    }

    private void addMessage(String key, Message m) {
        messagesByChat.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        if (currentChatKey.equals(key)) {
            int idx = messagesBox.getChildren().size();

            List<Message> msgs = messagesByChat.get(key);
            String lastDate = "";
            if (msgs.size() > 1) {
                Message prev = msgs.get(msgs.size() - 2);
                lastDate = prev.time.format(DATE_FMT);
            }
            String curDate = m.time.format(DATE_FMT);
            if (!curDate.equals(lastDate)) {
                messagesBox.getChildren().add(idx, buildTimeDivider(m.time));
                idx++;
            }

            messagesBox.getChildren().add(idx, buildBubble(m));
            scrollToBottom();
        }
    }

    private void addSystemMessage(String text) {
        Message m = new Message("Hệ thống", text, false, LocalDateTime.now());
        messagesByChat.computeIfAbsent(currentChatKey, k -> new ArrayList<>()).add(m);

        int idx = messagesBox.getChildren().size();

        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill:" + TEXT_GRAY + "; -fx-font-size:11.5px; "
                + "-fx-background-color:rgba(0,0,0,0.04); -fx-padding:6 16; -fx-background-radius:20;");
        HBox wrap = new HBox(lbl);
        wrap.setAlignment(Pos.CENTER);
        wrap.setPadding(new Insets(5, 0, 5, 0));

        messagesBox.getChildren().add(idx, wrap);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    private void sendTyping() {
        if ("BROADCAST".equals(currentChatType)) return;
        String target;
        if ("GROUP".equals(currentChatType)) target = "GROUP:" + currentChatTarget;
        else target = currentChatTarget;
        if (target == null) return;
        core.sendOnly("TYPING|" + myUsername + "|" + target);
    }

    // ⭐ Typing hiện GẦN ô nhập tin
    private void showTypingIndicator(String from, String target) {
        String key;
        if (target.startsWith("GROUP:")) {
            key = "group:" + target.substring(6);
        } else {
            key = "user:" + target;
        }

        if (!key.equals(currentChatKey)) return;

        typingLabel.setText("✏️ " + from + " đang soạn tin nhắn...");

        final String fromFinal = from;
        new Thread(() -> {
            try { Thread.sleep(2500); } catch (Exception ignored) {}
            Platform.runLater(() -> {
                if (typingLabel.getText().startsWith("✏️ " + fromFinal + " ")) {
                    typingLabel.setText("");
                }
            });
        }).start();
    }

    private void startListening() {
        listenThread = new Thread(() -> {
            try {
                byte[] buf = new byte[65535];
                while (running) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    core.getSocket().receive(p);
                    String resp = new String(p.getData(), 0, p.getLength(), "UTF-8").trim();
                    Platform.runLater(() -> handleServerMessage(resp));
                }
            } catch (Exception ignored) {}
        }, "UDP-Listen");
        listenThread.setDaemon(true);
        listenThread.start();
    }

    private void startMulticastListening() {
        mcastThread = new Thread(() -> {
            try {
                byte[] buf = new byte[65535];
                while (running) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    core.getMcSocket().receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(), "UTF-8").trim();
                    Platform.runLater(() -> handleMulticastMessage(msg));
                }
            } catch (Exception ignored) {}
        }, "Multicast-Listen");
        mcastThread.setDaemon(true);
        mcastThread.start();
    }

    private void startPingThread() {
        pingThread = new Thread(() -> {
            try {
                while (running) {
                    Thread.sleep(3000);
                    if (core != null && myUsername != null) {
                        core.sendOnly("LIST_USERS");
                    }
                }
            } catch (Exception ignored) {}
        }, "Ping-Thread");
        pingThread.setDaemon(true);
        pingThread.start();
    }

    private void handleMulticastMessage(String msg) {
        String[] p = msg.split("\\|", -1);
        if (p.length >= 5 && p[0].equals("MCAST")) {
            String groupId = p[1];
            String from    = p[2];
            String text    = p[4];
            if (!from.equals(myUsername)) {
                addMessage("group:" + groupId, new Message(from, text, false, LocalDateTime.now()));
            }
        }
    }

    private void handleServerMessage(String msg) {
        String[] p = msg.split("\\|", -1);
        if (p.length == 0) return;

        switch (p[0]) {
            case "MSG":
                switch (p[1]) {
                    case "UNICAST":
                        addMessage("user:" + p[2], new Message(p[2], p[4], false, LocalDateTime.now()));
                        break;
                    case "BROADCAST":
                        addMessage("broadcast", new Message(p[2], p[4], false, LocalDateTime.now()));
                        break;
                }
                break;
            case "SYS":
                addSystemMessage(p[1]);
                break;
            case "ERROR":
                addSystemMessage("❌ " + p[1]);
                break;
            case "USERLIST":
                updateUserList(p.length >= 2 ? p[1] : "");
                break;
            case "GROUPLIST":
                updateGroupList(p.length >= 2 ? p[1] : "");
                break;
            case "TYPING":
                if (p.length >= 3) showTypingIndicator(p[1], p[2]);
                break;
        }
    }

    private void updateUserList(String csv) {
        String selected = userList.getSelectionModel().getSelectedItem();
        userList.getItems().clear();
        if (!csv.isEmpty()) {
            for (String u : csv.split(",")) {
                if (!u.isEmpty()) userList.getItems().add(u);
            }
        }
        if (selected != null && userList.getItems().contains(selected)) {
            userList.getSelectionModel().select(selected);
        }
        Node n = sidebar.lookup("#userCount");
        if (n instanceof Label) ((Label) n).setText(String.valueOf(userList.getItems().size()));
    }

    private void updateGroupList(String csv) {
        String selected = groupList.getSelectionModel().getSelectedItem();
        Set<String> groups = new HashSet<>();
        if (!csv.isEmpty()) {
            for (String g : csv.split(";")) {
                if (g.isEmpty()) continue;
                String name = g.split(":")[0];
                if (!name.isEmpty()) groups.add(name);
            }
        }
        for (String g : groups) {
            if (!groupList.getItems().contains(g)) {
                groupList.getItems().add(g);
            }
        }
        if (selected != null && groupList.getItems().contains(selected)) {
            groupList.getSelectionModel().select(selected);
        }
        updateGroupCount();
    }

    private void updateGroupCount() {
        Node n = sidebar.lookup("#groupCount");
        if (n instanceof Label) ((Label) n).setText(String.valueOf(groupList.getItems().size()));
    }

    private String pickColor(String name) {
        String[] colors = {BLUE, ORANGE, CYAN, GREEN, PINK, PURPLE};
        return colors[Math.abs(name.hashCode()) % colors.length];
    }

    private String detectMyIP() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                String name = ni.getName().toLowerCase();
                if (name.contains("vbox") || name.contains("vmnet")
                    || name.contains("bluetooth") || name.contains("virtual")) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                    String ip = a.getHostAddress();
                    if (ip.contains(".") && !ip.startsWith("169.254") && !ip.startsWith("192.168.56.")) return ip;
                }
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    @Override
    public void stop() {
        running = false;
        if (core != null) {
            try { core.sendOnly("LOGOUT|" + myUsername); } catch (Exception ignored) {}
            core.close();
        }
    }

    public static void main(String[] args) { launch(args); }
}