import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// to-do list and reminders of one user. to-do.txt = done|task, reminders.txt = due|done|text
public class ReminderManager {
    private final File todoFile;
    private final File reminderFile;
    private final User user;
    private final List<Runnable> listeners = new ArrayList<>();

    public static class Todo {
        public String text;
        public boolean done;

        Todo(String text, boolean done) {
            this.text = text;
            this.done = done;
        }
    }

    public static class Reminder {
        public long due;
        public boolean done;
        public String text;

        Reminder(long due, boolean done, String text) {
            this.due = due;
            this.done = done;
            this.text = text;
        }
    }

    // the last reminder that rang, snooze uses it
    public Reminder lastFired;

    private final List<Todo> todos = new ArrayList<>();
    private final List<Reminder> reminders = new ArrayList<>();

    public ReminderManager(User user) {
        this.user = user;
        todoFile = user.file("to-do.txt");
        reminderFile = user.file("reminders.txt");
        load();
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    private void changed() {
        for (Runnable r : listeners) r.run();
    }

    private void load() {
        todos.clear();
        for (String line : FileStore.read(todoFile)) {
            int bar = line.indexOf('|');
            if (bar == 1) todos.add(new Todo(TextUtil.unescape(line.substring(2)), line.charAt(0) == '1'));
            else todos.add(new Todo(TextUtil.unescape(line), false));
        }
        reminders.clear();
        for (String line : FileStore.read(reminderFile)) {
            String[] p = line.split("\\|", 3);
            if (p.length < 3) continue;
            try {
                reminders.add(new Reminder(Long.parseLong(p[0]), p[1].equals("1"), TextUtil.unescape(p[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        sortReminders();
    }

    private void saveTodos() {
        List<String> lines = new ArrayList<>();
        for (Todo t : todos) lines.add((t.done ? "1" : "0") + "|" + TextUtil.escape(t.text));
        FileStore.write(todoFile, lines);
        changed();
    }

    private void saveReminders() {
        sortReminders();
        List<String> lines = new ArrayList<>();
        for (Reminder r : reminders) lines.add(r.due + "|" + (r.done ? "1" : "0") + "|" + TextUtil.escape(r.text));
        FileStore.write(reminderFile, lines);
        changed();
    }

    // timed ones first, ones without a time at the end
    private void sortReminders() {
        Collections.sort(reminders, new Comparator<Reminder>() {
            public int compare(Reminder a, Reminder b) {
                if (a.due == 0 && b.due == 0) return 0;
                if (a.due == 0) return 1;
                if (b.due == 0) return -1;
                return Long.compare(a.due, b.due);
            }
        });
    }

    public List<Todo> todos() {
        return todos;
    }

    public String addTodo(String task) {
        task = TextUtil.capitalize(task.trim());
        for (Todo t : todos) {
            if (!t.done && t.text.equalsIgnoreCase(task)) return "\"" + task + "\" is already on your list.";
        }
        todos.add(new Todo(task, false));
        saveTodos();
        return "Added to your to-do list: " + task + "  (task " + todos.size() + ")";
    }

    public void setDone(int index, boolean done) {
        if (index < 0 || index >= todos.size()) return;
        todos.get(index).done = done;
        saveTodos();
    }

    public void deleteTodo(int index) {
        if (index < 0 || index >= todos.size()) return;
        todos.remove(index);
        saveTodos();
    }

    public int clearDone() {
        int before = todos.size();
        for (int i = todos.size() - 1; i >= 0; i--) {
            if (todos.get(i).done) todos.remove(i);
        }
        saveTodos();
        return before - todos.size();
    }

    public void clearTodos() {
        todos.clear();
        saveTodos();
    }

    public int pendingCount() {
        int n = 0;
        for (Todo t : todos) if (!t.done) n++;
        return n;
    }

    public int findTodo(String query) {
        String q = TextUtil.clean(query);
        if (q.isEmpty()) return -1;
        int best = -1;
        double bestScore = 0;
        String[] qw = TextUtil.words(q);
        for (int i = 0; i < todos.size(); i++) {
            String[] tw = TextUtil.words(TextUtil.clean(todos.get(i).text));
            int hit = 0;
            for (String a : qw) {
                for (String b : tw) {
                    if (TextUtil.similar(a, b)) {
                        hit++;
                        break;
                    }
                }
            }
            double score = (double) hit / Math.max(qw.length, 1);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return bestScore >= 0.6 ? best : -1;
    }

    public String readTodos() {
        if (todos.isEmpty()) return "Your to-do list is empty. Say \"add task buy milk\" to start one.";
        StringBuilder sb = new StringBuilder("Your to-do list:");
        for (int i = 0; i < todos.size(); i++) {
            Todo t = todos.get(i);
            sb.append("\n").append(i + 1).append(". ").append(t.done ? "[done] " : "[ ] ").append(t.text);
        }
        int left = pendingCount();
        sb.append("\n").append(left == 0 ? "Everything is done!" : left + " still to do.");
        return sb.toString();
    }

    public List<Reminder> reminders() {
        return reminders;
    }

    public String addReminder(String text, long due) {
        reminders.add(new Reminder(due, false, text));
        saveReminders();
        if (due == 0) return "Noted: " + text + ". You didn't give a time, so I won't alert you. Try \"remind me to ... at 5 pm\".";
        return "I'll remind you: " + text + " (" + TimeParser.describe(due, user.settings.use24Hour()) + ").";
    }

    public void deleteReminder(int index) {
        if (index < 0 || index >= reminders.size()) return;
        reminders.remove(index);
        saveReminders();
    }

    public int clearFinishedReminders() {
        int before = reminders.size();
        for (int i = reminders.size() - 1; i >= 0; i--) {
            if (reminders.get(i).done) reminders.remove(i);
        }
        saveReminders();
        return before - reminders.size();
    }

    public List<Reminder> takeDue(long now) {
        List<Reminder> due = new ArrayList<>();
        for (Reminder r : reminders) {
            if (!r.done && r.due > 0 && r.due <= now) {
                r.done = true;
                due.add(r);
            }
        }
        if (!due.isEmpty()) {
            lastFired = due.get(due.size() - 1);
            saveReminders();
        }
        return due;
    }

    public int findReminder(String query) {
        String[] qw = TextUtil.words(TextUtil.clean(query));
        if (qw.length == 0) return -1;
        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < reminders.size(); i++) {
            String[] tw = TextUtil.words(TextUtil.clean(reminders.get(i).text));
            int hit = 0;
            for (String a : qw) {
                for (String b : tw) {
                    if (TextUtil.similar(a, b)) {
                        hit++;
                        break;
                    }
                }
            }
            double score = (double) hit / qw.length;
            if (reminders.get(i).done) score -= 0.05;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return bestScore >= 0.6 ? best : -1;
    }

    public void reschedule(int index, long due) {
        if (index < 0 || index >= reminders.size()) return;
        Reminder r = reminders.get(index);
        r.due = due;
        r.done = false;
        saveReminders();
    }

    public Reminder next() {
        for (Reminder r : reminders) {
            if (!r.done && r.due > 0) return r;
        }
        return null;
    }

    public String agendaFor(long dayStart) {
        long end = dayStart + 24L * 3600 * 1000;
        List<String> lines = new ArrayList<>();
        for (Reminder r : reminders) {
            if (!r.done && r.due >= dayStart && r.due < end) {
                lines.add("- " + r.text + " (" + new java.text.SimpleDateFormat(user.settings.use24Hour() ? "HH:mm" : "h:mm a").format(new java.util.Date(r.due)) + ")");
            }
        }
        String name = TimeParser.dayName(dayStart);
        if (lines.isEmpty()) return "You have no reminders for " + name + ".";
        return "Reminders for " + name + ":\n" + String.join("\n", lines);
    }

    public String readReminders() {
        StringBuilder sb = new StringBuilder("Your reminders:");
        boolean any = false;
        for (int i = 0; i < reminders.size(); i++) {
            Reminder r = reminders.get(i);
            any = true;
            sb.append("\n").append(i + 1).append(". ").append(r.text).append("  (")
                    .append(r.done ? "done" : TimeParser.describe(r.due, user.settings.use24Hour())).append(")");
        }
        return any ? sb.toString() : "You have no reminders. Try \"remind me to call mom in 30 minutes\".";
    }

    public String agenda() {
        StringBuilder sb = new StringBuilder();
        int left = pendingCount();
        long endOfDay = endOfToday();
        List<String> today = new ArrayList<>();
        for (Reminder r : reminders) {
            if (!r.done && r.due > 0 && r.due <= endOfDay) {
                today.add("- " + r.text + " (" + TimeParser.describe(r.due, user.settings.use24Hour()) + ")");
            }
        }
        if (left == 0 && today.isEmpty()) return "Nothing on your plate right now. Enjoy the free time!";
        sb.append("Here's your day:");
        if (!today.isEmpty()) {
            sb.append("\nReminders for today:");
            for (String s : today) sb.append("\n").append(s);
        }
        if (left > 0) {
            sb.append("\nYou have ").append(left).append(left == 1 ? " task" : " tasks").append(" to do:");
            int shown = 0;
            for (Todo t : todos) {
                if (!t.done && shown < 5) {
                    sb.append("\n- ").append(t.text);
                    shown++;
                }
            }
            if (left > 5) sb.append("\n... and ").append(left - 5).append(" more");
        }
        return sb.toString();
    }

    private static long endOfToday() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 23);
        c.set(java.util.Calendar.MINUTE, 59);
        c.set(java.util.Calendar.SECOND, 59);
        return c.getTimeInMillis();
    }
}
