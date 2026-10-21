import java.io.File;
import java.util.ArrayList;
import java.util.List;

// chat_history.txt, one line per message: time|U or B|text
public class ChatLog {

    public static class Entry {
        public final long time;
        public final boolean fromUser;
        public final String text;

        Entry(long time, boolean fromUser, String text) {
            this.time = time;
            this.fromUser = fromUser;
            this.text = text;
        }
    }

    private final File file;

    public ChatLog(User user) {
        file = user.file("chat_history.txt");
    }

    public void add(boolean fromUser, String text) {
        FileStore.append(file, System.currentTimeMillis() + "|" + (fromUser ? "U" : "B") + "|" + TextUtil.escape(text));
    }

    public List<Entry> load(int lastN) {
        List<Entry> all = new ArrayList<>();
        for (String line : FileStore.read(file)) {
            String[] p = line.split("\\|", 3);
            if (p.length < 3) continue;
            try {
                all.add(new Entry(Long.parseLong(p[0]), p[1].equals("U"), TextUtil.unescape(p[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        int from = Math.max(0, all.size() - lastN);
        return new ArrayList<>(all.subList(from, all.size()));
    }

    public int userMessageCount() {
        int n = 0;
        for (String line : FileStore.read(file)) {
            if (line.contains("|U|")) n++;
        }
        return n;
    }

    public void clear() {
        FileStore.write(file, new ArrayList<String>());
    }
}
