import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// finds a time in a sentence ("in 10 minutes", "tomorrow at 9") and gives back the text that is left
public class TimeParser {

    public static class Result {
        public String text;
        public long due;
        public boolean hasClock;
    }

    private static final String[] DAYS = {"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};

    private static final Pattern RELATIVE = Pattern.compile(
            "\\b(?:in|for|after)\\s+(a|an|half an|half a|\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DAY_WORD = Pattern.compile(
            "\\b(day after tomorrow|tomorrow|today|tonight|porshu|kal|ajke|(?:on\\s+|next\\s+|this\\s+)?(sunday|monday|tuesday|wednesday|thursday|friday|saturday))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK = Pattern.compile(
            "\\b(?:(at|by|around)\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?|tay|ta|tar)?(?:\\b|(?<=\\.))(?![\\d:])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PART_OF_DAY = Pattern.compile(
            "\\b(?:in the |this |at )?(morning|afternoon|evening|night|noon|midnight|shokal|dupur|bikel|shondha|rat)\\b", Pattern.CASE_INSENSITIVE);

    public static Result parse(String input, long now) {
        Result res = new Result();
        String s = " " + input.trim() + " ";

        Matcher m = RELATIVE.matcher(s);
        if (m.find()) {
            double qty = quantity(m.group(1));
            long unit = unitMillis(m.group(2));
            res.due = now + (long) (qty * unit);
            res.hasClock = true;
            res.text = tidy(s.substring(0, m.start()) + " " + s.substring(m.end()));
            return res;
        }

        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(now);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);

        boolean hasDay = false;
        int dayOffset = 0;
        int hour = -1, minute = 0;
        boolean guessAmPm = false;

        m = DAY_WORD.matcher(s);
        if (m.find()) {
            String w = m.group(1).toLowerCase();
            hasDay = true;
            if (w.equals("tomorrow") || w.equals("kal")) dayOffset = 1;
            else if (w.equals("day after tomorrow") || w.equals("porshu")) dayOffset = 2;
            else if (w.equals("tonight")) hour = 21;
            else if (m.group(2) != null) {
                int target = indexOfDay(m.group(2));
                int today = t.get(Calendar.DAY_OF_WEEK) - 1;
                dayOffset = (target - today + 7) % 7;
                if (dayOffset == 0 && w.startsWith("next")) dayOffset = 7;
            }
            s = s.substring(0, m.start()) + " " + s.substring(m.end());
        }

        m = CLOCK.matcher(s);
        while (m.find()) {
            boolean hasColon = m.group(3) != null;
            String ampm = m.group(4);
            if (ampm != null && ampm.matches("(?i)tay|ta|tar")) ampm = null;
            else if (m.group(1) == null && ampm == null && !hasColon) continue;
            int h = Integer.parseInt(m.group(2));
            int min = hasColon ? Integer.parseInt(m.group(3)) : 0;
            if (h > 23 || min > 59) continue;
            if (ampm != null) {
                if (h > 12 || h == 0) continue;
                boolean pm = ampm.toLowerCase().startsWith("p");
                hour = (h % 12) + (pm ? 12 : 0);
            } else if (h <= 12) {
                hour = h % 12;
                guessAmPm = true;
            } else {
                hour = h;
            }
            minute = min;
            s = s.substring(0, m.start()) + " " + s.substring(m.end());
            break;
        }

        if (hour < 0) {
            m = PART_OF_DAY.matcher(s);
            if (m.find()) {
                String p = m.group(1).toLowerCase();
                if (p.equals("morning") || p.equals("shokal")) hour = 9;
                else if (p.equals("afternoon") || p.equals("bikel")) hour = 15;
                else if (p.equals("evening") || p.equals("shondha")) hour = 18;
                else if (p.equals("night") || p.equals("rat")) hour = 21;
                else if (p.equals("dupur")) hour = 13;
                else if (p.equals("noon")) hour = 12;
                else hour = 0;
                s = s.substring(0, m.start()) + " " + s.substring(m.end());
            }
        }

        if (!hasDay && hour < 0) {
            res.text = tidy(input);
            res.due = 0;
            return res;
        }
        res.hasClock = hour >= 0;
        // only a day was given, use 9 am
        if (hour < 0) hour = 9;

        t.add(Calendar.DAY_OF_YEAR, dayOffset);
        // "at 5" with no am/pm: take whichever 5 comes first from now
        if (guessAmPm) {
            long best = Long.MAX_VALUE;
            for (int add = 0; add <= 12; add += 12) {
                Calendar c = (Calendar) t.clone();
                c.set(Calendar.HOUR_OF_DAY, hour + add);
                c.set(Calendar.MINUTE, minute);
                long when = c.getTimeInMillis();
                if (when > now && when < best) best = when;
            }
            if (best == Long.MAX_VALUE) {
                t.add(Calendar.DAY_OF_YEAR, 1);
                t.set(Calendar.HOUR_OF_DAY, hour);
                t.set(Calendar.MINUTE, minute);
                best = t.getTimeInMillis();
            }
            res.due = best;
        } else {
            t.set(Calendar.HOUR_OF_DAY, hour);
            t.set(Calendar.MINUTE, minute);
            // time already passed today and no day given = tomorrow
            if (!hasDay && t.getTimeInMillis() <= now) t.add(Calendar.DAY_OF_YEAR, 1);
            res.due = t.getTimeInMillis();
        }
        res.text = tidy(s);
        return res;
    }

    public static long parseDay(String input, long now) {
        Matcher m = DAY_WORD.matcher(" " + input + " ");
        if (!m.find()) return -1;
        String w = m.group(1).toLowerCase();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(now);
        int offset = 0;
        if (w.equals("tomorrow") || w.equals("kal")) offset = 1;
        else if (w.equals("day after tomorrow") || w.equals("porshu")) offset = 2;
        else if (m.group(2) != null) {
            int today = t.get(Calendar.DAY_OF_WEEK) - 1;
            offset = (indexOfDay(m.group(2)) - today + 7) % 7;
            if (offset == 0 && w.startsWith("next")) offset = 7;
        }
        t.add(Calendar.DAY_OF_YEAR, offset);
        t.set(Calendar.HOUR_OF_DAY, 0);
        t.set(Calendar.MINUTE, 0);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        return t.getTimeInMillis();
    }

    public static String dayName(long dayStart) {
        Calendar now = Calendar.getInstance();
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(dayStart);
        int diff = dayNumber(d) - dayNumber(now);
        if (diff == 0) return "today";
        if (diff == 1) return "tomorrow";
        return new SimpleDateFormat("EEEE d MMM").format(new Date(dayStart));
    }

    private static double quantity(String q) {
        q = q.toLowerCase();
        if (q.equals("a") || q.equals("an")) return 1;
        if (q.startsWith("half")) return 0.5;
        return Double.parseDouble(q);
    }

    private static long unitMillis(String u) {
        u = u.toLowerCase();
        if (u.startsWith("sec")) return 1000L;
        if (u.startsWith("min")) return 60L * 1000;
        if (u.startsWith("h")) return 3600L * 1000;
        return 24L * 3600 * 1000;
    }

    private static int indexOfDay(String name) {
        for (int i = 0; i < DAYS.length; i++) {
            if (DAYS[i].equalsIgnoreCase(name)) return i;
        }
        return 0;
    }

    private static String tidy(String s) {
        s = s.trim().replaceAll("\\s+", " ");
        s = s.replaceAll("(?i)^(to|about|that|me to|me about)\\s+", "");
        s = s.replaceAll("(?i)\\s+(at|on|by|in|around|next|this|for)$", "");
        s = s.replaceAll("(?i)^(at|on|by|in|around)\\s+", "");
        s = s.replaceAll("[,;\\s]+$", "");
        s = TextUtil.trimContent(s);
        return TextUtil.capitalize(s);
    }

    public static String describe(long due, boolean use24h) {
        if (due <= 0) return "No time set";
        Calendar now = Calendar.getInstance();
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(due);
        String clock = new SimpleDateFormat(use24h ? "HH:mm" : "h:mm a").format(new Date(due));
        int diff = dayNumber(d) - dayNumber(now);
        if (diff == 0) return "Today, " + clock;
        if (diff == 1) return "Tomorrow, " + clock;
        if (diff == -1) return "Yesterday, " + clock;
        return new SimpleDateFormat("EEE d MMM, ").format(new Date(due)) + clock;
    }

    private static int dayNumber(Calendar c) {
        return c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR);
    }
}
