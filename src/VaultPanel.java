import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VaultPanel extends Page {
    // the 3 screens: create passkey, unlock, entries
    private static final String SETUP = "setup", LOCKED = "locked", OPEN = "open";
    private static final String MASK = "••••••";

    private final Vault vault;
    private final CardLayout cards = new CardLayout();
    private final JPanel stack = new JPanel(cards);

    private final JPasswordField setupPass = new JPasswordField();
    private final JPasswordField setupConfirm = new JPasswordField();
    private final JLabel setupMessage = Theme.label(" ", Font.PLAIN, -1, Theme.danger);
    private final JPasswordField unlockPass = new JPasswordField();
    private final JLabel unlockMessage = Theme.label(" ", Font.PLAIN, -1, Theme.danger);

    private final JToggleButton passwordsTab = new JToggleButton("Passwords", true);
    private final JToggleButton passkeysTab = new JToggleButton("Passkeys");
    private final JCheckBox showSecrets = new JCheckBox("Show passwords");
    private final JTable table = new JTable();
    private final JLabel status = Theme.muted(" ");
    private final List<Vault.Entry> shown = new ArrayList<>();

    public VaultPanel(Vault vault) {
        super("Vault", "Passwords and passkeys, encrypted with your vault passkey.");
        this.vault = vault;

        stack.setOpaque(false);
        stack.add(buildSetup(), SETUP);
        stack.add(buildLocked(), LOCKED);
        stack.add(buildOpen(), OPEN);
        body.add(stack, BorderLayout.CENTER);

        vault.addListener(this::refresh);
        refresh();
    }

    private JComponent centered(JComponent card) {
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);
        wrap.add(card);
        return wrap;
    }

    private JComponent buildSetup() {
        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(400, 350));
        card.add(left(Theme.label("Create your vault passkey", Font.BOLD, 2, Theme.text)));
        card.add(Box.createVerticalStrut(6));
        card.add(left(Theme.muted("<html><div style='width:" + Theme.cssWidth(340) + "px'>This passkey locks everything in the vault. It is separate from your login password and can be changed later in Settings. If you forget it, the vault can only be reset, which deletes the saved entries.</div></html>")));
        card.add(Box.createVerticalStrut(14));
        Theme.styleField(setupPass);
        Theme.styleField(setupConfirm);
        card.add(left(Theme.muted("Passkey (at least " + Vault.MIN_PASSKEY_LENGTH + " characters)")));
        card.add(left(setupPass));
        card.add(Box.createVerticalStrut(8));
        card.add(left(Theme.muted("Confirm passkey")));
        card.add(left(setupConfirm));
        card.add(Box.createVerticalStrut(4));
        card.add(left(setupMessage));
        JButton create = new JButton("Create vault");
        Theme.styleButton(create, true);
        card.add(left(create));
        create.addActionListener(e -> {
            String p = new String(setupPass.getPassword());
            if (!p.equals(new String(setupConfirm.getPassword()))) {
                setupMessage.setText("The two passkeys do not match.");
                return;
            }
            String err = vault.setUp(p);
            if (err != null) {
                setupMessage.setText(err);
                return;
            }
            setupPass.setText("");
            setupConfirm.setText("");
            setupMessage.setText(" ");
        });
        setupConfirm.addActionListener(e -> create.doClick());
        return centered(card);
    }

    private JComponent buildLocked() {
        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(360, 280));
        card.add(left(Theme.label("The vault is locked", Font.BOLD, 2, Theme.text)));
        card.add(Box.createVerticalStrut(6));
        card.add(left(Theme.muted("<html><div style='width:" + Theme.cssWidth(300) + "px'>Enter your vault passkey to see your saved passwords and passkeys.</div></html>")));
        card.add(Box.createVerticalStrut(14));
        Theme.styleField(unlockPass);
        card.add(left(unlockPass));
        card.add(Box.createVerticalStrut(4));
        card.add(left(unlockMessage));
        JButton unlock = new JButton("Unlock");
        Theme.styleButton(unlock, true);
        card.add(left(unlock));
        card.add(Box.createVerticalStrut(10));
        card.add(left(Theme.muted("Forgot it? Settings > Vault can reset the vault.")));
        unlock.addActionListener(e -> {
            if (vault.unlock(new String(unlockPass.getPassword()))) {
                unlockPass.setText("");
                unlockMessage.setText(" ");
            } else {
                unlockMessage.setText("That passkey is not correct.");
                unlockPass.setText("");
            }
        });
        unlockPass.addActionListener(e -> unlock.doClick());
        return centered(card);
    }

    private JComponent buildOpen() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 12));

        ButtonGroup group = new ButtonGroup();
        group.add(passwordsTab);
        group.add(passkeysTab);
        styleTab(passwordsTab);
        styleTab(passkeysTab);
        passwordsTab.addActionListener(e -> refresh());
        passkeysTab.addActionListener(e -> refresh());
        showSecrets.setOpaque(false);
        showSecrets.setForeground(Theme.text);
        showSecrets.addActionListener(e -> refresh());

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tabs.setOpaque(false);
        tabs.add(passwordsTab);
        tabs.add(passkeysTab);
        tabs.add(Box.createHorizontalStrut(10));
        tabs.add(showSecrets);

        JButton lock = new JButton("Lock vault");
        Theme.styleButton(lock, false);
        lock.addActionListener(e -> vault.lock());
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(tabs, BorderLayout.WEST);
        top.add(lock, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        table.setModel(new DefaultTableModel());
        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        card.add(Theme.scroll(table), BorderLayout.CENTER);

        JButton add = new JButton("Add");
        JButton edit = new JButton("Edit");
        JButton delete = new JButton("Delete");
        JButton copy = new JButton("Copy secret");
        Theme.styleButton(add, true);
        for (JButton b : new JButton[]{edit, delete, copy}) Theme.styleButton(b, false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);
        buttons.add(copy);
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setOpaque(false);
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.SOUTH);
        card.add(bottom, BorderLayout.SOUTH);

        add.addActionListener(e -> editDialog(null));
        edit.addActionListener(e -> {
            Vault.Entry sel = selected();
            if (sel != null) editDialog(sel);
        });
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && selected() != null) editDialog(selected());
            }
        });
        delete.addActionListener(e -> {
            Vault.Entry sel = selected();
            if (sel != null && confirm("Delete everything saved for " + sel.title() + "?")) vault.delete(sel.service);
        });
        copy.addActionListener(e -> copySecret());
        return card;
    }

    private void styleTab(JToggleButton b) {
        b.setFocusPainted(false);
        b.setFont(Theme.font(Font.BOLD, -1));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
        b.addChangeListener(e -> {
            b.setBackground(b.isSelected() ? Theme.accent : Theme.panel);
            b.setForeground(b.isSelected() ? Color.WHITE : Theme.text);
        });
        b.setBackground(b.isSelected() ? Theme.accent : Theme.panel);
        b.setForeground(b.isSelected() ? Color.WHITE : Theme.text);
    }

    private <T extends JComponent> T left(T c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private void refresh() {
        if (!vault.isSetUp()) {
            cards.show(stack, SETUP);
            return;
        }
        if (!vault.isUnlocked()) {
            cards.show(stack, LOCKED);
            return;
        }
        cards.show(stack, OPEN);
        boolean passwords = passwordsTab.isSelected();
        showSecrets.setText(passwords ? "Show passwords" : "Show passkeys");

        shown.clear();
        for (Vault.Entry e : vault.entries()) {
            if ((e.kind.equals(Vault.PASSWORD)) == passwords) shown.add(e);
        }
        String[] cols = passwords
                ? new String[]{"Service", "Username", "Email", "Phone", "Password", "More"}
                : new String[]{"Service", "Passkey", "More"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        for (Vault.Entry e : shown) {
            String secretKey = passwords ? "password" : "passkey";
            String secret = e.fields.containsKey(secretKey) ? (showSecrets.isSelected() ? e.fields.get(secretKey) : MASK) : "";
            String more = more(e, passwords);
            if (passwords) {
                model.addRow(new Object[]{e.title(), val(e, "username"), val(e, "email"), val(e, "phone"), secret, more});
            } else {
                model.addRow(new Object[]{e.title(), secret, more});
            }
        }
        table.setModel(model);
        status.setText(shown.isEmpty() ? "Nothing here yet. Click Add, or just tell Elf in the chat." : shown.size() + " saved");
        status.setForeground(Theme.subtext);
    }

    private String val(Vault.Entry e, String key) {
        String v = e.fields.get(key);
        return v == null ? "" : v;
    }

    private String more(Vault.Entry e, boolean passwords) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> f : e.fields.entrySet()) {
            String k = f.getKey();
            boolean own = passwords ? (k.equals("username") || k.equals("email") || k.equals("phone") || k.equals("password"))
                    : k.equals("passkey");
            if (!own) parts.add(VaultParser.display(k) + ": " + f.getValue());
        }
        return String.join(", ", parts);
    }

    private Vault.Entry selected() {
        int row = table.getSelectedRow();
        return row >= 0 && row < shown.size() ? shown.get(row) : null;
    }

    private void copySecret() {
        Vault.Entry e = selected();
        if (e == null) {
            status.setText("Select a row first.");
            return;
        }
        final String secret = e.fields.containsKey("password") ? e.fields.get("password") : e.fields.get("passkey");
        if (secret == null) {
            status.setText("This entry has no password or passkey.");
            return;
        }
        final java.awt.datatransfer.Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setContents(new StringSelection(secret), null);
        status.setText("Copied. The clipboard is cleared in 30 seconds.");
        // clear the clipboard after 30 sec if it still has our password
        Timer t = new Timer(30000, ev -> {
            try {
                if (secret.equals(cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor))) cb.setContents(new StringSelection(""), null);
            } catch (Exception ignored) {
            }
        });
        t.setRepeats(false);
        t.start();
    }

    private void editDialog(Vault.Entry entry) {
        final boolean passwords = entry != null ? entry.kind.equals(Vault.PASSWORD) : passwordsTab.isSelected();
        final String[] keys = passwords
                ? new String[]{"username", "password", "email", "phone", "website", "note"}
                : new String[]{"passkey", "note"};

        JTextField service = new JTextField(entry == null ? "" : entry.title());
        Theme.styleField(service);
        Map<String, JTextField> inputs = new LinkedHashMap<>();
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 10);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        addRow(form, c, "Service / website", service);
        for (String k : keys) {
            JTextField f = new JTextField(entry != null && entry.fields.containsKey(k) ? entry.fields.get(k) : "");
            Theme.styleField(f);
            f.setColumns(20);
            inputs.put(k, f);
            addRow(form, c, VaultParser.display(k), f);
        }
        service.setColumns(20);

        int result = JOptionPane.showConfirmDialog(this, form, entry == null ? "Add to vault" : "Edit " + entry.title(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String name = service.getText().trim();
        if (name.isEmpty()) {
            error("Please enter the service or website name.");
            return;
        }
        Vault.Entry updated = new Vault.Entry(name, passwords ? Vault.PASSWORD : Vault.PASSKEY);
        // keep the fields this form doesnt show
        if (entry != null) updated.fields.putAll(entry.fields);
        for (String k : keys) {
            String v = inputs.get(k).getText().trim();
            if (v.isEmpty()) updated.fields.remove(k);
            else updated.fields.put(k, v);
        }
        String main = passwords ? "password" : "passkey";
        if (updated.fields.isEmpty() || (!passwords && !updated.fields.containsKey(main))) {
            error(passwords ? "Enter at least one detail (a password, username ...)." : "Please enter the passkey.");
            return;
        }
        if (entry != null) {
            vault.replace(entry.service, updated);
        } else {
            vault.save(name, updated.kind, updated.fields);
        }
    }

    private void addRow(JPanel form, GridBagConstraints c, String label, JComponent field) {
        c.gridx = 0;
        form.add(Theme.label(label, Font.PLAIN, -1, Theme.text), c);
        c.gridx = 1;
        form.add(field, c);
        c.gridy++;
    }
}
