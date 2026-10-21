import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class LoginFrame extends JFrame {
    private static final String LOGIN = "login", REGISTER = "register";

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    // login form
    private JTextField loginUser;
    private JPasswordField loginPass;
    private JLabel loginMessage;

    // register form
    private JTextField regName, regUser;
    private JPasswordField regPass, regConfirm;
    private JLabel regMessage;

    public LoginFrame() {
        this(null, null);
    }

    public LoginFrame(String prefillUser) {
        this(prefillUser, null);
    }

    public LoginFrame(String prefillUser, Rectangle bounds) {
        Theme.apply(false, 14);
        setTitle("Elf - Sign in");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1040, 700);
        setMinimumSize(new Dimension(860, 600));
        setLocationRelativeTo(null);
        if (bounds != null) setBounds(bounds);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.bg);

        JPanel box = Theme.card();
        box.setLayout(new BorderLayout(0, 14));
        box.setPreferredSize(new Dimension(380, 500));
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.border), new EmptyBorder(24, 28, 20, 28)));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel logo = Theme.label("Elf", Font.BOLD, 14, Theme.accent);
        logo.setAlignmentX(CENTER_ALIGNMENT);
        JLabel tag = Theme.muted("Your personal AI assistant");
        tag.setAlignmentX(CENTER_ALIGNMENT);
        head.add(logo);
        head.add(Box.createVerticalStrut(2));
        head.add(tag);
        box.add(head, BorderLayout.NORTH);

        cardPanel.setOpaque(false);
        cardPanel.add(buildLogin(), LOGIN);
        cardPanel.add(buildRegister(), REGISTER);
        box.add(cardPanel, BorderLayout.CENTER);

        root.add(box);
        setContentPane(root);

        if (prefillUser != null) loginUser.setText(prefillUser);
        getRootPane().setDefaultButton(null);
        SwingUtilities.invokeLater(() -> (loginUser.getText().isEmpty() ? loginUser : loginPass).requestFocusInWindow());
    }

    private JPanel buildLogin() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        loginUser = new JTextField();
        loginPass = new JPasswordField();
        Theme.styleField(loginUser);
        Theme.styleField(loginPass);
        JCheckBox show = showPasswordBox(loginPass);

        loginMessage = messageLabel();

        JButton login = new JButton("Log in");
        Theme.styleButton(login, true);
        login.addActionListener(e -> doLogin());

        KeyAdapter enter = new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) doLogin();
            }
        };
        loginUser.addKeyListener(enter);
        loginPass.addKeyListener(enter);

        p.add(left(Theme.title("Welcome back")));
        p.add(Box.createVerticalStrut(14));
        p.add(fieldLabel("Username"));
        p.add(left(loginUser));
        p.add(Box.createVerticalStrut(10));
        p.add(fieldLabel("Password"));
        p.add(left(loginPass));
        p.add(Box.createVerticalStrut(4));
        p.add(show);
        p.add(Box.createVerticalStrut(4));
        p.add(loginMessage);
        p.add(Box.createVerticalStrut(8));
        p.add(full(login));
        p.add(Box.createVerticalGlue());
        p.add(Box.createVerticalStrut(10));
        p.add(centered(switchLink("New here? Create an account", REGISTER)));
        p.add(Box.createVerticalStrut(6));

        p.add(centered(Theme.muted("Demo account:  mutant  /  mutant1234")));
        p.add(centered(Theme.muted("Demo vault passkey:  1234")));
        return p;
    }

    private JPanel buildRegister() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        regName = new JTextField();
        regUser = new JTextField();
        regPass = new JPasswordField();
        regConfirm = new JPasswordField();
        Theme.styleField(regName);
        Theme.styleField(regUser);
        Theme.styleField(regPass);
        Theme.styleField(regConfirm);
        regMessage = messageLabel();

        JButton create = new JButton("Create account");
        Theme.styleButton(create, true);
        create.addActionListener(e -> doRegister());
        regConfirm.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) doRegister();
            }
        });

        p.add(left(Theme.title("Create your account")));
        p.add(Box.createVerticalStrut(10));
        p.add(fieldLabel("Full name"));
        p.add(left(regName));
        p.add(Box.createVerticalStrut(8));
        p.add(fieldLabel("Username"));
        p.add(left(regUser));
        p.add(Box.createVerticalStrut(8));
        p.add(fieldLabel("Password (6+ characters)"));
        p.add(left(regPass));
        p.add(Box.createVerticalStrut(8));
        p.add(fieldLabel("Confirm password"));
        p.add(left(regConfirm));
        p.add(Box.createVerticalStrut(4));
        p.add(regMessage);
        p.add(Box.createVerticalStrut(6));
        p.add(full(create));
        p.add(Box.createVerticalGlue());
        p.add(centered(switchLink("Already have an account? Log in", LOGIN)));
        return p;
    }

    private void doLogin() {
        String u = loginUser.getText().trim();
        String pw = new String(loginPass.getPassword());
        if (u.isEmpty() || pw.isEmpty()) {
            loginMessage.setText("Enter your username and password.");
            return;
        }
        User user = UserManager.login(u, pw);
        if (user == null) {
            loginMessage.setText("Wrong username or password.");
            loginPass.setText("");
            loginPass.requestFocusInWindow();
            return;
        }
        MainFrame main = new MainFrame(user, MainFrame.PAGE_DASHBOARD);
        // main window opens with the same size and place as this one
        main.setBounds(getBounds());
        main.setVisible(true);
        dispose();
    }

    private void doRegister() {
        String err = UserManager.register(regUser.getText(), regName.getText(),
                new String(regPass.getPassword()), new String(regConfirm.getPassword()));
        if (err != null) {
            regMessage.setText(err);
            return;
        }
        String username = regUser.getText().trim().toLowerCase();
        JOptionPane.showMessageDialog(this, "Account created. You can log in now.", "Welcome", JOptionPane.INFORMATION_MESSAGE);
        regName.setText("");
        regUser.setText("");
        regPass.setText("");
        regConfirm.setText("");
        regMessage.setText(" ");
        loginUser.setText(username);
        loginPass.setText("");
        loginMessage.setText(" ");
        cards.show(cardPanel, LOGIN);
        loginPass.requestFocusInWindow();
    }

    // keeps everything in the box aligned to the left
    private <T extends JComponent> T left(T c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private JComponent centered(JComponent c) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.add(c);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private JLabel fieldLabel(String s) {
        JLabel l = Theme.label(s, Font.PLAIN, -1, Theme.subtext);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        return l;
    }

    private JLabel messageLabel() {
        JLabel l = Theme.label(" ", Font.PLAIN, -1, Theme.danger);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JCheckBox showPasswordBox(JPasswordField field) {
        JCheckBox show = new JCheckBox("Show password");
        show.setOpaque(false);
        show.setFont(Theme.font(Font.PLAIN, -1));
        show.setForeground(Theme.subtext);
        show.setAlignmentX(LEFT_ALIGNMENT);
        char echo = field.getEchoChar();
        show.addActionListener(e -> field.setEchoChar(show.isSelected() ? (char) 0 : echo));
        return show;
    }

    private JComponent full(JButton b) {
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return b;
    }

    private JLabel switchLink(String text, String target) {
        JLabel l = Theme.label(text, Font.PLAIN, -1, Theme.accent);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                loginMessage.setText(" ");
                regMessage.setText(" ");
                cards.show(cardPanel, target);
            }
        });
        return l;
    }
}
