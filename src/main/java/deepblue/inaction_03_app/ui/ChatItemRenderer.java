package deepblue.inaction_03_app.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;

/** 把 ChatItem 画成聊天气泡：自己的靠右（蓝底白字），别人的靠左（白底），系统 / 错误提示居中。 */
class ChatItemRenderer implements ListCellRenderer<ChatItem> {

    static final Color BACKGROUND = new Color(0xF3F6FA);
    private static final Color MINE_BG = new Color(0x1765D1);
    private static final Color OTHER_BG = Color.WHITE;
    private static final Color OTHER_BORDER = new Color(0xD9E3ED);
    private static final Color MUTED = new Color(0x5C6F84);
    private static final Color ERROR = new Color(0xC0392B);

    @Override
    public Component getListCellRendererComponent(JList<? extends ChatItem> list, ChatItem item, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        if (item.kind() == ChatItem.Kind.SYSTEM || item.kind() == ChatItem.Kind.ERROR) {
            JLabel label = new JLabel(html(item.text(), wrapWidth(list, item.text(), list.getFont())), SwingConstants.CENTER);
            label.setForeground(item.kind() == ChatItem.Kind.ERROR ? ERROR : MUTED);
            label.setFont(list.getFont().deriveFont(12f));
            label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            row.setBackground(BACKGROUND);
            row.add(label);
            return row;
        }

        boolean mine = item.kind() == ChatItem.Kind.MINE;
        JLabel header = new JLabel(item.header());
        header.setFont(list.getFont().deriveFont(11f));
        header.setForeground(MUTED);
        header.setAlignmentX(mine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        JLabel bubble = new JLabel(html(item.text(), wrapWidth(list, item.text(), list.getFont())));
        bubble.setOpaque(true);
        bubble.setBackground(mine ? MINE_BG : OTHER_BG);
        bubble.setForeground(mine ? Color.WHITE : Color.BLACK);
        bubble.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(mine ? MINE_BG : OTHER_BORDER, 1, true),
                BorderFactory.createEmptyBorder(7, 11, 7, 11)));
        bubble.setAlignmentX(mine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(BACKGROUND);
        box.add(header);
        box.add(bubble);

        JPanel row = new JPanel(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 14, 6));
        row.setBackground(BACKGROUND);
        row.add(box);
        return row;
    }

    /** 文字超过列表宽度的 60% 才折行，短消息的气泡按文字实际宽度显示。 */
    private static int wrapWidth(JList<?> list, String text, Font font) {
        int max = Math.max(160, (int) (list.getWidth() * 0.6));
        FontMetrics fm = list.getFontMetrics(font);
        int longest = 0;
        for (String line : text.split("\n")) {
            longest = Math.max(longest, fm.stringWidth(line));
        }
        return longest > max ? max : -1;
    }

    private static String html(String text, int width) {
        String body = escape(text).replace("\n", "<br>");
        return width > 0
                ? "<html><body style='width:" + width + "px'>" + body + "</body></html>"
                : "<html>" + body + "</html>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
