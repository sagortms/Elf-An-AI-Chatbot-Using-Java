import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// reads account details from any sentence. finds label words (password, user name, phone ...),
// takes the text after each one as its value, and what is left over is the service name
public class VaultParser {

    public static class Parsed {
        public String service;
        public final Map<String, String> fields = new LinkedHashMap<>();

        public boolean hasSecret() {
            return fields.containsKey("password") || fields.containsKey("passkey");
        }

        public String kind() {
            return !fields.containsKey("password") && fields.containsKey("passkey") ? Vault.PASSKEY : Vault.PASSWORD;
        }
    }

    // first item = name we store, the rest are words people type
    private static final String[][] LABELS = {
            {"username", "user name", "username", "user id", "userid", "login id", "login name", "login", "user", "id"},
            {"password", "password", "passwd", "pass word", "pass", "pwd"},
            {"passkey", "passkey", "pass key", "passcode", "pin code", "pin", "secret code", "security code"},
            {"email", "email address", "email id", "e-mail", "email", "mail id", "mail"},
            {"phone", "phone number", "mobile number", "contact number", "phone no", "mobile no", "cell number", "phone", "mobile", "contact"},
            {"website", "website", "web address", "url", "link", "site"},
            {"account number", "account number", "account no", "acc no", "acc number"},
            {"card number", "card number", "card no"},
            {"note", "note", "notes", "hint", "remark", "security question", "recovery code"},
    };

    public static String display(String key) {
        switch (key) {
            case "username": return "Username";
            case "password": return "Password";
            case "passkey": return "Passkey";
            case "email": return "Email";
            case "phone": return "Phone number";
            case "website": return "Website";
            case "account number": return "Account number";
            case "card number": return "Card number";
            default: return TextUtil.capitalize(key);
        }
    }

    private static final Pattern LABEL_PATTERN;
    private static final Map<String, String> LABEL_KEY = new LinkedHashMap<>();

    static {
        List<String> phrases = new ArrayList<>();
        for (String[] row : LABELS) {
            for (int i = 1; i < row.length; i++) {
                phrases.add(row[i]);
                LABEL_KEY.put(row[i], row[0]);
            }
        }
        // longest first so "phone number" wins over "phone"
        java.util.Collections.sort(phrases, new java.util.Comparator<String>() {
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });
        StringBuilder sb = new StringBuilder("(?i)\\b(?:");
        for (int i = 0; i < phrases.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(phrases.get(i).replace(" ", "\\s+").replace("-", "\\-"));
        }
        sb.append(")\\b");
        LABEL_PATTERN = Pattern.compile(sb.toString());
    }

    private static class Label {
        final int start, end;
        final String key;
        String value = "";
        String service;

        Label(int start, int end, String key) {
            this.start = start;
            this.end = end;
            this.key = key;
        }
    }

    private static final Pattern SAVE_VERBS = Pattern.compile(
            "^(?:please\\s+)?(?:(?:can|could|would|will)\\s+you\\s+)?(?:please\\s+)?"
                    + "(?:save|store|add|remember|note|keep|write down|put|record|log|update|change|set|reset|new)\\s+"
                    + "(?:this\\s+|that\\s+|down\\s+|it\\s+)?(?:in(?:to)?\\s+(?:the\\s+|my\\s+)?(?:vault|password manager|passwords?)\\s*[:,-]?\\s*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONNECTOR = Pattern.compile(
            "^[\\s:=,>\\-]*((?:is|are|was|will be|should be|to|as)\\b|[:=])?[\\s:=,>\\-]*", Pattern.CASE_INSENSITIVE);

    public static Set<String> labelsIn(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = LABEL_PATTERN.matcher(text);
        while (m.find()) found.add(keyOf(m.group()));
        return found;
    }

    private static String keyOf(String phrase) {
        return LABEL_KEY.get(phrase.toLowerCase().replaceAll("\\s+", " "));
    }

    public static Parsed parseSave(String text) {
        String s = text.trim();
        Matcher lead = SAVE_VERBS.matcher(s);
        if (lead.find()) s = s.substring(lead.end());
        s = s.replaceAll("(?i)\\s+(?:in|to|into)\\s+(?:the\\s+|my\\s+)?(?:vault|password manager|passwords?)[.!]*$", "");
        s = s.replaceFirst("(?i)^(?:my|the|our)\\s+", "");

        List<Label> labels = new ArrayList<>();
        Matcher m = LABEL_PATTERN.matcher(s);
        while (m.find()) labels.add(new Label(m.start(), m.end(), keyOf(m.group())));
        if (labels.isEmpty()) return null;

        // if a value doesnt fit its label (phone without digits) it was just normal text, so drop the label
        boolean dropped = true;
        while (dropped && !labels.isEmpty()) {
            dropped = false;
            fillValues(s, labels);
            for (int i = 0; i < labels.size(); i++) {
                if (!valid(labels.get(i))) {
                    labels.remove(i);
                    dropped = true;
                    break;
                }
            }
        }
        if (labels.isEmpty()) return null;

        Parsed p = new Parsed();
        for (Label l : labels) {
            if (!p.fields.containsKey(l.key)) p.fields.put(l.key, l.value);
            if (p.service == null && l.service != null) p.service = l.service;
        }
        if (p.service == null) p.service = serviceBefore(s, labels.get(0));
        if (p.service != null) {
            p.service = p.service.replaceAll("[\"']", "").trim().toLowerCase();
            if (p.service.isEmpty() || p.service.length() > 30) p.service = null;
        }
        return p;
    }

    private static void fillValues(String s, List<Label> labels) {
        for (int i = 0; i < labels.size(); i++) {
            Label l = labels.get(i);
            int stop = i + 1 < labels.size() ? labels.get(i + 1).start : s.length();
            String region = s.substring(l.end, stop);
            l.value = "";
            l.service = null;

            Matcher c = CONNECTOR.matcher(region);
            c.find();
            boolean hadConnector = c.group(1) != null;
            String r = region.substring(c.end());

            // "password for gmail is 1234"
            Matcher sc = Pattern.compile("^(?:for|of|on|in|at)\\s+(?:my\\s+|the\\s+|our\\s+)?(.+?)\\s+(?:is|are|=|:)\\s+(.+)$",
                    Pattern.CASE_INSENSITIVE).matcher(r);
            Matcher sc2 = Pattern.compile("^(?:for|of|on|in|at)\\s+(?:my\\s+|the\\s+|our\\s+)?(.+)$", Pattern.CASE_INSENSITIVE).matcher(r);
            if (sc.find() && sc.group(1).split("\\s+").length <= 3) {
                l.service = sc.group(1);
                r = sc.group(2);
            } else if (sc2.find()) {
                String tail = sc2.group(1).trim();
                String[] parts = tail.split("\\s+", 2);
                boolean valueBefore = i == 0 && valueBeforeLabel(s, l) != null;
                if (valueBefore || parts.length == 1) {
                    l.service = tail;
                    r = "";
                } else {
                    l.service = parts[0];
                    r = parts[1];
                }
            }
            r = tidyValue(r);

            if (l.service == null && !r.isEmpty() && !l.key.equals("note")) {
                Matcher tail = Pattern.compile("^(.+?)\\s+(?:for|of|on)\\s+(?:my\\s+|the\\s+)?([\\w.@+\\-]+(?:\\s\\w+){0,2})$",
                        Pattern.CASE_INSENSITIVE).matcher(r);
                if (tail.find()) {
                    r = tail.group(1);
                    l.service = tail.group(2);
                }
            }
            // "1234 is the password for gmail", value is in front
            if (r.isEmpty() && i == 0) {
                String[] before = valueBeforeLabel(s, l);
                if (before != null) {
                    r = before[0];
                    if (l.service == null && !before[1].isEmpty()) l.service = before[1];
                }
            }
            // "save password gmail 1234", no words in between
            if (!hadConnector && l.service == null && !r.isEmpty() && i == 0
                    && (l.key.equals("password") || l.key.equals("passkey"))
                    && s.substring(0, l.start).trim().isEmpty()) {
                String[] parts = r.split("\\s+");
                if (parts.length == 2) {
                    parts = r.split("\\s+", 2);
                    l.service = parts[0];
                    r = parts[1];
                }
            }
            l.value = r;
        }
    }

    private static final Pattern VALUE_BEFORE = Pattern.compile(
            "^(?:.*\\s)?(\\S+)\\s+(?:is|are|was)\\s+(?:my\\s+|the\\s+|our\\s+)?((?:\\S+\\s*){0,3})$", Pattern.CASE_INSENSITIVE);

    private static String[] valueBeforeLabel(String s, Label first) {
        Matcher m = VALUE_BEFORE.matcher(s.substring(0, first.start));
        if (m.find()) return new String[]{m.group(1).replaceAll("[\"',.]", ""), m.group(2).trim()};
        return null;
    }

    private static String tidyValue(String r) {
        r = r.trim();
        r = r.replaceAll("(?i)^(?:and|also|plus)\\s+", "");
        String before;
        do {
            before = r;
            r = r.replaceAll("(?i)(?:^|\\s)(?:and|also|plus|&|my|the|recovery|backup|alternate|secondary|registered|linked)[\\s,;.]*$", "");
        } while (!r.equals(before));
        r = r.replaceAll("[\\s,;.]+$", "");
        r = r.replaceAll("^[\"']|[\"']$", "");
        return r.trim();
    }

    private static boolean valid(Label l) {
        String v = l.value;
        if (v.isEmpty()) return false;
        switch (l.key) {
            case "phone":
                return v.replaceAll("[^0-9]", "").length() >= 5 && v.matches("[+()\\d\\s\\-]+");
            case "email":
                return v.matches("\\S+@\\S+\\.\\S+");
            case "website":
                return v.matches("(?i)(https?://|www\\.)?\\S+\\.\\S+");
            case "account number":
            case "card number":
                return v.replaceAll("[^0-9]", "").length() >= 4;
            default:
                return true;
        }
    }

    // "my facebook password" -> facebook
    private static String serviceBefore(String s, Label first) {
        String pre = s.substring(0, first.start).trim();
        pre = pre.replaceAll("(?i)\\s+(?:is|are|was)\\s+(?:my\\s+|the\\s+|our\\s+)?$", " ");
        pre = pre.replaceAll("(?i)^(?:my|the|our|a|an|this|for|of|on|in)\\s+", "");
        pre = pre.replaceAll("(?i)'s$", "").replaceAll("(?i)'s\\s", " ");
        pre = pre.replaceAll("(?i)\\b(?:account|accounts|login|logins|details|detail|data|info|information|credentials)\\b", " ");
        pre = pre.replaceAll("[:,;\\-]+\\s*$", "").replaceAll("(?i)\\s+(?:and|is|are|for)\\s*$", "");
        pre = pre.trim().replaceAll("\\s+", " ");
        if (pre.isEmpty() || pre.split(" ").length > 3) return null;
        return pre;
    }
}
