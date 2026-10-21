import java.io.File;

public class User {
    public final String username;
    public String fullName;
    public final String createdOn;
    public final File dir;
    public final Settings settings;
    public User(String username, String fullName, String createdOn, File dir) {
        this.username = username;
        this.fullName = fullName;
        this.createdOn = createdOn;
        this.dir = dir;
        this.settings = new Settings(new File(dir, "settings.properties"));
    }

    public File file(String name) {
        return new File(dir, name);
    }

    public String firstName() {
        String[] parts = fullName.trim().split("\\s+");
        return parts.length == 0 ? username : parts[0];
    }

    // nickname if there is one, else first name
    public String displayName() {
        String nick = settings.get("nickname", "").trim();
        return nick.isEmpty() ? firstName() : nick;
    }

    public String initials() {
        String[] parts = fullName.trim().split("\\s+");
        String s = parts[0].substring(0, 1);
        if (parts.length > 1) s += parts[parts.length - 1].substring(0, 1);
        return s.toUpperCase();
    }
}
