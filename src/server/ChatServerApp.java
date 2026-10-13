package server;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;

public class ChatServerApp extends Application {

    // ═══════════ THEME ═══════════
    private static final String BG_APP      = "#F1F5F9";
    private static final String BG_WHITE    = "#FFFFFF";
    private static final String BG_HEADER   = "#1E40AF";
    private static final String BORDER      = "#E2E8F0";
    private static final String BORDER_SOFT = "#F1F5F9";
    private static final String TEXT_DARK   = "#0F172A";
    private static final String TEXT_GRAY   = "#64748B";
    private static final String TEXT_DIM    = "#94A3B8";
    private static final String GREEN       = "#10B981";
    private static final String GREEN_BG    = "#ECFDF5";
    private static final String RED         = "#EF4444";
    private static final String RED_BG      = "#FEF2F2";
    private static final String BLUE        = "#2563EB";
    private static final String BLUE_BG     = "#EFF6FF";
    private static final String CYAN        = "#0891B2";
    private static final String CYAN_BG     = "#ECFEFF";
    private static final String ORANGE      = "#EA580C";
    private static final String ORANGE_BG   = "#FFF7ED";
    private static final String PURPLE      = "#7C3AED";

    private ChatServerCore core;
    private Label lbClients, lbGroups, lbUptime;
    private Label lbStatusText, lbIpValue, lbPortValue, lbMcValue;
    private Button startBtn, stopBtn;
    private TextField portField;
    private Circle statusDot;
    private long startTime = 0;

    private final ObservableList<ClientRow> clientData = FXCollections.observableArrayList();
    private final ObservableList<GroupRow>  groupData  = FXCollections.observableArrayList();
    private final ObservableList<LogRow>    logData    = FXCollections.observableArrayList();
    private TableView<ClientRow> clientTable;
    private TableView<GroupRow>  groupTable;
    private TableView<LogRow>    logTable;
    private Label lbLogCount;

    private Timeline refresher;
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat FULL_FMT = new SimpleDateFormat("dd/MM HH:mm:ss");

    public static class ClientRow {
        private final String username, ip, port, connectTime, status;
        public ClientRow(String u, String i, String p, String ct, String st) {
            username = u; ip = i; port = p; connectTime = ct; status = st;
        }
        public String getUsername()  { return username; }
        public String getIp()        { return ip; }
        public String getPort()      { return port; }
        public String getConnectTime(){ return connectTime; }
        public String getStatus()    { return status; }
    }

    public static class GroupRow {
        private final String groupId, members, count;
        public GroupRow(String g, String m, String c) { groupId = g; members = m; count = c; }
        public String getGroupId() { return groupId; }
        public String getMembers() { return members; }
        public String getCount()   { return count; }
    }

    public static class LogRow {
        private final String time, event, info, eventColor;
        private final FontAwesomeSolid icon;
        public LogRow(String t, String e, String i, String color, FontAwesomeSolid ic) {
            time = t; event = e; info = i; eventColor = color; icon = ic;
        }
        public String getTime()           { return time; }
        public String getEvent()          { return event; }
        public String getInfo()           { return info; }
        public String getEventColor()     { return eventColor; }
        public FontAwesomeSolid getIcon() { return icon; }
    }

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + BG_APP + ";");
        root.setTop(buildHeader());
        root.setCenter(buildContent());

        Scene scene = new Scene(root, 1450, 900);
        stage.setTitle("Multicast Chat — Server");
        stage.setScene(scene);
        stage.show();

        refresher = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            refreshData();
            updateUptime();
        }));
        refresher.setCycleCount(Timeline.INDEFINITE);
        refresher.play();
    }

    // ═════════════════════════════════════════════════════════════════
    //           HEADER
    // ═════════════════════════════════════════════════════════════════
    private VBox buildHeader() {
        // Row 1: Logo + title + status
        FontIcon logoIc = new FontIcon(FontAwesomeSolid.BROADCAST_TOWER);
        logoIc.setIconSize(22);
        logoIc.setIconColor(Color.WHITE);
        StackPane logoBox = new StackPane(logoIc);
        logoBox.setStyle("-fx-background-color:rgba(255,255,255,0.2); -fx-background-radius:10;");
        logoBox.setMinSize(44, 44);
        logoBox.setMaxSize(44, 44);

        Label title = new Label("Multicast Chat Server");
        title.setStyle("-fx-text-fill:white; -fx-font-size:18px; -fx-font-weight:bold;");
        Label sub = new Label("Quản lý & Giám sát — UDP Multicast");
        sub.setStyle("-fx-text-fill:rgba(255,255,255,0.8); -fx-font-size:11.5px;");
        VBox titleBox = new VBox(2, title, sub);

        HBox leftBox = new HBox(12, logoBox, titleBox);
        leftBox.setAlignment(Pos.CENTER_LEFT);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        statusDot = new Circle(5);
        statusDot.setFill(Color.web(ORANGE));
        lbStatusText = new Label("Chưa khởi động");
        lbStatusText.setStyle("-fx-text-fill:white; -fx-font-size:12.5px; -fx-font-weight:bold;");
        HBox statusBox = new HBox(8, statusDot, lbStatusText);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setPadding(new Insets(7, 16, 7, 16));
        statusBox.setStyle("-fx-background-color:rgba(255,255,255,0.18); -fx-background-radius:20;");

        HBox row1 = new HBox(16, leftBox, sp, statusBox);
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.setPadding(new Insets(18, 32, 12, 32));

        // Row 2: Info + Controls (all in one line)
        Label ipLbl = new Label("IP:");
        ipLbl.setStyle("-fx-text-fill:rgba(255,255,255,0.7); -fx-font-size:11.5px;");
        lbIpValue = new Label("—");
        lbIpValue.setStyle("-fx-text-fill:white; -fx-font-size:13px; -fx-font-weight:bold;");

        Label portLbl = new Label("Cổng:");
        portLbl.setStyle("-fx-text-fill:rgba(255,255,255,0.7); -fx-font-size:11.5px;");
        lbPortValue = new Label("2024");
        lbPortValue.setStyle("-fx-text-fill:white; -fx-font-size:13px; -fx-font-weight:bold;");

        Label mcLbl = new Label("Multicast:");
        mcLbl.setStyle("-fx-text-fill:rgba(255,255,255,0.7); -fx-font-size:11.5px;");
        lbMcValue = new Label(ChatServerCore.MULTICAST_ADDR + ":" + ChatServerCore.MULTICAST_PORT);
        lbMcValue.setStyle("-fx-text-fill:white; -fx-font-size:13px; -fx-font-weight:bold;");

        portField = new TextField("2024");
        portField.setPrefWidth(80);
        portField.setPrefHeight(36);
        portField.setStyle("-fx-background-color:rgba(255,255,255,0.18); -fx-text-fill:white; "
                + "-fx-background-radius:8; -fx-border-color:rgba(255,255,255,0.3); "
                + "-fx-border-radius:8; -fx-padding:0 12; -fx-font-size:13px;");

        startBtn = new Button();
        FontIcon playIc = new FontIcon(FontAwesomeSolid.PLAY);
        playIc.setIconSize(11);
        playIc.setIconColor(Color.WHITE);
        Label startLbl = new Label("Khởi động");
        startLbl.setGraphic(playIc);
        startLbl.setGraphicTextGap(8);
        startLbl.setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:13px;");
        startBtn.setGraphic(startLbl);
        startBtn.setPrefHeight(36);
        startBtn.setStyle("-fx-background-color:" + GREEN + "; -fx-background-radius:8; "
                + "-fx-cursor:hand; -fx-padding:0 18;");
        startBtn.setOnAction(e -> doStart());

        stopBtn = new Button();
        FontIcon stopIc = new FontIcon(FontAwesomeSolid.STOP_CIRCLE);
        stopIc.setIconSize(11);
        stopIc.setIconColor(Color.WHITE);
        Label stopLbl = new Label("Dừng");
        stopLbl.setGraphic(stopIc);
        stopLbl.setGraphicTextGap(8);
        stopLbl.setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:13px;");
        stopBtn.setGraphic(stopLbl);
        stopBtn.setPrefHeight(36);
        stopBtn.setDisable(true);
        stopBtn.setStyle("-fx-background-color:" + RED + "; -fx-background-radius:8; "
                + "-fx-cursor:hand; -fx-padding:0 18;");
        stopBtn.setOnAction(e -> doStop());

        HBox row2 = new HBox(14,
                ipLbl, lbIpValue, divider(),
                portLbl, lbPortValue, divider(),
                mcLbl, lbMcValue,
                sp, portField, startBtn, stopBtn);
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.setPadding(new Insets(0, 32, 18, 32));

        VBox header = new VBox(0, row1, row2);
        header.setStyle("-fx-background-color:" + BG_HEADER + ";");
        return header;
    }

    private Region divider() {
        Region r = new Region();
        r.setPrefWidth(1);
        r.setPrefHeight(16);
        r.setStyle("-fx-background-color:rgba(255,255,255,0.3);");
        return r;
    }

    // ═════════════════════════════════════════════════════════════════
    //           CONTENT — TẤT CẢ TRÊN 1 MÀN HÌNH
    // ═════════════════════════════════════════════════════════════════
    private VBox buildContent() {
        // 3 metric cards
        lbClients = new Label("0");
        lbGroups  = new Label("0");
        lbUptime  = new Label("00:00:00");

        HBox metrics = new HBox(16,
                makeMetricCard(FontAwesomeSolid.USERS, "Client Online", lbClients, BLUE, BLUE_BG),
                makeMetricCard(FontAwesomeSolid.LAYER_GROUP, "Nhóm hoạt động", lbGroups, ORANGE, ORANGE_BG),
                makeMetricCard(FontAwesomeSolid.CLOCK, "Uptime", lbUptime, GREEN, GREEN_BG)
        );

        // Middle: Clients (left) + Groups (right)
        VBox clientsCard = buildClientsCard();
        VBox groupsCard = buildGroupsCard();

        HBox middleRow = new HBox(16, clientsCard, groupsCard);
        HBox.setHgrow(clientsCard, Priority.ALWAYS);
        HBox.setHgrow(groupsCard, Priority.ALWAYS);

        // Bottom: Log
        VBox logCard = buildLogCard();

        VBox content = new VBox(16, metrics, middleRow, logCard);
        content.setPadding(new Insets(20, 32, 24, 32));
        VBox.setVgrow(middleRow, Priority.ALWAYS);
        VBox.setVgrow(logCard, Priority.ALWAYS);
        return content;
    }

    private VBox makeMetricCard(FontAwesomeSolid ic, String title, Label valueLbl,
                                 String color, String bgColor) {
        FontIcon fi = new FontIcon(ic);
        fi.setIconSize(20);
        fi.setIconColor(Color.web(color));
        StackPane iconBox = new StackPane(fi);
        iconBox.setStyle("-fx-background-color:" + bgColor + "; -fx-background-radius:10;");
        iconBox.setMinSize(48, 48);
        iconBox.setMaxSize(48, 48);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill:" + TEXT_GRAY + "; -fx-font-size:12px; -fx-font-weight:bold;");
        valueLbl.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:26px; -fx-font-weight:bold;");

        VBox textBox = new VBox(2, titleLbl, valueLbl);

        HBox row = new HBox(14, iconBox, textBox);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(row);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color:" + BG_WHITE + "; -fx-background-radius:12; "
                + "-fx-border-color:" + BORDER + "; -fx-border-radius:12;");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox buildClientsCard() {
        FontIcon ic = new FontIcon(FontAwesomeSolid.USERS);
        ic.setIconSize(14);
        ic.setIconColor(Color.web(BLUE));

        Label title = new Label("Client đang online");
        title.setGraphic(ic);
        title.setGraphicTextGap(8);
        title.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:14px; -fx-font-weight:bold;");

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Label badge = new Label("0");
        badge.setId("clientCountBadge");
        badge.setStyle("-fx-text-fill:" + BLUE + "; -fx-font-size:11.5px; -fx-font-weight:bold; "
                + "-fx-background-color:" + BLUE_BG + "; -fx-padding:5 12; -fx-background-radius:8;");

        HBox header = new HBox(8, title, sp, badge);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));

        clientTable = new TableView<>();
        clientTable.setStyle("-fx-background-color:transparent; -fx-font-size:13px; "
                + "-fx-border-color:transparent;");
        clientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        clientTable.setPlaceholder(new Label("Chưa có client nào online"));
        clientTable.setFixedCellSize(54);
        VBox.setVgrow(clientTable, Priority.ALWAYS);

        TableColumn<ClientRow, String> cUser = new TableColumn<>("Người dùng");
        cUser.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("username"));
        cUser.setPrefWidth(160);
        cUser.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setGraphic(null); }
                else {
                    String av = s.substring(0, 1).toUpperCase();
                    Label avatar = new Label(av);
                    avatar.setStyle("-fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:bold; "
                            + "-fx-background-color:" + pickColor(s) + "; "
                            + "-fx-min-width:32; -fx-min-height:32; -fx-max-width:32; -fx-max-height:32; "
                            + "-fx-alignment:center; -fx-background-radius:16;");
                    Label l = new Label(s);
                    l.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:13px; -fx-font-weight:bold;");
                    HBox h = new HBox(10, avatar, l);
                    h.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(h); setText(null);
                }
            }
        });

        TableColumn<ClientRow, String> cIp = new TableColumn<>("IP");
        cIp.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("ip"));
        cIp.setPrefWidth(140);

        TableColumn<ClientRow, String> cPort = new TableColumn<>("Port");
        cPort.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("port"));
        cPort.setPrefWidth(80);

        TableColumn<ClientRow, String> cTime = new TableColumn<>("Kết nối");
        cTime.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("connectTime"));
        cTime.setPrefWidth(130);

        TableColumn<ClientRow, String> cSt = new TableColumn<>("Trạng thái");
        cSt.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("status"));
        cSt.setPrefWidth(110);
        cSt.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setGraphic(null); }
                else {
                    Label dot = new Label("●");
                    dot.setStyle("-fx-text-fill:" + GREEN + "; -fx-font-size:11px;");
                    Label lbl = new Label(s);
                    lbl.setStyle("-fx-text-fill:" + GREEN + "; -fx-font-size:11.5px; -fx-font-weight:bold;");
                    HBox h = new HBox(5, dot, lbl);
                    h.setAlignment(Pos.CENTER_LEFT);
                    h.setPadding(new Insets(4, 10, 4, 10));
                    h.setStyle("-fx-background-color:" + GREEN_BG + "; -fx-background-radius:6;");
                    setGraphic(h); setText(null);
                }
            }
        });

        clientTable.getColumns().addAll(cUser, cIp, cPort, cTime, cSt);
        clientTable.setItems(clientData);

        VBox card = new VBox(header, clientTable);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color:" + BG_WHITE + "; -fx-background-radius:12; "
                + "-fx-border-color:" + BORDER + "; -fx-border-radius:12;");
        VBox.setVgrow(clientTable, Priority.ALWAYS);
        return card;
    }

    private VBox buildGroupsCard() {
        FontIcon ic = new FontIcon(FontAwesomeSolid.LAYER_GROUP);
        ic.setIconSize(14);
        ic.setIconColor(Color.web(ORANGE));

        Label title = new Label("Nhóm chat");
        title.setGraphic(ic);
        title.setGraphicTextGap(8);
        title.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:14px; -fx-font-weight:bold;");

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Label badge = new Label("0");
        badge.setId("groupCountBadge");
        badge.setStyle("-fx-text-fill:" + ORANGE + "; -fx-font-size:11.5px; -fx-font-weight:bold; "
                + "-fx-background-color:" + ORANGE_BG + "; -fx-padding:5 12; -fx-background-radius:8;");

        HBox header = new HBox(8, title, sp, badge);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));

        groupTable = new TableView<>();
        groupTable.setStyle("-fx-background-color:transparent; -fx-font-size:13px; "
                + "-fx-border-color:transparent;");
        groupTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        groupTable.setPlaceholder(new Label("Chưa có nhóm nào"));
        groupTable.setFixedCellSize(54);
        VBox.setVgrow(groupTable, Priority.ALWAYS);

        TableColumn<GroupRow, String> cId = new TableColumn<>("Tên nhóm");
        cId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("groupId"));
        cId.setPrefWidth(140);
        cId.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setGraphic(null); }
                else {
                    Label iconBox = new Label();
                    FontIcon fi = new FontIcon(FontAwesomeSolid.USERS);
                    fi.setIconSize(12);
                    fi.setIconColor(Color.web(ORANGE));
                    iconBox.setGraphic(fi);
                    iconBox.setStyle("-fx-background-color:" + ORANGE_BG + "; "
                            + "-fx-background-radius:16; -fx-padding:8;");
                    iconBox.setMinSize(32, 32);
                    iconBox.setMaxSize(32, 32);
                    iconBox.setAlignment(Pos.CENTER);
                    Label l = new Label(s);
                    l.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:13px; -fx-font-weight:bold;");
                    HBox h = new HBox(10, iconBox, l);
                    h.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(h); setText(null);
                }
            }
        });

        TableColumn<GroupRow, String> cMem = new TableColumn<>("Thành viên");
        cMem.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("members"));
        cMem.setPrefWidth(300);

        TableColumn<GroupRow, String> cCnt = new TableColumn<>("SL");
        cCnt.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("count"));
        cCnt.setPrefWidth(70);
        cCnt.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setGraphic(null); }
                else {
                    Label lbl = new Label(s);
                    lbl.setStyle("-fx-text-fill:" + ORANGE + "; -fx-font-size:11.5px; -fx-font-weight:bold;");
                    HBox h = new HBox(lbl);
                    h.setAlignment(Pos.CENTER_LEFT);
                    h.setPadding(new Insets(4, 10, 4, 10));
                    h.setStyle("-fx-background-color:" + ORANGE_BG + "; -fx-background-radius:6;");
                    setGraphic(h); setText(null);
                }
            }
        });

        groupTable.getColumns().addAll(cId, cMem, cCnt);
        groupTable.setItems(groupData);

        VBox card = new VBox(header, groupTable);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color:" + BG_WHITE + "; -fx-background-radius:12; "
                + "-fx-border-color:" + BORDER + "; -fx-border-radius:12;");
        VBox.setVgrow(groupTable, Priority.ALWAYS);
        return card;
    }

    private VBox buildLogCard() {
        FontIcon ic = new FontIcon(FontAwesomeSolid.SCROLL);
        ic.setIconSize(14);
        ic.setIconColor(Color.web(BLUE));

        Label title = new Label("Nhật ký hoạt động");
        title.setGraphic(ic);
        title.setGraphicTextGap(8);
        title.setStyle("-fx-text-fill:" + TEXT_DARK + "; -fx-font-size:14px; -fx-font-weight:bold;");

        lbLogCount = new Label("0 sự kiện");
        lbLogCount.setStyle("-fx-text-fill:" + TEXT_GRAY + "; -fx-font-size:11px; -fx-font-weight:bold; "
                + "-fx-background-color:#F1F5F9; -fx-padding:5 12; -fx-background-radius:8;");

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button clearBtn = new Button();
        FontIcon clearIc = new FontIcon(FontAwesomeSolid.TRASH_ALT);
        clearIc.setIconSize(11);
        clearIc.setIconColor(Color.web(RED));
        Label clearLbl = new Label("Xóa log");
        clearLbl.setGraphic(clearIc);
        clearLbl.setGraphicTextGap(8);
        clearLbl.setStyle("-fx-text-fill:" + RED + "; -fx-font-size:12px; -fx-font-weight:bold;");
        clearBtn.setGraphic(clearLbl);
        clearBtn.setStyle("-fx-background-color:transparent; -fx-cursor:hand; "
                + "-fx-border-color:" + BORDER + "; -fx-border-radius:8; "
                + "-fx-background-radius:8; -fx-padding:6 14;");
        clearBtn.setOnAction(e -> { logData.clear(); updateLogCount(); });

        HBox header = new HBox(10, title, lbLogCount, sp, clearBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));

        logTable = new TableView<>();
        logTable.setStyle("-fx-background-color:transparent; -fx-font-size:12.5px; "
                + "-fx-border-color:transparent;");
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        logTable.setPlaceholder(new Label("Chưa có hoạt động nào"));
        logTable.setFixedCellSize(38);
        VBox.setVgrow(logTable, Priority.ALWAYS);

        TableColumn<LogRow, String> cTime = new TableColumn<>("Thời gian");
        cTime.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("time"));
        cTime.setPrefWidth(120);

        TableColumn<LogRow, String> cEvent = new TableColumn<>("Sự kiện");
        cEvent.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("event"));
        cEvent.setPrefWidth(140);
        cEvent.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setGraphic(null); }
                else {
                    LogRow row = getTableView().getItems().get(getIndex());
                    String color = row.getEventColor();
                    FontAwesomeSolid ico = row.getIcon();
                    FontIcon fi = new FontIcon(ico != null ? ico : FontAwesomeSolid.INFO_CIRCLE);
                    fi.setIconSize(10);
                    fi.setIconColor(Color.web(color));
                    Label lbl = new Label(s);
                    lbl.setStyle("-fx-text-fill:" + color + "; -fx-font-size:11px; -fx-font-weight:bold;");
                    HBox h = new HBox(6, fi, lbl);
                    h.setAlignment(Pos.CENTER_LEFT);
                    h.setPadding(new Insets(3, 10, 3, 10));
                    h.setStyle("-fx-background-color:" + color + "15; -fx-background-radius:6;");
                    setGraphic(h); setText(null);
                }
            }
        });

        TableColumn<LogRow, String> cInfo = new TableColumn<>("Thông tin");
        cInfo.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("info"));
        cInfo.setPrefWidth(900);

        logTable.getColumns().addAll(cTime, cEvent, cInfo);
        logTable.setItems(logData);

        VBox card = new VBox(header, logTable);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color:" + BG_WHITE + "; -fx-background-radius:12; "
                + "-fx-border-color:" + BORDER + "; -fx-border-radius:12;");
        VBox.setVgrow(logTable, Priority.ALWAYS);
        return card;
    }

    private void updateLogCount() {
        if (lbLogCount != null) lbLogCount.setText(logData.size() + " sự kiện");
    }

    // ═════════════════════════════════════════════════════════════════
    //           ACTIONS
    // ═════════════════════════════════════════════════════════════════
    private void doStart() {
        int port;
        try { port = Integer.parseInt(portField.getText().trim()); }
        catch (Exception e) {
            addLog("ERROR", "Cổng không hợp lệ", RED, FontAwesomeSolid.EXCLAMATION_TRIANGLE);
            return;
        }

        core = new ChatServerCore(this::log);
        core.start(port);
        startTime = System.currentTimeMillis();

        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        portField.setDisable(true);

        statusDot.setFill(Color.web(GREEN));
        lbStatusText.setText("Đang hoạt động");
        lbIpValue.setText(getLocalIP());
        lbPortValue.setText(String.valueOf(port));

        addLog("SERVER", "Server khởi động cổng " + port, GREEN, FontAwesomeSolid.PLAY);
        addLog("SERVER", "IP: " + getLocalIP(), BLUE, FontAwesomeSolid.GLOBE);
        addLog("MULTICAST", ChatServerCore.MULTICAST_ADDR + ":" + ChatServerCore.MULTICAST_PORT,
                CYAN, FontAwesomeSolid.BROADCAST_TOWER);

        refreshData();
    }

    private void doStop() {
        if (core != null) core.stop();
        startBtn.setDisable(false);
        stopBtn.setDisable(true);
        portField.setDisable(false);
        statusDot.setFill(Color.web(ORANGE));
        lbStatusText.setText("Chưa khởi động");
        addLog("SERVER", "Server đã dừng", RED, FontAwesomeSolid.STOP_CIRCLE);
    }

    private String getLocalIP() {
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
                    InetAddress addr = addrs.nextElement();
                    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) continue;
                    String ip = addr.getHostAddress();
                    if (ip.contains(".") && !ip.startsWith("169.254")
                        && !ip.startsWith("192.168.56.") && !ip.startsWith("127.")) {
                        return ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "localhost";
    }

    private void refreshData() {
        if (core == null) return;
        Platform.runLater(() -> {
            clientData.clear();
            Map<String, ChatServerCore.ClientInfo> clients = core.getOnlineClients();
            for (Map.Entry<String, ChatServerCore.ClientInfo> e : clients.entrySet()) {
                ChatServerCore.ClientInfo ci = e.getValue();
                String timeStr = FULL_FMT.format(new Date(ci.connectedAt));
                clientData.add(new ClientRow(ci.username,
                        ci.ip.getHostAddress(),
                        String.valueOf(ci.port),
                        timeStr,
                        "Online"));
            }
            lbClients.setText(String.valueOf(clients.size()));

            Node cb = clientTable.lookup("#clientCountBadge");
            if (cb instanceof Label) ((Label) cb).setText(String.valueOf(clients.size()));

            groupData.clear();
            Map<String, Set<String>> groups = core.getGroups();
            for (Map.Entry<String, Set<String>> e : groups.entrySet()) {
                Set<String> members = e.getValue();
                String memberList = String.join(", ", members);
                groupData.add(new GroupRow(e.getKey(),
                        memberList.isEmpty() ? "(trống)" : memberList,
                        String.valueOf(members.size())));
            }
            lbGroups.setText(String.valueOf(groups.size()));

            Node gb = groupTable.lookup("#groupCountBadge");
            if (gb instanceof Label) ((Label) gb).setText(String.valueOf(groups.size()));
        });
    }

    private void updateUptime() {
        if (startTime == 0 || lbUptime == null) return;
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        long h = elapsed / 3600;
        long m = (elapsed % 3600) / 60;
        long s = elapsed % 60;
        lbUptime.setText(String.format("%02d:%02d:%02d", h, m, s));
    }

    private void log(String msg) {
        String color = BLUE;
        FontAwesomeSolid icon = FontAwesomeSolid.INFO_CIRCLE;
        String event = "INFO";

        if (msg.startsWith("[OK]"))          { color = GREEN;  icon = FontAwesomeSolid.CHECK_CIRCLE;      event = "OK"; }
        else if (msg.startsWith("[ERR]"))    { color = RED;    icon = FontAwesomeSolid.EXCLAMATION_TRIANGLE; event = "ERROR"; }
        else if (msg.startsWith("[ONLINE]")) { color = GREEN;  icon = FontAwesomeSolid.USER_PLUS;         event = "ONLINE"; }
        else if (msg.startsWith("[OFFLINE]")){ color = RED;    icon = FontAwesomeSolid.USER_MINUS;        event = "OFFLINE"; }
        else if (msg.startsWith("[UNICAST]")){ color = BLUE;   icon = FontAwesomeSolid.USER;              event = "UNICAST"; }
        else if (msg.startsWith("[BROADCAST]")){ color = ORANGE; icon = FontAwesomeSolid.BULLHORN;        event = "BROADCAST"; }
        else if (msg.startsWith("[JOIN]"))   { color = ORANGE; icon = FontAwesomeSolid.USER_PLUS;         event = "JOIN"; }
        else if (msg.startsWith("[LEAVE]"))  { color = RED;    icon = FontAwesomeSolid.USER_MINUS;        event = "LEAVE"; }
        else if (msg.startsWith("[MCAST"))   { color = CYAN;   icon = FontAwesomeSolid.BROADCAST_TOWER;   event = "MULTICAST"; }
        else if (msg.startsWith("[DISCOVERY]")){ color = CYAN; icon = FontAwesomeSolid.SATELLITE_DISH;    event = "DISCOVERY"; }

        addLog(event, msg, color, icon);
    }

    private void addLog(String event, String info, String color, FontAwesomeSolid icon) {
        String t = TIME_FMT.format(new Date());
        LogRow row = new LogRow(t, event, info, color, icon);
        Platform.runLater(() -> {
            logData.add(0, row);
            if (logData.size() > 500) logData.remove(logData.size() - 1);
            updateLogCount();
        });
    }

    private String pickColor(String name) {
        String[] colors = {BLUE, ORANGE, CYAN, GREEN, PURPLE, "#DB2777"};
        return colors[Math.abs(name.hashCode()) % colors.length];
    }

    @Override
    public void stop() {
        if (core != null) core.stop();
        if (refresher != null) refresher.stop();
    }

    public static void main(String[] args) { launch(args); }
}