import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// memories and life story, one line each: date|text
public class MemoryManager {
    private final File memFile;
    private final File storyFile;
    private final List<Runnable> listeners = new ArrayList<>();

    public static class Entry {
        public final String date;
        public final String text;

        Entry(String date, String text) {
            this.date = date;
            this.text = text;
        }

        public String toString() {
            return date + "  -  " + text;
        }
    }

    public MemoryManager(User user) {
        memFile = user.file("memories.txt");
        storyFile = user.file("life_story.txt");
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    private void changed() {
        for (Runnable r : listeners) r.run();
    }

    private List<Entry> load(File f) {
        List<Entry> list = new ArrayList<>();
        for (String line : FileStore.read(f)) {
            int bar = line.indexOf('|');
            if (bar == 10) list.add(new Entry(line.substring(0, bar), TextUtil.unescape(line.substring(bar + 1))));
            else list.add(new Entry("", TextUtil.unescape(line)));
        }
        return list;
    }

    private void save(File f, List<Entry> list) {
        List<String> lines = new ArrayList<>();
        for (Entry e : list) lines.add(e.date + "|" + TextUtil.escape(e.text));
        FileStore.write(f, lines);
        changed();
    }

    public List<Entry> memories() {
        return load(memFile);
    }

    public List<Entry> lifeStory() {
        return load(storyFile);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    public String addMemory(String text) {
        FileStore.append(memFile, today() + "|" + TextUtil.escape(text));
        changed();
        return "Okay, I'll remember that.";
    }

    public String addLifeStory(String text) {
        FileStore.append(storyFile, today() + "|" + TextUtil.escape(text));
        changed();
        return "Saved. That's now part of your life story.";
    }

    public void deleteMemory(int index) {
        List<Entry> list = memories();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            save(memFile, list);
        }
    }

    public void deleteLifeStory(int index) {
        List<Entry> list = lifeStory();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            save(storyFile, list);
        }
    }

    public void clearMemories() {
        save(memFile, new ArrayList<Entry>());
    }

    public String recall() {
        List<Entry> list = memories();
        if (list.isEmpty()) return "I don't have anything in memory yet. Tell me \"remember that ...\" and I'll keep it.";
        StringBuilder sb = new StringBuilder("Here is what I remember:");
        for (int i = 0; i < list.size(); i++) sb.append("\n").append(i + 1).append(". ").append(list.get(i).text);
        return sb.toString();
    }

    public String readLifeStory() {
        List<Entry> list = lifeStory();
        if (list.isEmpty()) return "Your life story is empty. Say \"life story: ...\" to add a chapter.";
        StringBuilder sb = new StringBuilder("Your life story so far:");
        for (Entry e : list) sb.append("\n- ").append(e.text);
        return sb.toString();
    }

    // notes that share words with the question
    public List<Entry> search(String query) {
        String[] qw = TextUtil.words(TextUtil.clean(query));
        List<Entry> hits = new ArrayList<>();
        List<Entry> all = memories();
        all.addAll(lifeStory());
        for (Entry e : all) {
            String[] ew = TextUtil.words(TextUtil.clean(e.text));
            int match = 0;
            for (String q : qw) {
                if (q.length() < 3 || isFiller(q)) continue;
                for (String w : ew) {
                    if (TextUtil.similar(q, w)) {
                        match++;
                        break;
                    }
                }
            }
            if (match > 0) hits.add(e);
        }
        return hits;
    }

    private static boolean isFiller(String w) {
        return w.matches("the|and|for|you|about|that|this|what|did|tell|say|know|with|have|was|were|are|any|when|where|who|which|how|does|can|could|will|would|its|time|date|day|remember|from|your|not|has|had");
    }

    public int forget(String query) {
        List<Entry> keep = new ArrayList<>();
        int removed = 0;
        List<Entry> hits = search(query);
        for (Entry e : memories()) {
            boolean hit = false;
            for (Entry h : hits) {
                if (h.text.equals(e.text) && h.date.equals(e.date)) hit = true;
            }
            if (hit) removed++;
            else keep.add(e);
        }
        if (removed > 0) save(memFile, keep);
        return removed;
    }
}
