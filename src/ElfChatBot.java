import javax.swing.*;

// start here. compile with javac -d out src/*.java then java -cp out ElfChatBot
public class ElfChatBot {

    public static void main(String[] args) {
        try {
            // metal look and feel so the colours are the same on every os
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println("Could not set look and feel: " + e.getMessage());
        }
        UserManager.init();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
