package deepblue.inaction_03_app.ui;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_03_app.client.ClientListener;
import deepblue.inaction_03_app.client.NettyChatClient;
import deepblue.inaction_03_app.model.AppMessage;
import deepblue.inaction_03_app.model.AppMessageType;
import io.netty.channel.ChannelFuture;
import lombok.extern.slf4j.Slf4j;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;

/**
 * 桌面聊天 App（Swing），用 Netty TCP 客户端连接 inaction_03 的 AppNettyServer（默认 127.0.0.1:9100）。
 *
 * 启动：先启动 NettyAdvancedApplication（会同时启动 9000 和 9100 两个 Netty 服务端），
 * 再运行本类 main（可多开几个，用不同的用户名登录）。程序参数可选：userId [host] [port]，不填则弹出登录框。
 * 在线列表由服务端实时推送：登录后收到 ONLINE_LIST，之后有人上线 / 下线收到 USER_ONLINE / USER_OFFLINE。
 *
 * 线程是本例的重点：
 * - Netty 回调（ClientListener）运行在 EventLoop 线程 → 用 SwingUtilities.invokeLater 切回界面线程再更新界面；
 * - 点 "发送" 在界面线程调用 client.send → Netty 自动把写操作交给 EventLoop，写完成的回调又在 EventLoop 上 → 再切回界面线程。
 */
@Slf4j
public final class MineNettyApp extends JFrame implements ClientListener {

    private static final Color GREEN = new Color(0x087F67);
    private static final Color ORANGE = new Color(0xB45A0B);
    private static final Color RED = new Color(0xC0392B);

    private final String userId;
    private final NettyChatClient client;

    private final DefaultListModel<String> onlineModel = new DefaultListModel<>();
    private final JList<String> onlineList = new JList<>(onlineModel);
    private final JLabel onlineTitle = new JLabel();
    private final DefaultListModel<ChatItem> chatModel = new DefaultListModel<>();
    private final JList<ChatItem> chatList = new JList<>(chatModel);
    private final JTextField targetField = new JTextField();
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("发送");
    private final JLabel statusLabel = new JLabel();

    private MineNettyApp(String userId, String host, int port) {
        super("Netty Chat · " + userId);
        this.userId = userId;
        this.client = new NettyChatClient(userId, host, port, this);
        buildUi();
        setStatus("● 连接中 " + host + ":" + port + " ...", ORANGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setupLookAndFeel();
            String[] login = args.length > 0 ? fromArgs(args) : showLoginDialog();
            if (login == null) {
                return;
            }
            MineNettyApp app = new MineNettyApp(login[0], login[1], Integer.parseInt(login[2]));
            log.info("MineNettyApp main invoke, userId={}, client={}", login[0], app.client);
            app.setVisible(true);
            app.client.start();
        });
    }

    // ---------------- Netty 回调：运行在 EventLoop 线程，统一切回界面线程 ----------------

    @Override
    public void onConnected() {
        SwingUtilities.invokeLater(() -> setStatus("● 已连接，登录中 ...", ORANGE));
    }

    @Override
    public void onMessage(AppMessage message) {
        if (message.getType() == AppMessageType.PONG) {
            return;
        }
        // ① 不能在 EventLoop 线程直接操作 Swing 组件，交给界面线程（EDT）执行。
        SwingUtilities.invokeLater(() -> showMessage(message));
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            setStatus("● 连接已断开", RED);
            setSendEnabled(false);
            // 断线期间的在线状态不可信，清空；重连登录后服务端会重新下发 ONLINE_LIST。
            onlineModel.clear();
            updateOnlineTitle();
            addChat(ChatItem.error("与服务器的连接断开了"));
        });
    }

    @Override
    public void onReconnecting(int delaySeconds) {
        SwingUtilities.invokeLater(() -> setStatus("● 重连中，" + delaySeconds + " 秒后重试 ...", ORANGE));
    }

    @Override
    public void onKicked() {
        SwingUtilities.invokeLater(() -> {
            setStatus("● 已被踢下线，不再重连", RED);
            setSendEnabled(false);
        });
    }

    // ---------------- 以下都在界面线程执行 ----------------

    private void showMessage(AppMessage message) {
        switch (message.getType()) {
            case LOGIN_OK -> {
                setStatus("● 已登录：" + userId + "（" + client.getHost() + ":" + client.getPort() + "）", GREEN);
                setSendEnabled(true);
                addChat(ChatItem.system("登录成功：" + message.getContent()));
            }
            // 合并而不是覆盖：并发登录时 USER_ONLINE 可能比 ONLINE_LIST 先到，覆盖会把刚上线的人丢掉。
            case ONLINE_LIST -> JSON.parseArray(message.getContent(), String.class).forEach(this::addOnlineUser);
            case USER_ONLINE -> {
                addOnlineUser(message.getContent());
                addChat(ChatItem.system(message.getContent() + " 上线了"));
            }
            case USER_OFFLINE -> {
                onlineModel.removeElement(message.getContent());
                updateOnlineTitle();
                addChat(ChatItem.system(message.getContent() + " 下线了"));
            }
            case CHAT -> addChat(ChatItem.other(message.getFrom(), message.getContent()));
            case ERROR -> addChat(ChatItem.error("[错误] " + message.getContent()));
            case KICKED -> addChat(ChatItem.error("[被踢下线] " + message.getContent()));
            default -> addChat(ChatItem.system("[" + message.getType() + "] " + message.getContent()));
        }
    }

    private void send() {
        String to = targetField.getText().trim();
        String content = inputField.getText().trim();
        if (to.isEmpty()) {
            addChat(ChatItem.error("请先在左侧选择聊天对象，或在 \"发给\" 中输入对方用户名"));
            return;
        }
        if (content.isEmpty()) {
            return;
        }
        // 在界面线程调用 writeAndFlush 是安全的：Netty 发现不是 EventLoop 线程，会把写操作封装成任务交给 EventLoop。
        ChannelFuture future = client.send(new AppMessage(AppMessageType.CHAT, userId, to, content));
        if (future == null) {
            addChat(ChatItem.error("未连接，消息没有发出"));
            return;
        }
        inputField.setText("");
        // ⑤ 写完成的回调运行在 EventLoop 线程，再切回界面线程显示结果；界面线程里不要 sync() 等待。
        future.addListener(f -> SwingUtilities.invokeLater(() -> {
            if (f.isSuccess()) {
                addChat(ChatItem.mine(to, content));
            } else {
                addChat(ChatItem.error("发送失败：" + f.cause()));
            }
        }));
    }

    /** 按名字有序插入；已存在或是自己则忽略。 */
    private void addOnlineUser(String user) {
        if (user == null || user.equals(userId) || onlineModel.contains(user)) {
            return;
        }
        int index = 0;
        while (index < onlineModel.size() && onlineModel.get(index).compareTo(user) < 0) {
            index++;
        }
        onlineModel.add(index, user);
        updateOnlineTitle();
    }

    private void updateOnlineTitle() {
        onlineTitle.setText("在线用户（" + onlineModel.size() + "）");
    }

    private void addChat(ChatItem item) {
        chatModel.addElement(item);
        chatList.ensureIndexIsVisible(chatModel.size() - 1);
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    private void setSendEnabled(boolean enabled) {
        sendButton.setEnabled(enabled);
        inputField.setEnabled(enabled);
    }

    // ---------------- 界面搭建 ----------------

    private void buildUi() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 620);
        setMinimumSize(new Dimension(640, 420));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.close();
            }
        });

        // 左侧：在线用户
        onlineTitle.setText("在线用户（0）");
        onlineTitle.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        onlineTitle.setFont(onlineTitle.getFont().deriveFont(Font.BOLD));
        onlineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineList.setFixedCellHeight(34);
        onlineList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, "●  " + value, index, isSelected, cellHasFocus);
                label.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 8));
                if (!isSelected) {
                    label.setForeground(GREEN);
                }
                return label;
            }
        });
        onlineList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && onlineList.getSelectedValue() != null) {
                targetField.setText(onlineList.getSelectedValue());
                inputField.requestFocusInWindow();
            }
        });
        JPanel left = new JPanel(new BorderLayout());
        left.setPreferredSize(new Dimension(200, 0));
        left.add(onlineTitle, BorderLayout.NORTH);
        JScrollPane onlineScroll = new JScrollPane(onlineList);
        onlineScroll.setBorder(BorderFactory.createEmptyBorder());
        left.add(onlineScroll, BorderLayout.CENTER);
        JLabel hint = new JLabel("<html>点用户名选中聊天对象<br>上线 / 下线由服务端实时推送</html>");
        hint.setForeground(Color.GRAY);
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setBorder(BorderFactory.createEmptyBorder(8, 12, 10, 12));
        left.add(hint, BorderLayout.SOUTH);
        left.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xD9E3ED)));

        // 中间：聊天记录
        chatList.setCellRenderer(new ChatItemRenderer());
        chatList.setBackground(ChatItemRenderer.BACKGROUND);
        chatList.setFocusable(false);
        chatList.setSelectionModel(new javax.swing.DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                // 聊天记录不需要选中效果
            }
        });
        JScrollPane chatScroll = new JScrollPane(chatList);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        // 窗口宽度变化时让 JList 重新计算每个气泡的高度（折行宽度跟着变）。
        chatScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                chatList.setFixedCellHeight(10);
                chatList.setFixedCellHeight(-1);
            }
        });

        // 底部：发给 + 输入框 + 发送，最下面是状态栏
        JPanel targetRow = new JPanel(new BorderLayout(8, 0));
        targetRow.add(new JLabel("发给"), BorderLayout.WEST);
        targetRow.add(targetField, BorderLayout.CENTER);
        targetField.setColumns(12);
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendButton, BorderLayout.EAST);
        inputField.addActionListener(e -> send());
        sendButton.addActionListener(e -> send());
        setSendEnabled(false);

        JPanel compose = new JPanel(new BorderLayout(10, 0));
        compose.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        JPanel targetWrap = new JPanel(new BorderLayout());
        targetWrap.add(targetRow, BorderLayout.CENTER);
        targetWrap.setPreferredSize(new Dimension(190, 0));
        compose.add(targetWrap, BorderLayout.WEST);
        compose.add(inputRow, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 12));
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(compose, BorderLayout.CENTER);
        bottom.add(statusLabel, BorderLayout.SOUTH);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD9E3ED)));

        JPanel center = new JPanel(new BorderLayout());
        center.add(chatScroll, BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(left, BorderLayout.WEST);
        getContentPane().add(center, BorderLayout.CENTER);
    }

    private static String[] fromArgs(String[] args) {
        return new String[]{
                args[0],
                args.length > 1 ? args[1] : "127.0.0.1",
                args.length > 2 ? args[2] : "9100"};
    }

    /** 登录框：用户名必填，端口必须是数字；取消返回 null。 */
    private static String[] showLoginDialog() {
        JTextField user = new JTextField();
        JTextField host = new JTextField("127.0.0.1");
        JTextField port = new JTextField("9100");
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("用户名"));
        form.add(user);
        form.add(new JLabel("服务器地址"));
        form.add(host);
        form.add(new JLabel("端口 (AppNettyServer)"));
        form.add(port);
        while (true) {
            int result = JOptionPane.showConfirmDialog(null, form, "登录 Netty Chat",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }
            String error = user.getText().isBlank() ? "请输入用户名"
                    : !port.getText().trim().matches("\\d+") ? "端口必须是数字"
                    : null;
            if (error == null) {
                return new String[]{user.getText().trim(), host.getText().trim(), port.getText().trim()};
            }
            JOptionPane.showMessageDialog(null, error, "登录", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 使用系统外观，并统一换成支持中文的字体。 */
    private static void setupLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            log.warn("MineNettyApp setupLookAndFeel invoke, userId=-, use default look and feel: {}", e.toString());
        }
        List<String> families = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        String family = families.contains("Microsoft YaHei UI") ? "Microsoft YaHei UI" : Font.DIALOG;
        FontUIResource font = new FontUIResource(family, Font.PLAIN, 13);
        UIManager.getDefaults().keySet().stream()
                .filter(key -> UIManager.get(key) instanceof FontUIResource)
                .toList()
                .forEach(key -> UIManager.put(key, font));
    }
}
