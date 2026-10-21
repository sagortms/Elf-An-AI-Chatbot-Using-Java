import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainFrame extends JFrame {
    public static final String PAGE_DASHBOARD = "dashboard", PAGE_CHAT = "chat", PAGE_TASKS = "tasks", PAGE_NOTES = "notes",
            PAGE_VAULT = "vault", PAGE_HELP = "help", PAGE_SETTINGS = "settings", PAGE_ACCOUNT = "account";

    private final User user;
    private final ReminderManager tasks;
    private final MemoryManager memory;
    private final Vault vault;
    private final ChatLog log;
    private final ChatBotLogic logic;

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private final Map<String, JLabel> badges = new HashMap<>();
    private ChatPanel chat;
    private JLabel brandLabel;
    private javax.swing.Timer reminderTimer;
    private String currentPage;

    public MainFrame(User user, String startPage) {
        this(user, startPage, null);
    }

    // vault comes from the old window on rebuild, so a theme change doesnt lock it
    private MainFrame(User user, String startPage, Vault existingVault) {
        this.user = user;
        Theme.apply(user.settings.darkTheme(), user.settings.fontSize());

        tasks = new ReminderManager(user);
        memory = new MemoryManager(user);
        vault = existingVault != null ? existingVault : new Vault(user);
        log = new ChatLog(user);
        logic = new ChatBotLogic(user, tasks, memory, vault);
        logic.setOnBotRenamed(this::refreshBotName);
        logic.setOnThemeRequest(dark -> {
            user.settings.set("theme", dark ? "dark" : "light");
            user.settings.save();
            // small delay so the reply is shown and saved before the window rebuilds
            javax.swing.Timer t = new javax.swing.Timer(1600, e -> rebuild(PAGE_CHAT));
            t.setRepeats(false);
            t.start();
        });

        setTitle(user.settings.botName() + " - AI Assistant");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1040, 700);
        setMinimumSize(new Dimension(860, 600));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildSidebar(), BorderLayout.WEST);

        content.setBackground(Theme.bg);
        chat = new ChatPanel(user, logic, log);
        content.add(new DashboardPanel(user, this, tasks, memory, vault), PAGE_DASHBOARD);
        content.add(chat, PAGE_CHAT);
        content.add(new TasksPanel(user, tasks), PAGE_TASKS);
        content.add(new NotesPanel(memory), PAGE_NOTES);
        content.add(new VaultPanel(vault), PAGE_VAULT);
        content.add(new HelpPanel(this), PAGE_HELP);
        content.add(new SettingsPanel(user, this, log, vault), PAGE_SETTINGS);
        content.add(new AccountPanel(user, this, tasks, memory, log), PAGE_ACCOUNT);
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);

        tasks.addListener(this::updateBadges);
        vault.addListener(this::updateBadges);
        updateBadges();
        showPage(startPage);

        // look for due reminders every 15 sec, and once now for ones missed while closed
        reminderTimer = new javax.swing.Timer(15000, e -> checkReminders());
        reminderTimer.start();
        SwingUtilities.invokeLater(this::checkReminders);
    }

    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.sidebar);
        side.setPreferredSize(new Dimension(Theme.fontSize * 15, 0));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel brand = new JPanel(new BorderLayout());
        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(20, 20, 16, 20));
        brandLabel = Theme.label(user.settings.botName(), Font.BOLD, 8, Color.WHITE);
        brand.add(brandLabel, BorderLayout.CENTER);
        brand.add(Theme.label("AI assistant", Font.PLAIN, -2, Theme.sidebarText), BorderLayout.SOUTH);
        top.add(brand);
        top.add(addNav(PAGE_DASHBOARD, "Dashboard"));
        top.add(addNav(PAGE_CHAT, "Chat"));
        top.add(addNav(PAGE_TASKS, "Tasks"));
        top.add(addNav(PAGE_NOTES, "Notes"));
        top.add(addNav(PAGE_VAULT, "Vault"));
        top.add(addNav(PAGE_HELP, "Help"));
        side.add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(addNav(PAGE_SETTINGS, "Settings"));
        bottom.add(buildUserChip());
        side.add(bottom, BorderLayout.SOUTH);
        return side;
    }

    private NavItem addNav(String page, String label) {
        NavItem item = new NavItem(page, label);
        navItems.put(page, item);
        return item;
    }

    private JComponent buildUserChip() {
        final NavItem chip = new NavItem(PAGE_ACCOUNT, null);
        chip.setLayout(new BorderLayout(10, 0));
        chip.setBorder(new EmptyBorder(12, 16, 12, 16));
        chip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        JLabel avatar = new JLabel(user.initials(), SwingConstants.CENTER) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.accent);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        avatar.setFont(Theme.font(Font.BOLD, -1));
        avatar.setForeground(Color.WHITE);
        avatar.setPreferredSize(new Dimension(36, 36));
        chip.add(avatar, BorderLayout.WEST);

        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(Theme.label(user.fullName, Font.BOLD, -1, Color.WHITE));
        text.add(Theme.label("@" + user.username, Font.PLAIN, -2, Theme.sidebarText));
        chip.add(text, BorderLayout.CENTER);
        navItems.put(PAGE_ACCOUNT, chip);
        return chip;
    }

    // one clickable row of the sidebar
    private class NavItem extends JPanel {
        private final String page;
        private boolean hover;

        NavItem(String page, String text) {
            this.page = page;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (text != null) {
                setLayout(new BorderLayout());
                setBorder(new EmptyBorder(11, 22, 11, 16));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
                add(Theme.label(text, Font.PLAIN, 1, Theme.sidebarText), BorderLayout.CENTER);
                if (page.equals(PAGE_TASKS) || page.equals(PAGE_VAULT)) {
                    JLabel badge = Theme.label("", Font.BOLD, -2, page.equals(PAGE_VAULT) ? Theme.sidebarText : Color.WHITE);
                    badges.put(page, badge);
                    add(badge, BorderLayout.EAST);
                }
            }
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    showPage(NavItem.this.page);
                }

                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        protected void paintComponent(Graphics g) {
            boolean selected = page.equals(currentPage);
            if (selected || hover) {
                g.setColor(selected ? Theme.sidebarSelected : Theme.sidebarHover);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            if (selected) {
                g.setColor(Theme.accent);
                g.fillRect(0, 0, 4, getHeight());
            }
            super.paintComponent(g);
        }
    }

    public void showPage(String page) {
        currentPage = page;
        cards.show(content, page);
        for (NavItem n : navItems.values()) n.repaint();
        if (page.equals(PAGE_CHAT)) chat.focusInput();
    }

    public void sendToChat(String text) {
        showPage(PAGE_CHAT);
        chat.ask(text);
    }

    private void updateBadges() {
        JLabel tasksBadge = badges.get(PAGE_TASKS);
        if (tasksBadge != null) {
            int n = tasks.pendingCount();
            tasksBadge.setText(n == 0 ? "" : String.valueOf(n));
        }
        JLabel vaultBadge = badges.get(PAGE_VAULT);
        if (vaultBadge != null) vaultBadge.setText(vault.isSetUp() ? (vault.isUnlocked() ? "open" : "locked") : "");
    }

    public void refreshBotName() {
        brandLabel.setText(user.settings.botName());
        setTitle(user.settings.botName() + " - AI Assistant");
        chat.refreshBotName();
    }

    public void clearChatView() {
        chat.clearView();
    }

    // used after theme or text size change
    public void rebuild(String page) {
        reminderTimer.stop();
        Rectangle bounds = getBounds();
        MainFrame f = new MainFrame(user, page, vault);
        f.setBounds(bounds);
        f.setVisible(true);
        dispose();
    }

    public void logout() {
        reminderTimer.stop();
        vault.lock();
        new LoginFrame(user.username, getBounds()).setVisible(true);
        dispose();
    }

    private void checkReminders() {
        List<ReminderManager.Reminder> due = tasks.takeDue(System.currentTimeMillis());
        for (ReminderManager.Reminder r : due) {
            String msg = "Reminder: " + r.text;
            chat.botSay(msg);
            if (user.settings.reminderSound()) Toolkit.getDefaultToolkit().beep();
            JOptionPane pane = new JOptionPane(r.text, JOptionPane.INFORMATION_MESSAGE);
            JDialog dialog = pane.createDialog(this, "Reminder");
            dialog.setModal(false);
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
        }
    }
}
