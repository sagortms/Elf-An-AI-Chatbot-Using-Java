import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HelpPanel extends Page {

    private static class Topic {
        final String name, intro;
        final String[] examples;

        Topic(String name, String intro, String... examples) {
            this.name = name;
            this.intro = intro;
            this.examples = examples;
        }
    }

    private static final Topic[] TOPICS = {
            new Topic("Getting started",
                    "Talk to Elf in normal sentences. Words like please or could you are ignored, small spelling mistakes in the first words are fixed, and a few Bangla phrases written in English letters work too.",
                    "hello", "what can you do", "who are you", "what is NLP", "how do you work", "kemon acho"),
            new Topic("Tasks",
                    "Add one task or several in one sentence. Say you finished something in your own words, Elf matches it to the task.",
                    "add task buy milk", "i need to buy milk, call dad and pay the bill", "i have to submit the report by 5 pm",
                    "show my tasks", "done 2", "tick off buy milk", "delete task 3", "clear done tasks", "undo"),
            new Topic("Reminders",
                    "Say what and when in any order. Elf pops up a message and plays a sound when the time comes. Reminders that were missed while the app was closed show up when you log in.",
                    "remind me to call mom in 30 minutes", "remind me tomorrow at 9 am to submit report", "wake me up at 6 am",
                    "ping me at 8 pm about the match", "don't let me forget to pay rent tonight", "set a timer for 10 minutes",
                    "show reminders", "snooze 10 minutes", "reschedule reminder 1 to 7 pm", "cancel the reminder about mom",
                    "what do i have tomorrow"),
            new Topic("Notes and memory",
                    "Tell Elf things to keep and ask for them later. If a note contains a time, Elf offers to set a reminder as well.",
                    "remember that my exam is on Friday at 10 am", "my birthday is 5 march", "when is my exam", "what do you remember",
                    "life story: I grew up in Dhaka", "show my life story", "forget exam", "what do you know about me"),
            new Topic("Vault (passwords)",
                    "Save account details by simply saying them, in any order. The vault is locked with its own passkey (set in the Vault page); if it is locked, Elf asks for the passkey first and then does what you asked.",
                    "my facebook password is 1234 and user name is mutant phone number is 01912345678",
                    "gmail username bob, password xyz, email bob@example.com", "1234 is the password for netflix",
                    "my bkash pin is 4321", "show gmail password", "my gmail data show me", "show facebook phone number",
                    "change my gmail password to abc999", "delete netflix phone", "list passwords", "lock vault"),
            new Topic("Maths and knowledge",
                    "A calculator that understands symbols and words, plus short explanations of the topics of this project.",
                    "what is 12 * (3 + 4)", "20% of 150", "square root of 144", "15 plus 27", "what is machine learning",
                    "explain intent", "what is java"),
            new Topic("Mood and fun",
                    "Tell Elf how you feel and it answers with a fitting message. \"Not happy\" is understood as sad.",
                    "i feel tired", "i am so happy today", "i am not happy", "tell me a joke", "another one", "fun fact",
                    "motivate me", "flip a coin", "roll a dice", "random number between 5 and 20"),
            new Topic("Chat settings",
                    "A few settings can be changed by talking. Everything else is in Settings.",
                    "dark mode", "light mode", "call me Sagor", "change your name to Nova"),
            new Topic("About Elf",
                    "Elf is a desktop assistant written in Java Swing. It works offline, understands language with rules and pattern matching (no trained model), and keeps all data in text files in the data folder next to the program. Only the vault entries and login password hashes are encrypted.",
                    "where is my data stored", "who made you", "what technology are you built with"),
    };

    private final MainFrame main;
    private final DefaultListModel<String> topicModel = new DefaultListModel<>();
    private final JList<String> topicList = Theme.styledList(topicModel);
    private final DefaultListModel<String> exampleModel = new DefaultListModel<>();
    private final JList<String> exampleList = Theme.styledList(exampleModel);
    private final JLabel intro = new JLabel();

    public HelpPanel(MainFrame main) {
        super("Help", "What Elf understands. Select an example and press Try it to send it to the chat.");
        this.main = main;

        for (Topic t : TOPICS) topicModel.addElement(t.name);
        topicList.setFixedCellHeight(Theme.fontSize + 20);
        topicList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel left = Theme.card();
        left.setLayout(new BorderLayout());
        left.add(Theme.scroll(topicList), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(230, 0));

        intro.setFont(Theme.font(Font.PLAIN, 0));
        intro.setForeground(Theme.text);
        intro.setVerticalAlignment(SwingConstants.TOP);

        JButton tryIt = new JButton("Try it in chat");
        Theme.styleButton(tryIt, true);
        JPanel right = Theme.card();
        right.setLayout(new BorderLayout(0, 10));
        right.add(intro, BorderLayout.NORTH);
        right.add(Theme.scroll(exampleList), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(tryIt, BorderLayout.WEST);
        south.add(Theme.muted("Double-click an example to send it."), BorderLayout.EAST);
        right.add(south, BorderLayout.SOUTH);

        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        body.add(row, BorderLayout.CENTER);

        topicList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showTopic(topicList.getSelectedIndex());
        });
        tryIt.addActionListener(e -> send());
        exampleList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) send();
            }
        });
        topicList.setSelectedIndex(0);
    }

    private void showTopic(int i) {
        if (i < 0) return;
        Topic t = TOPICS[i];
        intro.setText("<html><div style='width:" + Theme.cssWidth(Theme.fontSize * 28) + "px'>" + TextUtil.htmlEscape(t.intro) + "</div></html>");
        exampleModel.clear();
        for (String ex : t.examples) exampleModel.addElement(ex);
    }

    private void send() {
        String text = exampleList.getSelectedValue();
        if (text != null) main.sendToChat(text);
    }
}
