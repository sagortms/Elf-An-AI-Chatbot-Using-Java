import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class Page extends JPanel {
    protected final JPanel body = new JPanel(new BorderLayout());

    public Page(String title, String subtitle) {
        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.bg);
        setBorder(new EmptyBorder(22, 26, 22, 26));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel t = Theme.title(title);
        t.setAlignmentX(LEFT_ALIGNMENT);
        JLabel s = Theme.muted(subtitle);
        s.setAlignmentX(LEFT_ALIGNMENT);
        head.add(t);
        head.add(Box.createVerticalStrut(3));
        head.add(s);
        add(head, BorderLayout.NORTH);

        body.setOpaque(false);
        add(body, BorderLayout.CENTER);
    }

    protected void info(String message) {
        JOptionPane.showMessageDialog(this, message, "Elf", JOptionPane.INFORMATION_MESSAGE);
    }

    protected void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Elf", JOptionPane.ERROR_MESSAGE);
    }

    protected boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, "Please confirm", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
