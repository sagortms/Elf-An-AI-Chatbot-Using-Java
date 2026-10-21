import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NotesPanel extends Page {
    private final MemoryManager memory;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = Theme.styledList(model);
    private final JTextField search = new JTextField();
    private final JTextField input = new JTextField();
    private final JToggleButton memoriesTab = new JToggleButton("Memories", true);
    private final JToggleButton storyTab = new JToggleButton("Life story");
    private final JLabel countLabel = Theme.muted(" ");

    // row in the list -> index in the saved list (search can hide rows)
    private final List<Integer> shownIndexes = new ArrayList<>();

    public NotesPanel(MemoryManager memory) {
        super("Notes", "Things you asked Elf to remember, and your life story.");
        this.memory = memory;

        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 12));

        ButtonGroup group = new ButtonGroup();
        group.add(memoriesTab);
        group.add(storyTab);
        styleTab(memoriesTab);
        styleTab(storyTab);
        memoriesTab.addActionListener(e -> reload());
        storyTab.addActionListener(e -> reload());
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tabs.setOpaque(false);
        tabs.add(memoriesTab);
        tabs.add(storyTab);

        Theme.styleField(search);
        search.setPreferredSize(new Dimension(220, search.getPreferredSize().height));
        search.setToolTipText("Search");
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                reload();
            }

            public void removeUpdate(DocumentEvent e) {
                reload();
            }

            public void changedUpdate(DocumentEvent e) {
                reload();
            }
        });
        JPanel searchBox = new JPanel(new BorderLayout(6, 0));
        searchBox.setOpaque(false);
        searchBox.add(Theme.muted("Search"), BorderLayout.WEST);
        searchBox.add(search, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(tabs, BorderLayout.WEST);
        top.add(searchBox, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        card.add(Theme.scroll(list), BorderLayout.CENTER);

        Theme.styleField(input);
        JButton add = new JButton("Add");
        Theme.styleButton(add, true);
        JButton delete = new JButton("Delete selected");
        Theme.styleButton(delete, false);
        JPanel addRow = new JPanel(new BorderLayout(8, 0));
        addRow.setOpaque(false);
        addRow.add(input, BorderLayout.CENTER);
        addRow.add(add, BorderLayout.EAST);

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.add(delete, BorderLayout.WEST);
        buttons.add(countLabel, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(0, 10));
        bottom.setOpaque(false);
        bottom.add(addRow, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        card.add(bottom, BorderLayout.SOUTH);

        body.add(card, BorderLayout.CENTER);

        Runnable doAdd = () -> {
            String t = input.getText().trim();
            if (t.isEmpty()) return;
            if (memoriesTab.isSelected()) memory.addMemory(t);
            else memory.addLifeStory(t);
            input.setText("");
        };
        add.addActionListener(e -> doAdd.run());
        input.addActionListener(e -> doAdd.run());
        delete.addActionListener(e -> {
            int row = list.getSelectedIndex();
            if (row < 0 || row >= shownIndexes.size()) return;
            int real = shownIndexes.get(row);
            if (memoriesTab.isSelected()) memory.deleteMemory(real);
            else memory.deleteLifeStory(real);
        });

        memory.addListener(this::reload);
        reload();
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

    private void reload() {
        List<MemoryManager.Entry> all = memoriesTab.isSelected() ? memory.memories() : memory.lifeStory();
        String q = search.getText().trim().toLowerCase();
        model.clear();
        shownIndexes.clear();
        for (int i = 0; i < all.size(); i++) {
            MemoryManager.Entry e = all.get(i);
            if (!q.isEmpty() && !e.text.toLowerCase().contains(q)) continue;
            model.addElement("<html><font color='" + (Theme.dark ? "#8e95a1" : "#8a9099") + "'>"
                    + e.date + "</font>&nbsp;&nbsp;" + TextUtil.htmlEscape(e.text) + "</html>");
            shownIndexes.add(i);
        }
        countLabel.setText(all.size() + (all.size() == 1 ? " entry" : " entries"));
    }
}
