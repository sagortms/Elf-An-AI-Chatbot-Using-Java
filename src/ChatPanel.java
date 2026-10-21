import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ChatPanel extends JPanel {
    private final User user;
    private final ChatBotLogic logic;
    private final ChatLog log;

    private final JPanel messages = new JPanel();
    private final JScrollPane scroll;
    private final JTextField input = new JTextField();
    private final JButton send = new JButton("Send");
    private final JLabel nameLabel;

    private final List<String> sentBefore = new ArrayList<>();
    private int historyPos = 0;
    private String lastDay = "";
    private boolean busy = false;

    // follows the width of the viewport so long messages wrap
    private static class ScrollPanel extends JPanel implements Scrollable {
        ScrollPanel(JComponent inner) {
            setLayout(new BorderLayout());
            setBackground(Theme.bg);
            add(inner, BorderLayout.NORTH);
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 20;
        }

        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return r.height - 40;
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // rounded message bubble
    private static class Bubble extends JPanel {
        private final Color fill;

        Bubble(String message, long time, boolean fromUser, boolean h24) {
            fill = fromUser ? Theme.userBubble : Theme.botBubble;
            Color fg = fromUser ? Theme.userBubbleText : Theme.botBubbleText;
            setOpaque(false);
            setLayout(new BorderLayout(0, 3));
            setBorder(new EmptyBorder(9, 13, 6, 13));

            Font f = Theme.font(Font.PLAIN, 0);
            int maxWidth = Theme.fontSize * 30;
            FontMetrics fm = getFontMetrics(f);
            int w = 0;
            for (String line : message.split("\n")) w = Math.max(w, fm.stringWidth(line));
            w = Math.min(w + 12, maxWidth);

            String html = "<html><div style='width:" + Theme.cssWidth(w) + "px'>"
                    + TextUtil.htmlEscape(message).replace("\n", "<br>") + "</div></html>";
            JLabel body = new JLabel(html);
            body.setFont(f);
            body.setForeground(fg);
            add(body, BorderLayout.CENTER);

            JLabel stamp = new JLabel(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a").format(new Date(time)));
            stamp.setFont(Theme.font(Font.PLAIN, -4));
            stamp.setForeground(fg);
            stamp.setHorizontalAlignment(SwingConstants.RIGHT);
            add(stamp, BorderLayout.SOUTH);
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public ChatPanel(User user, ChatBotLogic logic, ChatLog log) {
        this.user = user;
        this.logic = logic;
        this.log = log;
        setLayout(new BorderLayout());
        setBackground(Theme.bg);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.panel);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border), new EmptyBorder(12, 20, 12, 20)));
        nameLabel = Theme.title(user.settings.botName());
        header.add(nameLabel, BorderLayout.WEST);
        header.add(Theme.muted("Ask for help to see what I can do"), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
        messages.setBackground(Theme.bg);
        scroll = new JScrollPane(new ScrollPanel(messages));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.bg);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        add(buildBottom(), BorderLayout.SOUTH);

        showHistory();
        showWelcome();
    }

    private JComponent buildBottom() {
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setBackground(Theme.panel);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border), new EmptyBorder(10, 16, 12, 16)));

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chips.setOpaque(false);
        for (final String q : logic.quickReplies()) {
            JButton chip = new JButton(q);
            Theme.styleButton(chip, false);
            chip.setFont(Theme.font(Font.PLAIN, -2));
            chip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.border), new EmptyBorder(3, 10, 3, 10)));
            chip.addActionListener(e -> submit(q));
            chips.add(chip);
        }
        bottom.add(chips, BorderLayout.NORTH);

        Theme.styleField(input);
        Theme.styleButton(send, true);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(input, BorderLayout.CENTER);
        row.add(send, BorderLayout.EAST);
        bottom.add(row, BorderLayout.CENTER);

        send.addActionListener(e -> submit(input.getText()));
        input.addActionListener(e -> submit(input.getText()));

        // up/down arrows bring back old messages
        input.getInputMap().put(KeyStroke.getKeyStroke("UP"), "prevMessage");
        input.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "nextMessage");
        input.getActionMap().put("prevMessage", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (sentBefore.isEmpty()) return;
                historyPos = Math.max(0, historyPos - 1);
                input.setText(sentBefore.get(historyPos));
            }
        });
        input.getActionMap().put("nextMessage", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (sentBefore.isEmpty()) return;
                historyPos = Math.min(sentBefore.size(), historyPos + 1);
                input.setText(historyPos == sentBefore.size() ? "" : sentBefore.get(historyPos));
            }
        });
        return bottom;
    }

    // lets dashboard and help send a message to the chat
    public void ask(String text) {
        submit(text);
    }

    public void focusInput() {
        input.requestFocusInWindow();
    }

    public void refreshBotName() {
        nameLabel.setText(user.settings.botName());
    }

    private void submit(String raw) {
        String text = raw.trim();
        if (text.isEmpty() || busy) return;
        input.setText("");
        sentBefore.add(text);
        historyPos = sentBefore.size();

        addBubble(text, true, System.currentTimeMillis());
        log.add(true, logic.logSafe(text));

        final String reply = logic.reply(text);
        final boolean secret = logic.lastReplyWasSecret();
        showReplyLater(reply, secret);
    }

    private void showReplyLater(final String reply, final boolean secret) {
        busy = true;
        send.setEnabled(false);
        final JComponent typing = user.settings.typingDelay() ? addTypingRow() : null;
        int delay = typing == null ? 0 : Math.min(300 + reply.length() * 8, 1200);
        Timer t = new Timer(Math.max(delay, 1), e -> {
            if (typing != null) messages.remove(typing);
            addBubble(reply, false, System.currentTimeMillis());
            log.add(false, secret ? "(saved vault details hidden)" : reply);
            busy = false;
            send.setEnabled(true);
            input.requestFocusInWindow();
        });
        t.setRepeats(false);
        t.start();
    }

    public void botSay(String text) {
        addBubble(text, false, System.currentTimeMillis());
        log.add(false, text);
    }

    private void showHistory() {
        for (ChatLog.Entry e : log.load(60)) addBubble(e.text, e.fromUser, e.time);
    }

    private void showWelcome() {
        addBubble(logic.welcome(), false, System.currentTimeMillis());
    }

    public void clearView() {
        messages.removeAll();
        lastDay = "";
        messages.revalidate();
        messages.repaint();
        showWelcome();
    }

    private void addBubble(String text, boolean fromUser, long time) {
        // date line whenever the day changes
        String day = new SimpleDateFormat("EEE, d MMM yyyy").format(new Date(time));
        if (!day.equals(lastDay)) {
            lastDay = day;
            JLabel d = Theme.muted(day);
            d.setHorizontalAlignment(SwingConstants.CENTER);
            d.setBorder(new EmptyBorder(8, 0, 4, 0));
            JPanel dp = new JPanel(new BorderLayout());
            dp.setOpaque(false);
            dp.add(d, BorderLayout.CENTER);
            messages.add(dp);
        }
        Bubble b = new Bubble(text, time, fromUser, user.settings.use24Hour());
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(3, 16, 3, 16));
        row.add(b, fromUser ? BorderLayout.EAST : BorderLayout.WEST);
        messages.add(row);
        messages.revalidate();
        scrollToBottom();
    }

    private JComponent addTypingRow() {
        JLabel l = Theme.muted(user.settings.botName() + " is typing...");
        l.setFont(Theme.font(Font.ITALIC, -2));
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(3, 20, 3, 16));
        row.add(l, BorderLayout.WEST);
        messages.add(row);
        messages.revalidate();
        scrollToBottom();
        return row;
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }
}
