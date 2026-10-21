import java.util.HashMap;
import java.util.Map;
import java.util.Random;

// every feeling word has a score, a "not" before it flips the score
public class MoodDetector {

    public static final String SAD = "sad", STRESS = "stress", ANGRY = "angry", TIRED = "tired",
            LONELY = "lonely", BORED = "bored", HAPPY = "happy";

    public static class Mood {
        public final String type;
        public final int score;

        Mood(String type, int score) {
            this.type = type;
            this.score = score;
        }

        public boolean isNegative() {
            return !type.equals(HAPPY);
        }
    }

    private static final Map<String, Integer> SCORES = new HashMap<>();
    private static final Map<String, String> TYPES = new HashMap<>();

    private static void word(String w, String type, int score) {
        SCORES.put(w, score);
        TYPES.put(w, type);
    }

    static {
        for (String w : new String[]{"sad", "unhappy", "upset", "cry", "crying", "hurt", "heartbroken", "down", "miserable"}) word(w, SAD, -2);
        for (String w : new String[]{"depressed", "hopeless", "worthless"}) word(w, SAD, -3);
        // a few bangla words written in english letters
        for (String w : new String[]{"kharap", "dukkho", "dukhi", "mankharap"}) word(w, SAD, -2);
        for (String w : new String[]{"chinta", "bhoy"}) word(w, STRESS, -2);
        for (String w : new String[]{"ragi", "rag"}) word(w, ANGRY, -2);
        for (String w : new String[]{"klanto", "ghumabo"}) word(w, TIRED, -2);
        for (String w : new String[]{"ekla", "eka"}) word(w, LONELY, -2);
        for (String w : new String[]{"khushi", "anondo"}) word(w, HAPPY, 2);
        word("bhalo", HAPPY, 1);
        for (String w : new String[]{"stressed", "anxious", "worried", "nervous", "tension", "panic", "overwhelmed", "scared", "afraid"}) word(w, STRESS, -2);
        for (String w : new String[]{"angry", "mad", "furious", "annoyed", "irritated", "frustrated", "hate"}) word(w, ANGRY, -2);
        for (String w : new String[]{"tired", "sleepy", "exhausted", "drained"}) word(w, TIRED, -2);
        for (String w : new String[]{"lonely", "alone", "isolated"}) word(w, LONELY, -2);
        for (String w : new String[]{"bored", "boring"}) word(w, BORED, -1);
        for (String w : new String[]{"happy", "excited", "awesome", "amazing", "glad", "delighted", "thrilled", "proud", "wonderful", "fantastic"}) word(w, HAPPY, 2);
        for (String w : new String[]{"good", "great", "fine", "nice", "relaxed", "calm", "better", "cheerful", "blessed", "lucky"}) word(w, HAPPY, 1);
    }

    public static Mood detect(String clean) {
        String[] w = TextUtil.words(clean);
        boolean firstPerson = clean.matches(".*\\b(i am|i feel|i feeling|feeling|im|i m|i have been|i got|i get|so|very|really|too)\\b.*");
        int total = 0;
        String type = null;
        int strongest = 0;
        for (int i = 0; i < w.length; i++) {
            Integer sc = SCORES.get(w[i]);
            if (sc == null) {
                for (String key : SCORES.keySet()) {
                    if (key.length() >= 6 && w[i].length() >= 6 && TextUtil.similar(w[i], key)) {
                        sc = SCORES.get(key);
                        w[i] = key;
                        break;
                    }
                }
            }
            if (sc == null) continue;
            String t = TYPES.get(w[i]);
            boolean negated = (i > 0 && isNegation(w[i - 1])) || (i > 1 && isNegation(w[i - 2]) && !w[i - 1].equals("and"));
            if (negated) {
                sc = -sc;
                t = sc > 0 ? HAPPY : SAD;
            }
            total += sc;
            if (Math.abs(sc) >= Math.abs(strongest)) {
                strongest = sc;
                type = t;
            }
        }
        if (type == null) return null;
        if (Math.abs(total) < 2 && !(firstPerson && Math.abs(total) >= 1)) return null;
        if (clean.matches("^(how|why|what|when|where|who|can|could|do|does|is|are|will|tell|show|give).*") && !firstPerson) return null;
        if (total == 0) return null;
        if (total < 0 && type.equals(HAPPY)) type = SAD;
        return new Mood(total > 0 ? HAPPY : type, total);
    }

    private static boolean isNegation(String w) {
        return w.equals("not") || w.equals("never") || w.equals("no") || w.equals("hardly") || w.equals("dont");
    }

    private static final Random rnd = new Random();

    private static final Map<String, String[]> OPENERS = new HashMap<>();
    private static final Map<String, String[]> COMFORT = new HashMap<>();

    static {
        OPENERS.put(SAD, new String[]{"I'm sorry you're feeling low, %s. Want to talk about it?",
                "That sounds heavy, %s. I'm here. Do you want to tell me what happened?"});
        OPENERS.put(STRESS, new String[]{"That sounds stressful, %s. What's weighing on you the most?",
                "Take a breath, %s. Want to tell me what's worrying you?"});
        OPENERS.put(ANGRY, new String[]{"That sounds really frustrating, %s. Do you want to vent about it?",
                "I get it, %s. Want to tell me what made you so upset?"});
        OPENERS.put(TIRED, new String[]{"You sound worn out, %s. Have you been getting enough rest?",
                "Long day, %s? Want to talk, or would a short break help more?"});
        OPENERS.put(LONELY, new String[]{"I'm here with you, %s. You're not alone right now. Want to talk?",
                "Feeling lonely is hard, %s. I'm happy to keep you company. What's on your mind?"});
        OPENERS.put(BORED, new String[]{"Bored, %s? I can tell you a joke, a fun fact, or flip a coin. Pick one!",
                "Let's fix that, %s. Want a joke, a fun fact, or some motivation?"});

        COMFORT.put(SAD, new String[]{
                "I'm really sorry. Things feel heavy now, but they do get lighter. You've got through hard days before.",
                "It's okay to feel down sometimes. Be gentle with yourself and take it one step at a time.",
                "Even the darkest night ends with a sunrise. You're doing your best, and that matters.",
                "Thank you for telling me. If it keeps hurting, please talk to someone you trust too."});
        COMFORT.put(STRESS, new String[]{
                "Try splitting it into small pieces and doing just the first one. I can add those pieces to your to-do list if you like.",
                "Breathe in for 4 seconds, hold for 4, breathe out for 4. Then pick the single most urgent thing.",
                "You don't have to solve everything today. A short walk or some water can also help clear your head."});
        COMFORT.put(ANGRY, new String[]{
                "It's fine to be angry. Give it a few minutes before reacting, a short walk or deep breaths can help.",
                "That would upset anyone. Writing down what you feel can take some of the heat out of it.",
                "I'm listening. Once you've cooled down a bit, we can think about what to do next."});
        COMFORT.put(TIRED, new String[]{
                "Rest is productive too. Even a 20 minute break can bring your energy back.",
                "Drink some water, stretch a little and get a proper sleep tonight. Your work can wait a bit.",
                "You've been pushing hard. Be kind to yourself and slow down for a while."});
        COMFORT.put(LONELY, new String[]{
                "I'm glad you told me. Maybe message a friend or family member today, even a small hello counts.",
                "You matter, and people care about you more than you may feel right now. I'm here whenever you want to chat.",
                "Feeling alone doesn't mean you are alone. Want me to remind you to call someone later?"});
    }

    private static final String[] HAPPY_STRONG = {
            "That's wonderful, %s! What made you so happy?",
            "Love to hear that, %s! Tell me what happened!"};
    private static final String[] HAPPY_MILD = {
            "Glad to hear that, %s!",
            "Nice! Keep that energy going, %s."};
    private static final String[] HAPPY_CELEBRATE = {
            "Yay! That made my day too!",
            "That's awesome to hear! I'm smiling with you!",
            "So happy for you! Keep spreading that joy!",
            "Moments like these are precious. Enjoy every bit of it!"};

    public static String opener(Mood m, String name) {
        if (m.type.equals(HAPPY)) {
            return String.format(pick(m.score >= 2 ? HAPPY_STRONG : HAPPY_MILD), name);
        }
        return String.format(pick(OPENERS.get(m.type)), name);
    }

    public static String comfort(String type) {
        String[] list = COMFORT.get(type);
        return list == null ? pick(COMFORT.get(SAD)) : pick(list);
    }

    public static String celebrate() {
        return pick(HAPPY_CELEBRATE);
    }

    private static String pick(String[] arr) {
        return arr[rnd.nextInt(arr.length)];
    }
}
