import java.io.*;
import java.util.Properties;

// per user settings in data/<user>/settings.properties
public class Settings {
    private final File file;
    private final Properties props = new Properties();

    public Settings(File file) {
        this.file = file;
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(new InputStreamReader(in, "UTF-8"));
            } catch (IOException e) {
                System.out.println("Could not load settings: " + e.getMessage());
            }
        }
    }

    public String get(String key, String def) {
        return props.getProperty(key, def);
    }

    public boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(def)));
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void set(String key, String value) {
        props.setProperty(key, value);
    }

    public void set(String key, boolean value) {
        props.setProperty(key, String.valueOf(value));
    }

    public void set(String key, int value) {
        props.setProperty(key, String.valueOf(value));
    }

    public void save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            props.store(new OutputStreamWriter(out, "UTF-8"), "Elf user settings");
        } catch (IOException e) {
            System.out.println("Could not save settings: " + e.getMessage());
        }
    }

    public String botName() {
        return get("botName", "Elf");
    }

    public boolean darkTheme() {
        return "dark".equals(get("theme", "light"));
    }

    public int fontSize() {
        return getInt("fontSize", 14);
    }

    public boolean use24Hour() {
        return getBool("clock24", false);
    }

    public boolean typingDelay() {
        return getBool("typingDelay", true);
    }

    public boolean reminderSound() {
        return getBool("reminderSound", true);
    }

    public void resetToDefault() {
        String nick = props.getProperty("nickname");
        props.clear();
        if (nick != null) props.setProperty("nickname", nick);
    }
}
