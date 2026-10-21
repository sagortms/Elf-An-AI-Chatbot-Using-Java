import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.text.JTextComponent;
import java.awt.*;

public class Theme {
    public static boolean dark = false;
    public static int fontSize = 14;

    public static Color bg, panel, text, subtext, border, accent, accentDark, onAccent, danger;
    public static Color sidebar, sidebarText, sidebarSelected, sidebarHover;
    public static Color userBubble, botBubble, userBubbleText, botBubbleText, inputBg;

    static {
        apply(false, 14);
    }

    public static void apply(boolean darkMode, int size) {
        dark = darkMode;
        fontSize = size;
        accent = new Color(62, 142, 90);
        accentDark = new Color(46, 112, 70);
        onAccent = Color.WHITE;
        danger = new Color(192, 57, 43);
        if (dark) {
            bg = new Color(30, 33, 38);
            panel = new Color(39, 43, 50);
            text = new Color(230, 232, 235);
            subtext = new Color(150, 156, 166);
            border = new Color(62, 67, 76);
            sidebar = new Color(22, 25, 29);
            sidebarText = new Color(200, 205, 212);
            sidebarSelected = new Color(48, 54, 62);
            sidebarHover = new Color(36, 40, 46);
            userBubble = new Color(46, 92, 64);
            botBubble = new Color(52, 58, 69);
            userBubbleText = new Color(240, 245, 241);
            botBubbleText = new Color(230, 232, 235);
            inputBg = new Color(47, 52, 60);
        } else {
            bg = new Color(245, 246, 248);
            panel = Color.WHITE;
            text = new Color(33, 37, 41);
            subtext = new Color(108, 117, 125);
            border = new Color(218, 221, 226);
            sidebar = new Color(37, 42, 51);
            sidebarText = new Color(214, 219, 226);
            sidebarSelected = new Color(60, 68, 82);
            sidebarHover = new Color(48, 54, 66);
            userBubble = new Color(200, 230, 201);
            botBubble = new Color(236, 238, 241);
            userBubbleText = new Color(25, 45, 30);
            botBubbleText = new Color(33, 37, 41);
            inputBg = Color.WHITE;
        }
        setDefaults();
    }

    // colours for the stock components too (dialogs, lists, tables ...)
    private static void setDefaults() {
        ColorUIResource bgR = new ColorUIResource(bg);
        ColorUIResource panelR = new ColorUIResource(panel);
        ColorUIResource textR = new ColorUIResource(text);
        ColorUIResource inputR = new ColorUIResource(inputBg);
        Font f = font(Font.PLAIN, 0);

        String[] backgrounds = {"Panel.background", "OptionPane.background", "CheckBox.background", "RadioButton.background",
                "Viewport.background", "ScrollPane.background", "Label.background", "SplitPane.background"};
        for (String k : backgrounds) UIManager.put(k, bgR);
        String[] foregrounds = {"Label.foreground", "OptionPane.messageForeground", "CheckBox.foreground", "RadioButton.foreground",
                "TextField.foreground", "PasswordField.foreground", "TextArea.foreground", "List.foreground", "ComboBox.foreground"};
        for (String k : foregrounds) UIManager.put(k, textR);
        String[] inputs = {"TextField.background", "PasswordField.background", "TextArea.background", "ComboBox.background"};
        for (String k : inputs) UIManager.put(k, inputR);
        UIManager.put("ToggleButton.select", new ColorUIResource(accent));
        UIManager.put("Table.background", panelR);
        UIManager.put("Table.foreground", textR);
        UIManager.put("Table.selectionBackground", new ColorUIResource(accent));
        UIManager.put("Table.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("TableHeader.background", new ColorUIResource(dark ? new Color(52, 58, 69) : new Color(236, 238, 241)));
        UIManager.put("TableHeader.foreground", textR);
        UIManager.put("List.background", panelR);
        UIManager.put("List.selectionBackground", new ColorUIResource(accent));
        UIManager.put("List.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(accent));
        UIManager.put("ComboBox.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("TextField.caretForeground", textR);
        UIManager.put("PasswordField.caretForeground", textR);
        UIManager.put("TextField.selectionBackground", new ColorUIResource(accent));
        UIManager.put("PasswordField.selectionBackground", new ColorUIResource(accent));
        UIManager.put("OptionPane.buttonFont", f);
        UIManager.put("OptionPane.messageFont", f);
        UIManager.put("Button.font", f);
        UIManager.put("Label.font", f);
        UIManager.put("TextField.font", f);
        UIManager.put("PasswordField.font", f);
        UIManager.put("List.font", f);
        UIManager.put("ComboBox.font", f);
        UIManager.put("CheckBox.font", f);
        UIManager.put("RadioButton.font", f);
        UIManager.put("TextArea.font", f);
        UIManager.put("ToolTip.font", font(Font.PLAIN, -1));
    }

    private static double cssScale = 0;

    // swing turns "width:100px" in html into more real pixels (about 1.33x here), so measure it once and divide
    public static int cssWidth(int pixels) {
        if (cssScale == 0) {
            JLabel probe = new JLabel("<html><div style='width:100px'>x</div></html>");
            cssScale = probe.getPreferredSize().width / 100.0;
            if (cssScale <= 0) cssScale = 1;
        }
        return (int) Math.round(pixels / cssScale);
    }

    public static Font font(int style, int delta) {
        return new Font(Font.SANS_SERIF, style, fontSize + delta);
    }

    public static void styleButton(JButton b, boolean primary) {
        b.setFont(font(Font.BOLD, -1));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (primary) {
            b.setBackground(accent);
            b.setForeground(onAccent);
            b.setBorder(new EmptyBorder(8, 18, 8, 18));
        } else {
            b.setBackground(panel);
            b.setForeground(text);
            b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(border), new EmptyBorder(7, 14, 7, 14)));
        }
    }

    public static void styleDangerButton(JButton b) {
        styleButton(b, true);
        b.setBackground(danger);
    }

    public static void styleField(JTextComponent c) {
        c.setFont(font(Font.PLAIN, 0));
        c.setBackground(inputBg);
        c.setForeground(text);
        c.setCaretColor(text);
        c.setBorder(BorderFactory.createCompoundBorder(new LineBorder(border), new EmptyBorder(7, 9, 7, 9)));
    }

    public static JLabel label(String s, int style, int delta, Color color) {
        JLabel l = new JLabel(s);
        l.setFont(font(style, delta));
        l.setForeground(color);
        return l;
    }

    public static JLabel title(String s) {
        return label(s, Font.BOLD, 6, text);
    }

    public static JLabel muted(String s) {
        return label(s, Font.PLAIN, -1, subtext);
    }

    public static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(panel);
        p.setBorder(BorderFactory.createCompoundBorder(new LineBorder(border), new EmptyBorder(14, 16, 14, 16)));
        return p;
    }

    public static JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(new LineBorder(border));
        sp.getViewport().setBackground(panel);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    public static void styleTable(javax.swing.JTable t) {
        t.setBackground(panel);
        t.setForeground(text);
        t.setFont(font(Font.PLAIN, 0));
        t.setRowHeight(fontSize + 14);
        t.setGridColor(border);
        t.setShowVerticalLines(false);
        t.setFillsViewportHeight(true);
        t.setSelectionBackground(accent);
        t.setSelectionForeground(Color.WHITE);
        t.setIntercellSpacing(new Dimension(0, 1));
        javax.swing.table.JTableHeader h = t.getTableHeader();
        h.setFont(font(Font.BOLD, -1));
        h.setBackground(dark ? new Color(52, 58, 69) : new Color(236, 238, 241));
        h.setForeground(text);
        h.setReorderingAllowed(false);
    }

    public static JList<String> styledList(DefaultListModel<String> model) {
        JList<String> list = new JList<>(model);
        list.setBackground(panel);
        list.setForeground(text);
        list.setFont(font(Font.PLAIN, 0));
        list.setFixedCellHeight(fontSize + 18);
        list.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, sel, false);
                c.setBorder(new EmptyBorder(0, 10, 0, 10));
                if (!sel) {
                    c.setBackground(panel);
                    c.setForeground(text);
                }
                return c;
            }
        });
        return list;
    }
}
