import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class DashboardPanel extends Page {
    private final User user;
    private final MainFrame main;
    private final ReminderManager tasks;
    private final MemoryManager memory;
    private final Vault vault;

    private final JLabel tasksStat = new JLabel();
    private final JLabel remindersStat = new JLabel();
    private final JLabel notesStat = new JLabel();
    private final JLabel vaultStat = new JLabel();
    private final JLabel vaultCaption = Theme.muted("vault accounts");
    private final DefaultListModel<String> reminderModel = new DefaultListModel<>();
    private final DefaultListModel<String> taskModel = new DefaultListModel<>();
    private final JList<String> reminderList = Theme.styledList(reminderModel);
    private final JList<String> taskList = Theme.styledList(taskModel);
    // done tasks are not shown, so row number != task number
    private final List<Integer> taskIndexes = new ArrayList<>();

    private static final String[] TIPS = {
            "remind me to call mom in 30 minutes",
            "i need to buy milk, call dad and pay the bill",
            "my gmail password is abc123 and username is bob",
            "show my gmail data",
            "remember that my exam is on Friday at 10 am",
            "what do i have tomorrow",
            "what is 15% of 200",
            "i feel tired today",
    };

    public DashboardPanel(User user, MainFrame main, ReminderManager tasks, MemoryManager memory, Vault vault) {
        super("Dashboard", new SimpleDateFormat("EEEE, d MMMM yyyy").format(new Date()));
        this.user = user;
        this.main = main;
        this.tasks = tasks;
        this.memory = memory;
        this.vault = vault;

        JPanel column = new JPanel(new BorderLayout(0, 16));
        column.setOpaque(false);
        column.add(buildStats(), BorderLayout.NORTH);
        column.add(buildLists(), BorderLayout.CENTER);
        column.add(buildAsk(), BorderLayout.SOUTH);
        body.add(column, BorderLayout.CENTER);

        tasks.addListener(this::refresh);
        memory.addListener(this::refresh);
        vault.addListener(this::refresh);
        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                refresh();
            }
        });
        refresh();
    }

    private JComponent buildStats() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);
        row.add(statCard(tasksStat, Theme.muted("tasks pending")));
        row.add(statCard(remindersStat, Theme.muted("reminders coming up")));
        row.add(statCard(notesStat, Theme.muted("notes saved")));
        row.add(statCard(vaultStat, vaultCaption));
        return row;
    }

    private JComponent statCard(JLabel number, JLabel caption) {
        number.setFont(Theme.font(Font.BOLD, 10));
        number.setForeground(Theme.accent);
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout());
        card.add(number, BorderLayout.CENTER);
        card.add(caption, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildLists() {
        JPanel grid = new JPanel(new GridLayout(1, 2, 14, 0));
        grid.setOpaque(false);

        JPanel left = Theme.card();
        left.setLayout(new BorderLayout(0, 8));
        left.add(Theme.label("Coming up", Font.BOLD, 2, Theme.text), BorderLayout.NORTH);
        left.add(Theme.scroll(reminderList), BorderLayout.CENTER);
        JButton more = new JButton("Manage reminders");
        Theme.styleButton(more, false);
        more.addActionListener(e -> main.showPage(MainFrame.PAGE_TASKS));
        left.add(more, BorderLayout.SOUTH);

        JPanel right = Theme.card();
        right.setLayout(new BorderLayout(0, 8));
        right.add(Theme.label("To do", Font.BOLD, 2, Theme.text), BorderLayout.NORTH);
        right.add(Theme.scroll(taskList), BorderLayout.CENTER);
        right.add(Theme.muted("Double-click a task to mark it done."), BorderLayout.SOUTH);
        taskList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = taskList.getSelectedIndex();
                if (e.getClickCount() == 2 && row >= 0 && row < taskIndexes.size()) tasks.setDone(taskIndexes.get(row), true);
            }
        });

        grid.add(left);
        grid.add(right);
        return grid;
    }

    private JComponent buildAsk() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 8));
        card.add(Theme.label("Tell Elf something", Font.BOLD, 2, Theme.text), BorderLayout.NORTH);

        JTextField field = new JTextField();
        Theme.styleField(field);
        JButton send = new JButton("Send");
        Theme.styleButton(send, true);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(field, BorderLayout.CENTER);
        row.add(send, BorderLayout.EAST);
        card.add(row, BorderLayout.CENTER);
        card.add(Theme.muted("Try:  " + TIPS[new Random().nextInt(TIPS.length)]), BorderLayout.SOUTH);

        Runnable go = () -> {
            String t = field.getText().trim();
            if (t.isEmpty()) return;
            field.setText("");
            main.sendToChat(t);
        };
        send.addActionListener(e -> go.run());
        field.addActionListener(e -> go.run());
        return card;
    }

    private void refresh() {
        boolean h24 = user.settings.use24Hour();

        tasksStat.setText(String.valueOf(tasks.pendingCount()));
        notesStat.setText(String.valueOf(memory.memories().size() + memory.lifeStory().size()));
        vaultStat.setText(String.valueOf(vault.count()));
        vaultCaption.setText(vault.isSetUp() ? "vault accounts" : "vault not set up");

        int coming = 0;
        reminderModel.clear();
        for (ReminderManager.Reminder r : tasks.reminders()) {
            if (r.done || r.due <= 0) continue;
            coming++;
            if (reminderModel.size() < 6) {
                reminderModel.addElement("<html>" + TextUtil.htmlEscape(r.text) + "&nbsp;&nbsp;<font color='"
                        + (Theme.dark ? "#8e95a1" : "#8a9099") + "'>" + TimeParser.describe(r.due, h24) + "</font></html>");
            }
        }
        remindersStat.setText(String.valueOf(coming));
        if (reminderModel.isEmpty()) reminderModel.addElement("No upcoming reminders");

        taskModel.clear();
        taskIndexes.clear();
        List<ReminderManager.Todo> todos = tasks.todos();
        for (int i = 0; i < todos.size(); i++) {
            if (!todos.get(i).done && taskModel.size() < 8) {
                taskModel.addElement(todos.get(i).text);
                taskIndexes.add(i);
            }
        }
        if (taskModel.isEmpty()) taskModel.addElement("Nothing to do. Nice!");
    }
}
