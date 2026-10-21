import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// vault.meta = salt|hash|keySalt, vault.txt = one encrypted entry per line
public class Vault {
    public static final String PASSWORD = "password", PASSKEY = "passkey";
    public static final int MIN_PASSKEY_LENGTH = 4;

    public static class Entry {
        public String service;
        public String kind;
        public final Map<String, String> fields = new LinkedHashMap<>();

        public Entry(String service, String kind) {
            this.service = service;
            this.kind = kind;
        }

        public String title() {
            StringBuilder sb = new StringBuilder();
            for (String w : service.split(" ")) {
                if (w.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(TextUtil.capitalize(w));
            }
            return sb.toString();
        }

        String serialize() {
            StringBuilder sb = new StringBuilder(TextUtil.escape(kind) + "|" + TextUtil.escape(service));
            for (Map.Entry<String, String> e : fields.entrySet()) {
                sb.append('|').append(TextUtil.escape(e.getKey())).append('|').append(TextUtil.escape(e.getValue()));
            }
            return sb.toString();
        }

        static Entry parse(String line) {
            String[] p = line.split("\\|", -1);
            if (p.length < 2) return null;
            Entry e = new Entry(TextUtil.unescape(p[1]), TextUtil.unescape(p[0]));
            for (int i = 2; i + 1 < p.length; i += 2) e.fields.put(TextUtil.unescape(p[i]), TextUtil.unescape(p[i + 1]));
            return e;
        }
    }

    private final File dataFile;
    private final File metaFile;
    private byte[] key;
    private final List<Runnable> listeners = new ArrayList<>();

    public Vault(User user) {
        dataFile = user.file("vault.txt");
        metaFile = user.file("vault.meta");
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    private void changed() {
        for (Runnable r : listeners) r.run();
    }

    public boolean isSetUp() {
        return metaFile.exists();
    }

    public boolean isUnlocked() {
        return key != null;
    }

    private String[] meta() {
        List<String> lines = FileStore.read(metaFile);
        if (lines.isEmpty()) return null;
        String[] p = lines.get(0).split("\\|");
        return p.length == 3 ? p : null;
    }

    public String setUp(String passkey) {
        if (passkey.length() < MIN_PASSKEY_LENGTH) return "The passkey needs at least " + MIN_PASSKEY_LENGTH + " characters.";
        String salt = Crypto.newSalt();
        String keySalt = Crypto.newSalt();
        List<String> lines = new ArrayList<>();
        lines.add(salt + "|" + Crypto.hash(passkey, salt) + "|" + keySalt);
        FileStore.write(metaFile, lines);
        key = Crypto.deriveKey(passkey, keySalt);
        changed();
        return null;
    }

    public boolean unlock(String passkey) {
        String[] m = meta();
        if (m == null || !Crypto.hash(passkey, m[0]).equals(m[1])) return false;
        key = Crypto.deriveKey(passkey, m[2]);
        changed();
        return true;
    }

    public void lock() {
        if (key == null) return;
        key = null;
        changed();
    }

    public boolean checkPasskey(String passkey) {
        String[] m = meta();
        return m != null && Crypto.hash(passkey, m[0]).equals(m[1]);
    }

    public String changePasskey(String oldPass, String newPass, String confirm) {
        if (!checkPasskey(oldPass)) return "The current passkey is wrong.";
        if (newPass.length() < MIN_PASSKEY_LENGTH) return "The new passkey needs at least " + MIN_PASSKEY_LENGTH + " characters.";
        if (!newPass.equals(confirm)) return "The new passkeys do not match.";
        // encrypt everything again with the new key
        unlock(oldPass);
        List<Entry> all = entries();
        setUp(newPass);
        writeAll(all);
        return null;
    }

    public void reset() {
        dataFile.delete();
        metaFile.delete();
        key = null;
        changed();
    }

    // counts lines only, so it works while locked
    public int count() {
        return FileStore.read(dataFile).size();
    }

    public List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        if (key == null) return list;
        for (String line : FileStore.read(dataFile)) {
            String plain = Crypto.decrypt(line, key);
            Entry e = plain == null ? null : Entry.parse(plain);
            if (e != null) list.add(e);
        }
        Collections.sort(list, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                return a.service.compareToIgnoreCase(b.service);
            }
        });
        return list;
    }

    private void writeAll(List<Entry> list) {
        List<String> lines = new ArrayList<>();
        for (Entry e : list) lines.add(Crypto.encrypt(e.serialize(), key));
        FileStore.write(dataFile, lines);
        changed();
    }

    public Entry get(String service) {
        for (Entry e : entries()) {
            if (e.service.equalsIgnoreCase(service.trim())) return e;
        }
        return null;
    }

    public Entry findIn(String sentence) {
        String[] words = TextUtil.words(TextUtil.clean(sentence));
        Entry best = null;
        int bestScore = 0;
        for (Entry e : entries()) {
            String[] sw = TextUtil.words(TextUtil.clean(e.service));
            if (sw.length == 0) continue;
            int hit = 0;
            for (String s : sw) {
                for (String w : words) {
                    if (TextUtil.similar(w, s)) {
                        hit++;
                        break;
                    }
                }
            }
            if (hit == sw.length) {
                int score = sw.length * 100 + e.service.length();
                if (score > bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
        }
        return best;
    }

    public boolean save(String service, String kind, Map<String, String> fields) {
        service = service.trim().toLowerCase();
        List<Entry> all = entries();
        Entry target = null;
        for (Entry e : all) {
            if (e.service.equalsIgnoreCase(service)) target = e;
        }
        boolean existed = target != null;
        if (target == null) {
            target = new Entry(service, kind);
            all.add(target);
        }
        target.fields.putAll(fields);
        target.kind = target.fields.containsKey("password") || !target.fields.containsKey("passkey") ? PASSWORD : PASSKEY;
        writeAll(all);
        return existed;
    }

    public void replace(String oldService, Entry entry) {
        List<Entry> all = entries();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).service.equalsIgnoreCase(oldService)) all.remove(i);
        }
        entry.service = entry.service.trim().toLowerCase();
        all.add(entry);
        writeAll(all);
    }

    public boolean delete(String service) {
        List<Entry> all = entries();
        boolean found = false;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).service.equalsIgnoreCase(service)) {
                all.remove(i);
                found = true;
            }
        }
        if (found) writeAll(all);
        return found;
    }

    public boolean deleteField(String service, String field) {
        List<Entry> all = entries();
        for (Entry e : all) {
            if (e.service.equalsIgnoreCase(service) && e.fields.remove(field) != null) {
                if (e.fields.isEmpty()) all.remove(e);
                writeAll(all);
                return true;
            }
        }
        return false;
    }
}
