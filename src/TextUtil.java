import java.util.Arrays;

public class TextUtil {

    public static String clean(String s) {
        if (s == null) return "";
        s = s.toLowerCase().replace('’', '\'').replace('‘', '\'');
        s = s.replace("can't", "cannot").replace("won't", "will not");
        s = s.replace("n't", " not").replace("'re", " are").replace("'ll", " will");
        s = s.replace("'ve", " have").replace("'m", " am").replace("'d", " would").replace("'s", " is");
        s = s.replace("to-do", "todo");
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) ? c : ' ');
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    public static String[] words(String clean) {
        if (clean.isEmpty()) return new String[0];
        return clean.split(" ");
    }

    public static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    // short words must match exactly or date/data get mixed up
    public static boolean similar(String a, String b) {
        if (a.equals(b)) return true;
        int len = Math.min(a.length(), b.length());
        if (len < 4) return false;
        int dist = distance(a, b);
        if (len == 4) {
            if (dist != 1) return false;
            if (a.length() != b.length()) return true;
            char[] x = a.toCharArray();
            char[] y = b.toCharArray();
            Arrays.sort(x);
            Arrays.sort(y);
            return Arrays.equals(x, y);
        }
        if (len < 8) return dist <= 1;
        return dist <= 2;
    }

    public static boolean isYes(String clean) {
        return clean.matches("(yes|yeah|yep|yup|ya|sure|ok|okay|of course|please|haan|ji|go ahead|why not)( .*)?");
    }

    public static boolean isNo(String clean) {
        return clean.matches("(no|nope|nah|na|not now|nothing|never mind|cancel|stop)( .*)?");
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String trimContent(String s) {
        s = s.trim();
        while (s.length() > 1 && (s.startsWith("\"") || s.startsWith("'") || s.startsWith("“"))) {
            s = s.substring(1).trim();
        }
        while (s.length() > 1 && (s.endsWith("\"") || s.endsWith("'") || s.endsWith("”")
                || s.endsWith(".") || s.endsWith("!") || s.endsWith("?"))) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    // so one record stays on one line in the txt files
    public static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("|", "\\p");
    }

    public static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                if (n == 'n') sb.append('\n');
                else if (n == 'p') sb.append('|');
                else sb.append(n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
