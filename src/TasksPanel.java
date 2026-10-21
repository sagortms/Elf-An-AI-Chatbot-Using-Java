import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class TasksPanel extends Page {
    private final User user;
    private final ReminderManager tasks;

    private final DefaultListModel<String> todoModel = new DefaultListModel<>();
    private final DefaultListModel<String> remModel = new DefaultListModel<>();
    private final JList<String> todoList = Theme.styledList(todoModel);
    private final JList<String> remList = Theme.styledList(remModel);
    private final JLabel todoCount = Theme.muted(" ");
    private final JLabel remStatus = Theme.muted(" ");

    public TasksPanel(User user, ReminderManager tasks) {
        super("Tasks", "Your to-do list and reminders. Elf can also manage these from the chat.");
        this.user = user;
        this.tasks = tasks;

        JPanel grid = new JPanel(new GridLayout(1, 2, 18, 0));
        grid.setOpaque(false);
        grid.add(buildTodo());
        grid.add(buildReminders());
        body.add(grid, BorderLayout.CENTER);

        tasks.addListener(this::refresh);
        refresh();
    }

    private JComponent buildTodo() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 10));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Theme.label("To-do list", Font.BOLD, 2, Theme.text), BorderLayout.WEST);
        head.add(todoCount, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        card.add(Theme.scroll(todoList), BorderLayout.CENTER);

        JTextField field = new JTextField();
        Theme.styleField(field);
        JButton add = new JButton("Add");
        Theme.styleButton(add, true);
        JPanel addRow = new JPanel(new BorderLayout(8, 0));
        addRow.setOpaque(false);
        addRow.add(field, BorderLayout.CENTER);
        addRow.add(add, BorderLayout.EAST);

        JButton toggle = new JButton("Done / Undo");
        JButton delete = new JButton("Delete");
        JButton clear = new JButton("Clear done");
        Theme.styleButton(toggle, false);
        Theme.styleButton(delete, false);
        Theme.styleButton(clear, false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(toggle);
        buttons.add(delete);
        buttons.add(clear);

        JPanel bottom = new JPanel(new BorderLayout(0, 10));
        bottom.setOpaque(false);
        bottom.add(addRow, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        card.add(bottom, BorderLayout.SOUTH);

        Runnable doAdd = () -> {
            String t = field.getText().trim();
            if (t.isEmpty()) return;
            tasks.addTodo(t);
            field.setText("");
        };
        add.addActionListener(e -> doAdd.run());
        field.addActionListener(e -> doAdd.run());
        toggle.addActionListener(e -> toggleSelected());
        delete.addActionListener(e -> {
            int i = todoList.getSelectedIndex();
            if (i >= 0) tasks.deleteTodo(i);
        });
        clear.addActionListener(e -> tasks.clearDone());
        todoList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) toggleSelected();
            }
        });
        return card;
    }

    private JComponent buildReminders() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 10));
        card.add(Theme.label("Reminders", Font.BOLD, 2, Theme.text), BorderLayout.NORTH);
        card.add(Theme.scroll(remList), BorderLayout.CENTER);

        JTextField field = new JTextField();
        Theme.styleField(field);
        JButton add = new JButton("Add");
        Theme.styleButton(add, true);
        JPanel addRow = new JPanel(new BorderLayout(8, 0));
        addRow.setOpaque(false);
        addRow.add(field, BorderLayout.CENTER);
        addRow.add(add, BorderLayout.EAST);

        JLabel hint = Theme.muted("Example: call mom in 30 minutes, or tomorrow at 9 am");
        hint.setFont(Theme.font(Font.PLAIN, -3));

        JButton delete = new JButton("Delete");
        JButton clear = new JButton("Clear done");
        Theme.styleButton(delete, false);
        Theme.styleButton(clear, false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(delete);
        buttons.add(clear);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        addRow.setAlignmentX(LEFT_ALIGNMENT);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        remStatus.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        bottom.add(addRow);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(hint);
        bottom.add(remStatus);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(buttons);
        card.add(bottom, BorderLayout.SOUTH);

        Runnable doAdd = () -> {
            String t = field.getText().trim();
            if (t.isEmpty()) return;
            TimeParser.Result r = TimeParser.parse(t, System.currentTimeMillis());
            if (r.text.isEmpty()) r.text = "Reminder";
            if (r.due == 0) {
                remStatus.setText("I couldn't find a time in that. Add something like \"in 20 minutes\" or \"at 5 pm\".");
                remStatus.setForeground(Theme.danger);
                return;
            }
            tasks.addReminder(r.text, r.due);
            field.setText("");
            remStatus.setText("Reminder set for " + TimeParser.describe(r.due, user.settings.use24Hour()) + ".");
            remStatus.setForeground(Theme.accent);
        };
        add.addActionListener(e -> doAdd.run());
        field.addActionListener(e -> doAdd.run());
        delete.addActionListener(e -> {
            int i = remList.getSelectedIndex();
            if (i >= 0) tasks.deleteReminder(i);
        });
        clear.addActionListener(e -> tasks.clearFinishedReminders());
        return card;
    }

    private void toggleSelected() {
        int i = todoList.getSelectedIndex();
        if (i < 0) return;
        tasks.setDone(i, !tasks.todos().get(i).done);
        if (i < todoModel.size()) todoList.setSelectedIndex(i);
    }

    private void refresh() {
        int selTodo = todoList.getSelectedIndex();
        int selRem = remList.getSelectedIndex();
        todoModel.clear();
        List<ReminderManager.Todo> todos = tasks.todos();
        String gray = Theme.dark ? "#888e99" : "#9aa0a6";
        for (int i = 0; i < todos.size(); i++) {
            ReminderManager.Todo t = todos.get(i);
            String label = (i + 1) + ".  " + TextUtil.htmlEscape(t.text);
            todoModel.addElement(t.done ? "<html><strike><font color='" + gray + "'>" + label + "</font></strike></html>" : label);
        }
        int left = tasks.pendingCount();
        todoCount.setText(left + " pending, " + (todos.size() - left) + " done");

        remModel.clear();
        long now = System.currentTimeMillis();
        for (int i = 0; i < tasks.reminders().size(); i++) {
            ReminderManager.Reminder r = tasks.reminders().get(i);
            String when = TimeParser.describe(r.due, user.settings.use24Hour());
            String label = (i + 1) + ".  " + TextUtil.htmlEscape(r.text) + "   <font color='" + gray + "'>" + when;
            if (r.done) label += " (done)";
            else if (r.due > 0 && r.due < now) label += " (overdue)";
            label += "</font>";
            remModel.addElement("<html>" + (r.done ? "<strike>" + label + "</strike>" : label) + "</html>");
        }
        if (selTodo >= 0 && selTodo < todoModel.size()) todoList.setSelectedIndex(selTodo);
        if (selRem >= 0 && selRem < remModel.size()) remList.setSelectedIndex(selRem);
    }
}
