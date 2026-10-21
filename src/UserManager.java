import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserManager {
    public static final File DATA_DIR = new File("data");
    private static final File USERS_FILE = new File(DATA_DIR, "users.txt");

    public static final String SAMPLE_USER = "mutant";
    public static final String SAMPLE_NAME = "Mutant Freeman";
    public static final String SAMPLE_PASS = "mutant1234";
    public static final String SAMPLE_PASSKEY = "1234";

    private static class Record {
        String username, fullName, salt, hash, created;

        String toLine() {
            return username + "|" + TextUtil.escape(fullName) + "|" + salt + "|" + hash + "|" + created;
        }
    }

    public static void init() {
        if (!DATA_DIR.exists()) DATA_DIR.mkdirs();
        // first run: make the demo account
        if (!USERS_FILE.exists()) {
            createAccount(SAMPLE_USER, SAMPLE_NAME, SAMPLE_PASS);
            seedSampleData();
        }
    }

    private static List<Record> loadAll() {
        List<Record> list = new ArrayList<>();
        for (String line : FileStore.read(USERS_FILE)) {
            String[] p = line.split("\\|", -1);
            if (p.length < 5) continue;
            Record r = new Record();
            r.username = p[0];
            r.fullName = TextUtil.unescape(p[1]);
            r.salt = p[2];
            r.hash = p[3];
            // date is always the last field
            r.created = p[p.length - 1];
            list.add(r);
        }
        return list;
    }

    private static void saveAll(List<Record> list) {
        List<String> lines = new ArrayList<>();
        for (Record r : list) lines.add(r.toLine());
        FileStore.write(USERS_FILE, lines);
    }

    private static Record find(List<Record> list, String username) {
        for (Record r : list) {
            if (r.username.equalsIgnoreCase(username)) return r;
        }
        return null;
    }

    public static boolean exists(String username) {
        return find(loadAll(), username) != null;
    }

    public static String register(String username, String fullName, String password, String confirm) {
        username = username.trim().toLowerCase();
        fullName = fullName.trim().replaceAll("\\s+", " ");
        if (fullName.length() < 2) return "Please enter your full name.";
        if (!username.matches("[a-z0-9_.]{3,20}")) {
            return "Username must be 3-20 characters: letters, numbers, dot or underscore.";
        }
        if (password.length() < 6) return "Password must be at least 6 characters.";
        if (!password.equals(confirm)) return "The two passwords do not match.";
        if (exists(username)) return "That username is already taken.";
        createAccount(username, fullName, password);
        return null;
    }

    private static void createAccount(String username, String fullName, String password) {
        Record r = new Record();
        r.username = username;
        r.fullName = fullName;
        r.salt = Crypto.newSalt();
        r.hash = Crypto.hash(password, r.salt);
        r.created = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        List<Record> list = loadAll();
        list.add(r);
        saveAll(list);
        new File(DATA_DIR, username).mkdirs();
    }

    public static User login(String username, String password) {
        Record r = find(loadAll(), username.trim());
        if (r == null || !Crypto.hash(password, r.salt).equals(r.hash)) return null;
        User u = new User(r.username, r.fullName, r.created, new File(DATA_DIR, r.username));
        u.dir.mkdirs();
        return u;
    }

    public static void updateFullName(User user, String newName) {
        List<Record> list = loadAll();
        Record r = find(list, user.username);
        if (r == null) return;
        r.fullName = newName.trim().replaceAll("\\s+", " ");
        user.fullName = r.fullName;
        saveAll(list);
    }

    public static String changePassword(User user, String oldPass, String newPass, String confirm) {
        List<Record> list = loadAll();
        Record r = find(list, user.username);
        if (r == null || !Crypto.hash(oldPass, r.salt).equals(r.hash)) return "Current password is wrong.";
        if (newPass.length() < 6) return "New password must be at least 6 characters.";
        if (!newPass.equals(confirm)) return "The new passwords do not match.";
        r.salt = Crypto.newSalt();
        r.hash = Crypto.hash(newPass, r.salt);
        saveAll(list);
        return null;
    }

    public static boolean checkPassword(User user, String password) {
        Record r = find(loadAll(), user.username);
        return r != null && Crypto.hash(password, r.salt).equals(r.hash);
    }

    public static void deleteAccount(User user) {
        List<Record> list = loadAll();
        Record r = find(list, user.username);
        if (r != null) list.remove(r);
        saveAll(list);
        deleteFolder(user.dir);
    }

    private static void deleteFolder(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteFolder(k);
        }
        f.delete();
    }

    private static void seedSampleData() {
        File dir = new File(DATA_DIR, SAMPLE_USER);
        long now = System.currentTimeMillis();
        List<String> todo = new ArrayList<>();
        todo.add("0|AI project final at 10 AM");
        todo.add("0|Web project final at 12:30 PM");
        todo.add("0|Finish the AI assignment");
        todo.add("1|Install JDK");
        FileStore.write(new File(dir, "to-do.txt"), todo);

        List<String> rem = new ArrayList<>();
        rem.add((now + 24L * 3600 * 1000) + "|0|Submit lab report");
        FileStore.write(new File(dir, "reminders.txt"), rem);

        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        List<String> mem = new ArrayList<>();
        mem.add(today + "|I have some work left in AI");
        mem.add(today + "|Tell my friend about the lab schedule");
        FileStore.write(new File(dir, "memories.txt"), mem);

        // demo vault, passkey 1234
        User demo = new User(SAMPLE_USER, SAMPLE_NAME, today, dir);
        Vault vault = new Vault(demo);
        vault.setUp(SAMPLE_PASSKEY);
        Map<String, String> fb = new LinkedHashMap<>();
        fb.put("username", "mutant");
        fb.put("password", "1234");
        fb.put("phone", "01912345678");
        vault.save("facebook", Vault.PASSWORD, fb);
        Map<String, String> gm = new LinkedHashMap<>();
        gm.put("email", "mutant@example.com");
        gm.put("password", "sample1234");
        vault.save("gmail", Vault.PASSWORD, gm);
        Map<String, String> lock = new LinkedHashMap<>();
        lock.put("passkey", "4321");
        vault.save("phone lock", Vault.PASSKEY, lock);
        vault.lock();
    }
}
