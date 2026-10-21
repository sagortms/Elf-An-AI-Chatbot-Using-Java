import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class AccountPanel extends Page {
    private final User user;
    private final MainFrame main;
    private final ReminderManager tasks;
    private final MemoryManager memory;
    private final ChatLog log;

    private final JLabel nameLabel = Theme.label(" ", Font.BOLD, 6, Theme.text);
    private final JLabel messagesStat = new JLabel();
    private final JLabel tasksStat = new JLabel();
    private final JLabel remindersStat = new JLabel();
    private final JLabel notesStat = new JLabel();

    private final JTextField nameField = new JTextField();
    private final JPasswordField oldPass = new JPasswordField();
    private final JPasswordField newPass = new JPasswordField();
    private final JPasswordField confirmPass = new JPasswordField();

    public AccountPanel(User user, MainFrame main, ReminderManager tasks, MemoryManager memory, ChatLog log) {
        super("Account", "Your profile and sign-in details.");
        this.user = user;
        this.main = main;
        this.tasks = tasks;
        this.memory = memory;
        this.log = log;

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.add(profileCard());
        column.add(Box.createVerticalStrut(14));
        column.add(editCard());
        column.add(Box.createVerticalStrut(14));
        column.add(sessionCard());

        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(Theme.bg);
        holder.add(column, BorderLayout.NORTH);
        JScrollPane sp = Theme.scroll(holder);
        sp.setBorder(null);
        sp.getViewport().setBackground(Theme.bg);
        body.add(sp, BorderLayout.CENTER);

        refreshStats();
        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                refreshStats();
            }
        });
    }

    private JComponent profileCard() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(18, 0));

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
        avatar.setFont(Theme.font(Font.BOLD, 8));
        avatar.setForeground(Color.WHITE);
        avatar.setPreferredSize(new Dimension(72, 72));
        JPanel avatarBox = new JPanel(new BorderLayout());
        avatarBox.setOpaque(false);
        avatarBox.add(avatar, BorderLayout.NORTH);
        card.add(avatarBox, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        nameLabel.setText(user.fullName);
        info.add(nameLabel);
        info.add(Theme.muted("@" + user.username + "   |   member since " + user.createdOn));
        info.add(Box.createVerticalStrut(12));

        JPanel stats = new JPanel(new GridLayout(1, 4, 10, 0));
        stats.setOpaque(false);
        stats.add(stat(messagesStat, "messages sent"));
        stats.add(stat(tasksStat, "tasks pending"));
        stats.add(stat(remindersStat, "reminders"));
        stats.add(stat(notesStat, "notes saved"));
        stats.setAlignmentX(LEFT_ALIGNMENT);
        info.add(stats);
        for (Component c : info.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        card.add(info, BorderLayout.CENTER);
        return card;
    }

    private JComponent stat(JLabel number, String caption) {
        number.setFont(Theme.font(Font.BOLD, 6));
        number.setForeground(Theme.accent);
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.bg);
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        p.add(number, BorderLayout.NORTH);
        p.add(Theme.muted(caption), BorderLayout.SOUTH);
        return p;
    }

    private JComponent editCard() {
        JPanel card = Theme.card();
        card.setLayout(new GridLayout(1, 2, 28, 0));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(Theme.label("Full name", Font.BOLD, 1, Theme.text));
        left.add(Box.createVerticalStrut(8));
        Theme.styleField(nameField);
        nameField.setText(user.fullName);
        left.add(fill(nameField));
        left.add(Box.createVerticalStrut(8));
        JButton saveName = new JButton("Save name");
        Theme.styleButton(saveName, true);
        left.add(fill(saveName));
        saveName.addActionListener(e -> saveName());

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(Theme.label("Change password", Font.BOLD, 1, Theme.text));
        right.add(Box.createVerticalStrut(8));
        for (JPasswordField f : new JPasswordField[]{oldPass, newPass, confirmPass}) Theme.styleField(f);
        right.add(labelled("Current password", oldPass));
        right.add(labelled("New password", newPass));
        right.add(labelled("Confirm new password", confirmPass));
        right.add(Box.createVerticalStrut(4));
        JButton changePass = new JButton("Update password");
        Theme.styleButton(changePass, true);
        right.add(fill(changePass));
        changePass.addActionListener(e -> changePassword());

        card.add(left);
        card.add(right);
        return card;
    }

    private JComponent sessionCard() {
        JPanel card = Theme.card();
        card.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton logout = new JButton("Log out");
        Theme.styleButton(logout, false);
        JButton delete = new JButton("Delete account");
        Theme.styleDangerButton(delete);
        card.add(logout);
        card.add(delete);
        card.add(Theme.muted("Deleting removes your chats, tasks, notes and saved passwords."));
        logout.addActionListener(e -> main.logout());
        delete.addActionListener(e -> deleteAccount());
        return card;
    }

    private JComponent labelled(String label, JComponent field) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel l = Theme.muted(label);
        l.setAlignmentX(LEFT_ALIGNMENT);
        p.add(l);
        p.add(Box.createVerticalStrut(2));
        p.add(fill(field));
        p.add(Box.createVerticalStrut(6));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private JComponent fill(JComponent c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private void refreshStats() {
        messagesStat.setText(String.valueOf(log.userMessageCount()));
        tasksStat.setText(String.valueOf(tasks.pendingCount()));
        remindersStat.setText(String.valueOf(tasks.reminders().size()));
        notesStat.setText(String.valueOf(memory.memories().size() + memory.lifeStory().size()));
    }

    private boolean isDemo() {
        return user.username.equals(UserManager.SAMPLE_USER);
    }

    private void saveName() {
        String n = nameField.getText().trim();
        if (n.length() < 2) {
            error("Please enter your full name.");
            return;
        }
        UserManager.updateFullName(user, n);
        main.rebuild(MainFrame.PAGE_ACCOUNT);
    }

    private void changePassword() {
        if (isDemo()) {
            info("The demo account keeps its password so it always works for demos.\nCreate your own account to use a private password.");
            return;
        }
        String err = UserManager.changePassword(user, new String(oldPass.getPassword()),
                new String(newPass.getPassword()), new String(confirmPass.getPassword()));
        if (err != null) {
            error(err);
            return;
        }
        oldPass.setText("");
        newPass.setText("");
        confirmPass.setText("");
        info("Password updated.");
    }

    private void deleteAccount() {
        if (isDemo()) {
            info("The demo account can't be deleted. Create your own account if you want one you can remove.");
            return;
        }
        JPasswordField pw = new JPasswordField();
        Object[] msg = {"This permanently deletes the account \"" + user.username + "\" and all its data.\nEnter your password to confirm:", pw};
        if (JOptionPane.showConfirmDialog(this, msg, "Delete account", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (!UserManager.checkPassword(user, new String(pw.getPassword()))) {
            error("That password is not correct.");
            return;
        }
        UserManager.deleteAccount(user);
        main.logout();
    }
}
