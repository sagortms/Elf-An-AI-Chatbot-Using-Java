import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SettingsPanel extends Page {
    private final User user;
    private final MainFrame main;
    private final ChatLog log;
    private final Vault vault;

    private final JTextField botName = new JTextField();
    private final JToggleButton light = new JToggleButton("Light");
    private final JToggleButton dark = new JToggleButton("Dark");
    private final JComboBox<String> textSize = new JComboBox<>(new String[]{"Small", "Medium", "Large"});
    private final JCheckBox clock24 = new JCheckBox("Use 24-hour clock");
    private final JCheckBox typing = new JCheckBox("Show \"typing...\" before Elf replies");
    private final JLabel vaultStatus = Theme.muted(" ");
    private final JCheckBox sound = new JCheckBox("Play a sound when a reminder is due");

    public SettingsPanel(User user, MainFrame main, ChatLog log, Vault vault) {
        super("Settings", "These settings belong to your account only.");
        this.user = user;
        this.main = main;
        this.log = log;
        this.vault = vault;

        JPanel form = Theme.card();
        form.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 0, 6, 16);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;

        section(form, c, "Assistant");
        row(form, c, "Assistant name", botName);
        Theme.styleField(botName);
        botName.setColumns(11);
        botName.setMinimumSize(botName.getPreferredSize());

        section(form, c, "Appearance");
        ButtonGroup themeGroup = new ButtonGroup();
        themeGroup.add(light);
        themeGroup.add(dark);
        JPanel themeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        themeRow.setOpaque(false);
        themeRow.add(light);
        themeRow.add(dark);
        styleToggle(light);
        styleToggle(dark);
        row(form, c, "Theme", themeRow);
        row(form, c, "Text size", textSize);

        section(form, c, "Behaviour");
        for (JCheckBox box : new JCheckBox[]{clock24, typing, sound}) {
            box.setOpaque(false);
            wide(form, c, box);
        }

        JPanel form2 = Theme.card();
        form2.setLayout(new GridBagLayout());
        GridBagConstraints c2 = new GridBagConstraints();
        c2.insets = new Insets(6, 0, 6, 16);
        c2.anchor = GridBagConstraints.WEST;
        c2.gridx = 0;
        c2.gridy = 0;

        section(form2, c2, "Chat data");
        JButton export = new JButton("Export chat...");
        JButton clear = new JButton("Clear chat history");
        Theme.styleButton(export, false);
        Theme.styleButton(clear, false);
        JPanel dataRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        dataRow.setOpaque(false);
        dataRow.add(export);
        dataRow.add(clear);
        wide(form2, c2, dataRow);

        section(form2, c2, "Vault");
        JButton changePass = new JButton("Change passkey...");
        JButton resetVault = new JButton("Reset vault...");
        Theme.styleButton(changePass, false);
        Theme.styleButton(resetVault, false);
        JPanel vaultRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        vaultRow.setOpaque(false);
        vaultRow.add(changePass);
        vaultRow.add(resetVault);
        wide(form2, c2, vaultStatus);
        wide(form2, c2, vaultRow);
        changePass.addActionListener(e -> changePasskey());
        resetVault.addActionListener(e -> resetVault());
        vault.addListener(this::updateVaultStatus);
        updateVaultStatus();

        JButton save = new JButton("Save changes");
        JButton reset = new JButton("Reset to default");
        Theme.styleButton(save, true);
        Theme.styleButton(reset, false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(save);
        actions.add(reset);

        JPanel columns = new JPanel(new GridLayout(1, 2, 14, 0));
        columns.setOpaque(false);
        JPanel leftBox = new JPanel(new BorderLayout());
        leftBox.setOpaque(false);
        leftBox.add(form, BorderLayout.NORTH);
        JPanel rightBox = new JPanel(new BorderLayout());
        rightBox.setOpaque(false);
        rightBox.add(form2, BorderLayout.NORTH);
        columns.add(leftBox);
        columns.add(rightBox);

        JPanel wrap = new JPanel(new BorderLayout(0, 14));
        wrap.setOpaque(false);
        wrap.add(columns, BorderLayout.NORTH);
        wrap.add(actions, BorderLayout.CENTER);
        padRight(form);
        padRight(form2);
        body.add(wrap, BorderLayout.CENTER);

        load();
        java.awt.event.ActionListener live = e -> applyAppearance();
        light.addActionListener(live);
        dark.addActionListener(live);
        textSize.addActionListener(live);
        save.addActionListener(e -> save());
        reset.addActionListener(e -> {
            if (!confirm("Reset all settings to their default values?")) return;
            user.settings.resetToDefault();
            user.settings.save();
            main.rebuild(MainFrame.PAGE_SETTINGS);
        });
        export.addActionListener(e -> exportChat());
        clear.addActionListener(e -> {
            if (!confirm("Delete the whole chat history? This cannot be undone.")) return;
            log.clear();
            main.clearChatView();
            info("Chat history cleared.");
        });
    }

    // takes the spare width so the rows stay on the left
    private void padRight(JPanel form) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 5;
        g.gridy = 0;
        g.weightx = 1;
        form.add(new JLabel(), g);
    }

    private void section(JPanel form, GridBagConstraints c, String name) {
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(c.gridy == 0 ? 0 : 16, 0, 4, 16);
        form.add(Theme.label(name, Font.BOLD, 1, Theme.accent), c);
        c.gridy++;
        c.gridwidth = 1;
        c.insets = new Insets(6, 0, 6, 16);
    }

    private void row(JPanel form, GridBagConstraints c, String label, JComponent field) {
        c.gridx = 0;
        form.add(Theme.label(label, Font.PLAIN, 0, Theme.text), c);
        c.gridx = 1;
        form.add(field, c);
        c.gridy++;
    }

    private void wide(JPanel form, GridBagConstraints c, JComponent comp) {
        c.gridx = 0;
        c.gridwidth = 2;
        form.add(comp, c);
        c.gridy++;
        c.gridwidth = 1;
    }

    private void load() {
        Settings s = user.settings;
        botName.setText(s.botName());
        light.setSelected(!s.darkTheme());
        dark.setSelected(s.darkTheme());
        int size = s.fontSize();
        textSize.setSelectedIndex(size <= 13 ? 0 : size >= 16 ? 2 : 1);
        clock24.setSelected(s.use24Hour());
        typing.setSelected(s.typingDelay());
        sound.setSelected(s.reminderSound());
    }

    private void styleToggle(JToggleButton b) {
        b.setFocusPainted(false);
        b.setFont(Theme.font(Font.BOLD, -1));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)));
        b.addChangeListener(e -> {
            b.setBackground(b.isSelected() ? Theme.accent : Theme.panel);
            b.setForeground(b.isSelected() ? Color.WHITE : Theme.text);
        });
        b.setBackground(Theme.panel);
        b.setForeground(Theme.text);
    }

    private void applyAppearance() {
        Settings s = user.settings;
        int[] sizes = {13, 14, 16};
        boolean wantDark = dark.isSelected();
        int wantSize = sizes[textSize.getSelectedIndex()];
        if (wantDark == s.darkTheme() && wantSize == s.fontSize()) return;
        s.set("theme", wantDark ? "dark" : "light");
        s.set("fontSize", wantSize);
        s.save();
        main.rebuild(MainFrame.PAGE_SETTINGS);
    }

    private void save() {
        String name = botName.getText().trim();
        if (name.isEmpty() || name.length() > 20 || !name.matches("[\\p{L}0-9 _.\\-]+")) {
            error("The assistant name can have letters and numbers, up to 20 characters.");
            return;
        }
        Settings s = user.settings;
        boolean oldDark = s.darkTheme();
        int oldSize = s.fontSize();
        boolean oldClock = s.use24Hour();

        int[] sizes = {13, 14, 16};
        int newSize = sizes[textSize.getSelectedIndex()];
        s.set("botName", TextUtil.capitalize(name));
        s.set("theme", dark.isSelected() ? "dark" : "light");
        s.set("fontSize", newSize);
        s.set("clock24", clock24.isSelected());
        s.set("typingDelay", typing.isSelected());
        s.set("reminderSound", sound.isSelected());
        s.save();

        boolean needsRebuild = oldDark != dark.isSelected() || oldSize != newSize || oldClock != clock24.isSelected();
        if (needsRebuild) {
            main.rebuild(MainFrame.PAGE_SETTINGS);
        } else {
            main.refreshBotName();
            info("Settings saved.");
        }
    }

    private void updateVaultStatus() {
        if (!vault.isSetUp()) vaultStatus.setText("No vault passkey yet. Create one below.");
        else vaultStatus.setText("Vault passkey is set. " + vault.count() + " saved entr" + (vault.count() == 1 ? "y" : "ies") + ".");
    }

    private void changePasskey() {
        JPasswordField oldP = new JPasswordField(), newP = new JPasswordField(), confirmP = new JPasswordField();
        boolean exists = vault.isSetUp();
        for (JPasswordField f : new JPasswordField[]{oldP, newP, confirmP}) Theme.styleField(f);
        Object[] message = exists
                ? new Object[]{"Current passkey", oldP, "New passkey", newP, "Confirm new passkey", confirmP}
                : new Object[]{"New passkey", newP, "Confirm passkey", confirmP};
        if (JOptionPane.showConfirmDialog(this, message, exists ? "Change vault passkey" : "Create vault passkey",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String err;
        if (exists) {
            err = vault.changePasskey(new String(oldP.getPassword()), new String(newP.getPassword()), new String(confirmP.getPassword()));
        } else if (!new String(newP.getPassword()).equals(new String(confirmP.getPassword()))) {
            err = "The two passkeys do not match.";
        } else {
            err = vault.setUp(new String(newP.getPassword()));
        }
        if (err != null) error(err);
        else info(exists ? "Vault passkey changed." : "Vault passkey created.");
    }

    private void resetVault() {
        if (!vault.isSetUp() && vault.count() == 0) {
            info("There is nothing to reset yet.");
            return;
        }
        JPasswordField pw = new JPasswordField();
        Theme.styleField(pw);
        Object[] message = {"This deletes every saved password and passkey and removes the vault passkey.\nEnter your account password to confirm:", pw};
        if (JOptionPane.showConfirmDialog(this, message, "Reset vault", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (!UserManager.checkPassword(user, new String(pw.getPassword()))) {
            error("That account password is not correct.");
            return;
        }
        vault.reset();
        info("The vault was reset. You can create a new passkey in the Vault page.");
    }

    private void exportChat() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("elf_chat_" + user.username + ".txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File target = chooser.getSelectedFile();
        if (target.exists() && !confirm(target.getName() + " already exists. Replace it?")) return;

        List<String> lines = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (ChatLog.Entry e : log.load(Integer.MAX_VALUE)) {
            lines.add("[" + fmt.format(new Date(e.time)) + "] " + (e.fromUser ? user.firstName() : user.settings.botName())
                    + ": " + e.text.replace("\n", " / "));
        }
        if (FileStore.write(target, lines)) info("Saved " + lines.size() + " messages to " + target.getName() + ".");
        else error("Could not write the file.");
    }
}
