import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// order inside reply():
// tidy -> pending question -> commands -> calculator/knowledge -> mood -> memory question
// -> intents -> reminder offer -> fallback
public class ChatBotLogic {

    private final User user;
    private final ReminderManager tasks;
    private final MemoryManager memory;
    private final Vault vault;
    private final VaultChat vaultChat;
    private final Random rnd = new Random();

    private Runnable onBotRenamed;
    private Consumer<Boolean> onThemeRequest;

    // what Elf is waiting for from the user
    private enum Pending { NONE, RENAME, ASK_TASK, ASK_REMINDER, ASK_NOTE, CONFIRM_FORGET_ALL, CONFIRM_CLEAR_TASKS,
        CONFIRM_REMINDER, MOOD_ASKED, MOOD_LISTEN, HAPPY_ASKED }

    private Pending pending = Pending.NONE;
    private String moodType = MoodDetector.SAD;
    private String lastIntent = "";
    private int unknownCount = 0;
    private String offerText = "";
    private long offerDue = 0;
    // last thing added, so undo knows what to remove
    private String lastAddedType = "";
    private String lastAddedText = "";
    private long lastAddedDue = 0;

    // small talk and info requests are matched with these
    private static class Intent {
        final String name;
        final int weight;
        final int maxWords;
        final String[] phrases;

        Intent(String name, int weight, int maxWords, String... phrases) {
            this.name = name;
            this.weight = weight;
            this.maxWords = maxWords;
            this.phrases = phrases;
        }
    }

    private static final Intent[] INTENTS = {
            new Intent("greeting", 3, 5, "hi", "hello", "hey", "hii", "hiya", "yo", "howdy", "hola", "good morning", "good afternoon",
                    "good evening", "assalamualaikum", "salam", "namaste"),
            new Intent("howareyou", 3, 7, "how are you", "how r u", "how do you do", "how is it going", "how are things",
                    "whats up", "what is up", "kemon acho", "kemon aso", "kemon achen", "ki khobor", "ki obostha", "how you doing"),
            new Intent("botname", 3, 8, "your name", "who are you", "what are you", "introduce yourself", "about yourself", "what should i call you"),
            new Intent("username", 3, 8, "my name", "who am i", "do you know me", "know my name"),
            new Intent("aboutme", 3, 8, "what do you know about me", "tell me about me", "everything about me", "what have you saved"),
            new Intent("creator", 3, 9, "who made you", "who created you", "who built you", "who developed you", "who programmed you",
                    "your creator", "your developer"),
            new Intent("age", 3, 6, "how old are you", "your age", "when were you born"),
            new Intent("human", 3, 7, "are you human", "are you real", "are you a robot", "are you a bot", "are you ai", "are you a person"),
            new Intent("doing", 3, 5, "what are you doing", "wyd", "what you doing"),
            new Intent("time", 3, 8, "time", "what time", "clock", "current time", "koyta baje", "kota baje", "somoy koto"),
            new Intent("date", 3, 8, "date", "what day", "which day", "todays date", "day today", "current date", "what is today",
                    "ajker tarikh", "aj ki tarikh", "tarikh"),
            new Intent("weather", 3, 8, "weather", "temperature", "forecast", "raining"),
            new Intent("joke", 3, 7, "joke", "jokes", "funny", "make me laugh", "humor", "make me smile", "moja"),
            new Intent("fact", 3, 7, "fact", "facts", "trivia", "did you know", "something interesting"),
            new Intent("motivate", 3, 7, "motivate", "motivation", "inspire", "inspiration", "quote", "encourage me", "cheer me up", "pump me up"),
            new Intent("coin", 3, 7, "flip a coin", "toss a coin", "coin toss", "coin", "heads or tails", "toss"),
            new Intent("dice", 3, 7, "dice", "die", "roll a dice", "roll the dice", "roll a die"),
            new Intent("help", 3, 8, "help", "what can you do", "commands", "features", "how to use", "what do you do", "menu",
                    "instructions", "guide", "show me what you can do"),
            new Intent("forgotpw", 3, 8, "forgot password", "forgot my password", "forgot passkey", "forgot my passkey", "lost my password"),
            new Intent("thanks", 3, 8, "thanks", "thank you", "thx", "dhonnobad", "shukriya", "appreciate it", "ty", "good job",
                    "well done", "nice work"),
            new Intent("sorry", 3, 5, "sorry", "my bad", "apologies", "i apologize"),
            new Intent("bye", 3, 6, "bye", "goodbye", "see you", "good night", "take care", "tata", "allah hafez", "khoda hafez",
                    "talk later", "gotta go", "cya", "see you later"),
            new Intent("love", 3, 6, "i love you", "love you", "i like you", "you are the best", "you are awesome", "you are great"),
            new Intent("insult", 3, 5, "stupid", "dumb", "idiot", "useless", "you suck"),
            new Intent("laugh", 3, 3, "haha", "hehe", "lol", "lmao", "hahaha"),
            new Intent("ack", 2, 3, "ok", "okay", "cool", "nice", "fine", "alright", "hmm", "great", "wow", "oh", "accha", "thik ache"),
            new Intent("again", 3, 4, "again", "another one", "one more", "one more time", "more", "next")
    };

    private static final String[] VERBS = {"show", "list", "remind", "remember", "delete", "remove", "complete", "finished",
            "change", "rename", "calculate", "forget", "display", "cancel", "schedule", "snooze", "reschedule", "postpone", "unlock"};

    public ChatBotLogic(User user, ReminderManager tasks, MemoryManager memory, Vault vault) {
        this.user = user;
        this.tasks = tasks;
        this.memory = memory;
        this.vault = vault;
        this.vaultChat = new VaultChat(vault);
    }

    public void setOnBotRenamed(Runnable r) {
        onBotRenamed = r;
    }

    public void setOnThemeRequest(Consumer<Boolean> c) {
        onThemeRequest = c;
    }

    public String botName() {
        return user.settings.botName();
    }

    public boolean lastReplyWasSecret() {
        return vaultChat.secretReply;
    }

    public String logSafe(String text) {
        return vaultChat.logSafe(text);
    }

    public String reply(String input) {
        vaultChat.secretReply = false;
        String raw = input.trim();

        // passkey and yes/no for the vault are read exactly as typed
        if (vaultChat.isWaiting()) {
            String r = vaultChat.pendingAnswer(raw, TextUtil.clean(raw));
            if (r != null) return r;
        }

        String text = tidy(fixTypos(raw.replaceAll("\\s+", " ")));
        String clean = TextUtil.clean(text);
        if (clean.isEmpty()) return "I couldn't read that. Could you type it again?";
        String name = user.displayName();

        // did Elf ask something before? then this is the answer
        String r = answerPending(text, clean);
        if (r != null) return r;

        r = command(text, clean);
        if (r != null) {
            stopMoodTalk();
            return r;
        }

        r = Calculator.tryAnswer(text);
        if (r == null) r = KnowledgeBase.answer(clean);
        if (r == null) r = KnowledgeBase.aboutElf(clean);
        if (r != null) {
            stopMoodTalk();
            return r;
        }

        // still talking about feelings from before
        if (isMoodTalk()) {
            Intent strong = bestIntent(clean);
            if (TextUtil.isYes(clean) || TextUtil.isNo(clean) || strong == null || strong.name.equals("ack")) {
                boolean newFeeling = (pending == Pending.MOOD_ASKED || pending == Pending.HAPPY_ASKED) && MoodDetector.detect(clean) != null;
                if (!newFeeling) return moodFollowUp(clean);
            }
            stopMoodTalk();
        }

        MoodDetector.Mood mood = MoodDetector.detect(clean);
        if (mood != null) {
            if (mood.isNegative()) {
                moodType = mood.type;
                pending = mood.type.equals(MoodDetector.BORED) ? Pending.NONE : Pending.MOOD_ASKED;
            } else {
                pending = mood.score >= 2 ? Pending.HAPPY_ASKED : Pending.NONE;
            }
            lastIntent = "mood";
            unknownCount = 0;
            return MoodDetector.opener(mood, name);
        }

        r = memoryQuestion(text, clean);
        if (r != null) return r;

        Intent best = bestIntent(clean);
        if (best != null) {
            unknownCount = 0;
            String intentName = best.name;
            if (intentName.equals("again")) {
                if (lastIntent.matches("joke|fact|motivate|coin|dice")) intentName = lastIntent;
                else return "Again what? Ask me for a joke, fact or a quote first.";
            }
            String answer = answerIntent(intentName, clean);
            if (!intentName.equals("ack") && !intentName.equals("thanks")) lastIntent = intentName;
            return answer;
        }

        r = offerReminder(text, clean);
        if (r != null) return r;

        return fallback(text, clean);
    }

    private static final Pattern LEAD_WORDS = Pattern.compile(
            "^(?:hey|hi|hello|hii|ok|okay|so|well|um|uh|please|pls|plz|kindly|just|also|and|then|actually|btw)\\b[,!.\\s]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern POLITE = Pattern.compile(
            "^(?:(?:can|could|would|will) you\\s+(?:please\\s+|kindly\\s+)?|"
                    + "(?:i would like you to|i'd like you to|i want you to|i need you to|i wish you would|you should|go ahead and)\\s+|"
                    + "i (?:want|wanna|would like|'d like) to (?=(?:add|set|save|create|make|show|see|check|remember|note|delete|remove|schedule|list|know|ask)\\b))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TAIL_WORDS = Pattern.compile(
            "[\\s,]*(?:please|pls|plz|for me|thanks|thank you|thx)[\\s.!?]*$", Pattern.CASE_INSENSITIVE);

    // removes filler like "hey elf could you please ... for me"
    private String tidy(String text) {
        String t = text.trim();
        Pattern bot = Pattern.compile("^" + Pattern.quote(botName()) + "\\b[,:!\\s]*", Pattern.CASE_INSENSITIVE);
        for (int round = 0; round < 3; round++) {
            String before = t;
            t = strip(t, LEAD_WORDS);
            t = strip(t, bot);
            t = strip(t, POLITE);
            if (t.equals(before)) break;
        }
        Matcher m = TAIL_WORDS.matcher(t);
        if (m.find() && m.start() >= 3) t = t.substring(0, m.start());
        return t;
    }

    private String strip(String t, Pattern p) {
        Matcher m = p.matcher(t);
        if (m.find() && t.length() - m.end() >= 3) return t.substring(m.end());
        return t;
    }

    // only the first 3 words, so names inside a task dont get changed
    private String fixTypos(String text) {
        String[] parts = text.split(" ");
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            String w = parts[i].toLowerCase().replaceAll("[^a-z]", "");
            if (w.length() < 4 || java.util.Arrays.asList(VERBS).contains(w)) continue;
            for (String v : VERBS) {
                if (TextUtil.similar(w, v)) {
                    parts[i] = v;
                    break;
                }
            }
        }
        return String.join(" ", parts);
    }

    private String answerPending(String text, String clean) {
        Pending p = pending;
        switch (p) {
            case RENAME:
                pending = Pending.NONE;
                if (TextUtil.isNo(clean)) return "Okay, I'll keep my name as " + botName() + ".";
                return rename(TextUtil.trimContent(text));
            case ASK_TASK:
                pending = Pending.NONE;
                if (TextUtil.isNo(clean)) return "No problem, nothing added.";
                return addTasks(TextUtil.trimContent(text));
            case ASK_REMINDER:
                pending = Pending.NONE;
                if (TextUtil.isNo(clean)) return "Okay, no reminder then.";
                return makeReminder(text);
            case ASK_NOTE:
                pending = Pending.NONE;
                if (TextUtil.isNo(clean)) return "Okay, I won't save anything.";
                return saveNote(TextUtil.trimContent(text));
            case CONFIRM_REMINDER:
                pending = Pending.NONE;
                if (TextUtil.isYes(clean)) return addReminder(offerText, offerDue);
                return TextUtil.isNo(clean) ? "Okay, no reminder." : null;
            case CONFIRM_FORGET_ALL:
                pending = Pending.NONE;
                if (TextUtil.isYes(clean)) {
                    memory.clearMemories();
                    return "Done. I've cleared all my memories.";
                }
                return TextUtil.isNo(clean) ? "Okay, I kept everything." : null;
            case CONFIRM_CLEAR_TASKS:
                pending = Pending.NONE;
                if (TextUtil.isYes(clean)) {
                    tasks.clearTodos();
                    return "Your to-do list is now empty.";
                }
                return TextUtil.isNo(clean) ? "Okay, your list stays as it is." : null;
            default:
                return null;
        }
    }

    private boolean isMoodTalk() {
        return pending == Pending.MOOD_ASKED || pending == Pending.MOOD_LISTEN || pending == Pending.HAPPY_ASKED;
    }

    private void stopMoodTalk() {
        if (isMoodTalk()) pending = Pending.NONE;
    }

    private String moodFollowUp(String clean) {
        Pending p = pending;
        pending = Pending.NONE;
        if (p == Pending.HAPPY_ASKED) return MoodDetector.celebrate();
        if (p == Pending.MOOD_ASKED) {
            if (TextUtil.isYes(clean)) {
                pending = Pending.MOOD_LISTEN;
                return "I'm listening. Tell me what's on your mind.";
            }
            if (TextUtil.isNo(clean)) return "That's okay. I'm here whenever you want to talk. Want to hear a joke instead?";
        }
        return MoodDetector.comfort(moodType);
    }

    private static final Pattern[] NAME_PATTERNS = {
            Pattern.compile("^(?:please )?(?:change|set|update) your name (?:to|as) (.+)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:please )?rename (?:you|yourself)(?: to| as)? ?(.*)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:please )?(?:i will |i want to |i would like to )?call you (?:as |by )?(.+)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^your new name is (.+)$", Pattern.CASE_INSENSITIVE),
    };
    private static final Pattern CALL_ME = Pattern.compile(
            "^(?:please )?(?:call me|you can call me|my name is|i am called|my nickname is) (.+)$", Pattern.CASE_INSENSITIVE);

    private static final String TASK_WORD = "(?:task|todo|to-do|to do|item|errand|chore)";
    private static final String LIST_WORD = "(?:todo|to-do|to do|task|tasks|checklist|list)";
    private static final Pattern[] TASK_ADD = {
            Pattern.compile("^(?:please )?(?:add|put|create|insert|save|make|write) (?:a |an |the )?(?:new )?" + TASK_WORD + "s?"
                    + "(?: (?:to|in|into) (?:my )?" + LIST_WORD + "(?: list)?)?[:\\- ]+(.+)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:please )?add (.+?) (?:to|into|in|on) (?:my )?" + LIST_WORD + "(?: list)?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:put|write|note|jot) (.+?) (?:in|on|into|to) (?:my )?" + LIST_WORD + "(?: list)?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:todo|to-do|tasks?)\\s*[:\\-]\\s*(.+)$", Pattern.CASE_INSENSITIVE),
    };
    private static final Pattern TASK_NATURAL = Pattern.compile(
            "^i (?:have to|need to|must|got to|gotta|should|ought to|am supposed to|have got to) (.+)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern DONE_NUM1 = Pattern.compile(
            "^(?:please )?(?:mark )?(?:task |todo )?(?:no\\.? ?|number |#)?(\\d+) (?:as )?(?:done|complete|completed|finished)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DONE_NUM2 = Pattern.compile(
            "^(?:please )?(?:done|complete|finish|tick|check off|mark)(?: task| todo| number)? ?(?:no\\.? ?|number |#)?(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DONE_TEXT = Pattern.compile(
            "^(?:i )?(?:have )?(?:just )?(?:finished|completed|done|did) (?:with )?(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DONE_TEXT2 = Pattern.compile(
            "^(?:(?:tick|cross|check|strike) off|mark) (.+?)(?: (?:as )?(?:done|complete|completed|finished))?$|"
                    + "^(.+?) (?:is|are|was) (?:done|finished|complete|completed)$|^i(?: am|'m) done with (.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNDO_NUM = Pattern.compile(
            "^(?:undo|reopen|uncheck|unmark)(?: task| todo)? ?#?(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_DELETE = Pattern.compile(
            "^(?:please )?(?:delete|remove|drop|cancel)(?: the)?(?: task| todo| to-do) ?(?:no\\.? ?|number |#)?(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_DELETE = Pattern.compile(
            "^(?:delete|remove|drop|cancel|scrap|erase) (?:the |my )?(.+?)(?: from (?:my )?" + LIST_WORD + "s?(?: list)?)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNDO_LAST = Pattern.compile(
            "^(?:undo|undo (?:that|it)|cancel (?:that|it)|never ?mind|delete (?:that|it)|remove (?:that|it)|take (?:that|it) back|scratch that|oops)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REMIND = Pattern.compile(
            "^(?:remind me|reminder|(?:set|create|make|add|schedule) (?:a |an |one )?(?:new )?reminder)\\b[:\\s]*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMIND_BN = Pattern.compile(
            "^(?:amake|amay)\\s+(.+?)\\s+mone\\s+kor(?:i|e)(?:ye)?\\s+(?:dio|daw|diyo|dis|den)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALERT = Pattern.compile(
            "^(alert|notify|ping|buzz|nudge|wake|tell|let) me(?: up)?(?: to| about| that| know)?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DONT_FORGET = Pattern.compile(
            "^(?:(?:don't|dont|do not) (?:let me )?forget|make sure i|i (?:don't|dont|do not) want to forget)(?: to| about)?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIMER = Pattern.compile(
            "^(?:set (?:a |an )?(?:timer|alarm)(?: for)?|timer|alarm)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMINDER_DELETE = Pattern.compile(
            "^(?:delete|remove|cancel|clear|drop) (?:my |the )?reminders?(?: (?:about|for|to|of))? ?(?:no\\.? ?|number |#)?(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SNOOZE = Pattern.compile(
            "^(?:snooze(?: it)?(?: for| by)?|remind me again(?: in)?|(?:later|not now|ask me later)(?:,? remind me)?(?: in)?)\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESCHEDULE = Pattern.compile(
            "^(?:reschedule|postpone|move|change|update|shift|delay)(?: my)?(?: the)? reminder(?: number| no\\.?| #)? ?(\\d+|.+?)\\s+(?:to|for|until|till|at)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REMEMBER = Pattern.compile(
            "^(?:remember|note|keep in mind|write down|jot down|memorize|memorise|note to self|note down|save a note)(?: this| that| down)?(?: that)?\\s*[:\\-,]?\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MAKE_NOTE = Pattern.compile(
            "^(?:make|take|add) a note(?: that| of| about)?\\s*[:\\-]?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_MEMORY = Pattern.compile(
            "^(?:do you remember|what do you remember about|what did i (?:tell|say to) you about|what do you know about|what did i say about|did i tell you about) (.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FORGET = Pattern.compile("^forget (?:about |that )?(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_NOTE = Pattern.compile(
            "^(?:delete|remove|erase) (?:the |my )?(?:note|memory)(?: about| on| of)? (.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIFE_STORY = Pattern.compile(
            "^(?:add to (?:my )?life ?story|life ?story|my life ?story (?:is|:)?)\\s*[:\\-]?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FACT = Pattern.compile(
            "^my (?:birthday|anniversary|exam|test|quiz|class|presentation|interview|deadline|wedding|flight|appointment|meeting|favou?rite \\w+|"
                    + "address|roll|student id|mother|father|mom|dad|brother|sister|best friend|teacher|room|hostel|bus|train)\\b.*?\\b(?:is|are|was|will be|starts|ends|at|on)\\b.+$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RANDOM_NUMBER = Pattern.compile(
            "(?:random number|pick a number)(?: (?:between|from) (-?\\d+) (?:and|to) (-?\\d+))?", Pattern.CASE_INSENSITIVE);

    private static final String DAYS = "today|tomorrow|day after tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday";

    private String command(String text, String clean) {
        String r;
        if ((r = nameCommands(text, clean)) != null) return r;
        if ((r = reminderCommands(text, clean)) != null) return r;
        if ((r = taskCommands(text, clean)) != null) return r;
        if ((r = agendaCommands(text, clean)) != null) return r;

        Matcher m = UNDO_LAST.matcher(text);
        if (m.matches()) return undoLast();

        // vault before notes, otherwise "my gmail password is x" would be saved as a note
        if ((r = vaultChat.handle(text, clean)) != null) return r;

        if ((r = deleteByText(text)) != null) return r;
        if ((r = memoryCommands(text, clean)) != null) return r;

        m = RANDOM_NUMBER.matcher(text);
        if (m.find()) {
            int lo = 1, hi = 100;
            if (m.group(1) != null) {
                lo = Integer.parseInt(m.group(1));
                hi = Integer.parseInt(m.group(2));
                if (lo > hi) {
                    int t = lo;
                    lo = hi;
                    hi = t;
                }
            }
            return "Your number is " + (lo + rnd.nextInt(hi - lo + 1)) + ". (between " + lo + " and " + hi + ")";
        }
        m = Pattern.compile("\\b(dark|light) (?:mode|theme)\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find() && clean.split(" ").length <= 6) {
            boolean dark = m.group(1).equalsIgnoreCase("dark");
            if (dark == user.settings.darkTheme()) return "I'm already in " + (dark ? "dark" : "light") + " mode.";
            if (onThemeRequest != null) onThemeRequest.accept(dark);
            return "Switching to " + (dark ? "dark" : "light") + " mode.";
        }
        return null;
    }

    private String nameCommands(String text, String clean) {
        Matcher m;
        for (Pattern p : NAME_PATTERNS) {
            m = p.matcher(text);
            if (m.matches()) {
                String wanted = TextUtil.trimContent(m.group(1));
                if (wanted.isEmpty() || wanted.toLowerCase().matches("(by )?(another|a new|a different|new|some other|something else)( name)?")) {
                    pending = Pending.RENAME;
                    return "Sure! What name would you like to give me?";
                }
                return rename(wanted);
            }
        }
        if (clean.matches(".*\\b(change your name|rename you|rename yourself|give you a new name|new name for you|call you by another name)\\b.*")) {
            pending = Pending.RENAME;
            return "Sure! What name would you like to give me?";
        }
        m = CALL_ME.matcher(text);
        if (m.matches()) {
            String nick = TextUtil.trimContent(m.group(1));
            if (nick.length() > 25 || nick.split(" ").length > 3) return null;
            nick = TextUtil.capitalize(nick);
            user.settings.set("nickname", nick);
            user.settings.save();
            return "Nice to meet you, " + nick + "! I'll call you that from now on.";
        }
        return null;
    }

    private String rename(String wanted) {
        wanted = wanted.trim();
        if (wanted.isEmpty() || wanted.length() > 20 || !wanted.matches("[\\p{L}0-9 _.\\-]+")) {
            return "Please pick a simple name (letters and numbers, up to 20 characters).";
        }
        wanted = TextUtil.capitalize(wanted);
        user.settings.set("botName", wanted);
        user.settings.save();
        if (onBotRenamed != null) onBotRenamed.run();
        return "Okay, from now on call me " + wanted + "!";
    }

    private String reminderCommands(String text, String clean) {
        Matcher m;

        m = SNOOZE.matcher(text);
        if (m.matches()) {
            ReminderManager.Reminder last = tasks.lastFired;
            if (last == null) return "There's no reminder to snooze right now.";
            String when = m.group(1).trim();
            long due = System.currentTimeMillis() + 10 * 60 * 1000;
            if (!when.isEmpty()) {
                TimeParser.Result t = TimeParser.parse(when, System.currentTimeMillis());
                if (t.due == 0) t = TimeParser.parse("in " + when.replaceAll("(?i)^(for|in)\\s+", ""), System.currentTimeMillis());
                if (t.due > 0) due = t.due;
            }
            return addReminder(last.text, due);
        }

        m = RESCHEDULE.matcher(text);
        if (m.matches()) {
            int idx = m.group(1).matches("\\d+") ? Integer.parseInt(m.group(1)) - 1 : tasks.findReminder(m.group(1));
            if (idx < 0 || idx >= tasks.reminders().size()) return "I couldn't find that reminder. Say \"show reminders\" to see the numbers.";
            TimeParser.Result t = TimeParser.parse(m.group(2), System.currentTimeMillis());
            if (t.due == 0) return "When should I move it to? For example \"tomorrow at 9 am\".";
            String what = tasks.reminders().get(idx).text;
            tasks.reschedule(idx, t.due);
            return "Moved \"" + what + "\" to " + TimeParser.describe(t.due, user.settings.use24Hour()) + ".";
        }

        if (clean.matches("(clear|delete|remove) (all )?(done|finished|completed|old) reminders")) {
            return "Removed " + tasks.clearFinishedReminders() + " finished reminder(s).";
        }
        m = REMINDER_DELETE.matcher(text);
        if (m.matches()) {
            String which = m.group(1).trim();
            int idx = which.matches("\\d+") ? Integer.parseInt(which) - 1 : tasks.findReminder(which);
            if (idx < 0 || idx >= tasks.reminders().size()) return "I couldn't find that reminder. Say \"show reminders\" to see the list.";
            String gone = tasks.reminders().get(idx).text;
            tasks.deleteReminder(idx);
            return "Deleted the reminder: " + gone;
        }
        if (clean.matches("(when is |what is |whats )?(my )?next reminder( is)?")) {
            ReminderManager.Reminder next = tasks.next();
            if (next == null) return "You have no upcoming reminders.";
            return "Your next reminder: " + next.text + " (" + TimeParser.describe(next.due, user.settings.use24Hour()) + ").";
        }
        if (clean.matches("((show|list|view|see|display|check|what are)( me)?( my| the| all)?( upcoming| pending)? )?reminders( list)?")
                || clean.matches("(what|which) reminders.*") || clean.matches("do i have (any )?(upcoming )?reminders") || clean.matches("any reminders")) {
            return tasks.readReminders();
        }

        m = REMIND.matcher(text);
        if (m.matches()) {
            String rest = m.group(1).trim();
            if (rest.isEmpty()) {
                pending = Pending.ASK_REMINDER;
                return "Sure. What should I remind you about, and when? (for example: call mom in 30 minutes)";
            }
            return makeReminder(rest);
        }
        m = REMIND_BN.matcher(text);
        if (m.matches()) return makeReminder(m.group(1));

        m = TIMER.matcher(text);
        if (m.matches()) {
            TimeParser.Result t = TimeParser.parse(m.group(1), System.currentTimeMillis());
            if (t.due == 0) t = TimeParser.parse("in " + m.group(1).replaceAll("(?i)^(for|in)\\s+", ""), System.currentTimeMillis());
            if (t.due == 0) return "How long should the timer run? For example \"set a timer for 10 minutes\".";
            return addReminder("Time's up!", t.due);
        }

        m = ALERT.matcher(text);
        if (m.matches()) {
            String verb = m.group(1).toLowerCase();
            TimeParser.Result t = TimeParser.parse(m.group(2), System.currentTimeMillis());
            if (t.due > 0 && (t.hasClock || !verb.equals("tell") && !verb.equals("let"))) {
                if (t.text.isEmpty()) t.text = verb.equals("wake") ? "Wake up" : "Reminder";
                return addReminder(t.text, t.due);
            }
        }

        m = DONT_FORGET.matcher(text);
        if (m.matches()) {
            TimeParser.Result t = TimeParser.parse(m.group(1), System.currentTimeMillis());
            if (t.due > 0 && !t.text.isEmpty()) return addReminder(t.text, t.due);
            return addTasks(TextUtil.trimContent(m.group(1))) + "\n(There was no time in it, so I put it on your to-do list.)";
        }
        return null;
    }

    private String makeReminder(String sentence) {
        TimeParser.Result t = TimeParser.parse(sentence, System.currentTimeMillis());
        if (t.text.isEmpty()) t.text = "Reminder";
        return addReminder(t.text, t.due);
    }

    private String addReminder(String text, long due) {
        lastAddedType = "reminder";
        lastAddedText = text;
        lastAddedDue = due;
        if (due > 0) return pick("Done. ", "Got it. ", "Sure. ", "") + tasks.addReminder(text, due);
        return tasks.addReminder(text, due);
    }

    private String taskCommands(String text, String clean) {
        Matcher m;

        if (clean.matches("add (a |an )?" + TASK_WORD) || clean.matches("new (task|todo)")) {
            pending = Pending.ASK_TASK;
            return "What is the task?";
        }
        for (Pattern p : TASK_ADD) {
            m = p.matcher(text);
            if (m.matches()) return addTasks(TextUtil.trimContent(m.group(1)));
        }
        if (isShowTasks(clean)) return tasks.readTodos();
        if (clean.matches("(clear|delete|remove) (all )?(done|finished|completed) (tasks|todos|items)")) {
            return "Removed " + tasks.clearDone() + " finished task(s).";
        }
        if (clean.matches("(clear|delete|remove|empty) (all )?(my )?(tasks|todos|todo list|task list)")) {
            if (tasks.todos().isEmpty()) return "Your to-do list is already empty.";
            pending = Pending.CONFIRM_CLEAR_TASKS;
            return "That will delete all " + tasks.todos().size() + " tasks. Are you sure? (yes / no)";
        }
        m = TASK_DELETE.matcher(text);
        if (m.matches()) return deleteTask(m.group(1).trim());
        for (Pattern p : new Pattern[]{DONE_NUM1, DONE_NUM2}) {
            m = p.matcher(text);
            if (m.matches()) return completeTask(Integer.parseInt(m.group(1)) - 1, true);
        }
        m = UNDO_NUM.matcher(text);
        if (m.matches()) return completeTask(Integer.parseInt(m.group(1)) - 1, false);
        m = DONE_TEXT.matcher(text);
        if (m.matches()) {
            int idx = tasks.findTodo(m.group(1));
            if (idx >= 0) return completeTask(idx, true);
        }
        m = DONE_TEXT2.matcher(text);
        if (m.matches()) {
            String what = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
            int idx = what == null ? -1 : tasks.findTodo(what);
            if (idx >= 0) return completeTask(idx, true);
        }
        if (clean.matches("how many (tasks|todos|todo|things).*")) {
            int left = tasks.pendingCount();
            return left == 0 ? "You have no pending tasks." : "You have " + left + " pending task(s) out of " + tasks.todos().size() + ".";
        }
        m = TASK_NATURAL.matcher(text);
        if (m.matches()) {
            String what = TextUtil.trimContent(m.group(1));
            if (!what.toLowerCase().matches("^(talk|tell|ask|know|say|speak|be honest|go now|leave now|sleep|cry|stop)\\b.*") && what.split(" ").length >= 2) {
                TimeParser.Result t = TimeParser.parse(what, System.currentTimeMillis());
                if (t.due > 0 && t.hasClock && !t.text.isEmpty()) {
                    String task = addTasks(t.text);
                    tasks.addReminder(t.text, t.due);
                    return task + "\nI'll also remind you at " + TimeParser.describe(t.due, user.settings.use24Hour()) + ".";
                }
                return addTasks(what) + "\nSay \"undo\" if you didn't want that.";
            }
        }
        return null;
    }

    // "buy milk, call dad and pay bill" becomes 3 tasks
    private String addTasks(String content) {
        List<String> items = new ArrayList<>();
        for (String part : content.split("\\s*[,;]\\s*|\\s+and then\\s+|\\s+then\\s+")) {
            for (String sub : splitAnd(part)) {
                sub = TextUtil.trimContent(sub);
                if (!sub.isEmpty()) items.add(sub);
            }
        }
        if (items.isEmpty()) return "What is the task?";
        if (items.size() == 1) {
            String item = TextUtil.capitalize(items.get(0));
            lastAddedType = "task";
            lastAddedText = item;
            return tasks.addTodo(item);
        }
        StringBuilder sb = new StringBuilder("Added " + items.size() + " tasks:");
        for (String item : items) {
            String it = TextUtil.capitalize(item);
            tasks.addTodo(it);
            sb.append("\n- ").append(it);
            lastAddedType = "task";
            lastAddedText = it;
        }
        return sb.toString();
    }

    // "call mom and pay bill" is 2 tasks but "buy milk and eggs" stays 1
    private List<String> splitAnd(String part) {
        List<String> out = new ArrayList<>();
        String[] halves = part.split("\\s+and\\s+");
        boolean allLong = halves.length > 1;
        for (String h : halves) {
            if (h.trim().split("\\s+").length < 2) allLong = false;
        }
        if (allLong) {
            for (String h : halves) out.add(h);
        } else {
            out.add(part);
        }
        return out;
    }

    private boolean isShowTasks(String clean) {
        return clean.matches("(show|list|view|see|display|check|open|read|give|tell)( me)?( my| the| all)?( pending| current| remaining)? (todo|todos|tasks|task|to do)( list| items)?")
                || clean.matches("(my|the|all|pending)( pending)? (todo|todos|tasks|todo list|task list|to do list)")
                || clean.matches("(todo|todos|tasks|todo list|task list)")
                || clean.matches("((show|view|see|open|check|read)( me)?( my| the)? )?(list|checklist)")
                || clean.matches("(any|do i have( any)?) (tasks|todos|pending tasks|pending work|work to do)( left| pending)?")
                || clean.matches(".*\\b(what do i have to do|what is left to do|what is on my list|what is pending|what should i do|what do i need to do|what is left|anything left to do|anything pending)\\b.*")
                || clean.matches(".*\\b(kaj|task|todo)\\b.*\\b(dekhao|dekhaw|dekha)\\b.*");
    }

    private String completeTask(int index, boolean done) {
        if (index < 0 || index >= tasks.todos().size()) {
            return tasks.todos().isEmpty() ? "Your to-do list is empty." : "There is no task number " + (index + 1) + ". You have " + tasks.todos().size() + ".";
        }
        String t = tasks.todos().get(index).text;
        tasks.setDone(index, done);
        if (!done) return "Okay, I reopened: " + t;
        int left = tasks.pendingCount();
        return "Nice work! Marked as done: " + t + (left == 0 ? "\nYour list is clear now." : "\n" + left + " left.");
    }

    private String deleteTask(String which) {
        int idx;
        if (which.matches("\\d+")) idx = Integer.parseInt(which) - 1;
        else idx = tasks.findTodo(which);
        if (idx < 0 || idx >= tasks.todos().size()) return "I couldn't find that task. Say \"show tasks\" to see the numbers.";
        String t = tasks.todos().get(idx).text;
        tasks.deleteTodo(idx);
        return "Deleted the task: " + t;
    }

    private String deleteByText(String text) {
        Matcher m = GENERIC_DELETE.matcher(text);
        if (!m.matches()) return null;
        String what = m.group(1);
        int idx = tasks.findTodo(what);
        if (idx >= 0) return deleteTask(String.valueOf(idx + 1));
        idx = tasks.findReminder(what);
        if (idx >= 0) {
            String gone = tasks.reminders().get(idx).text;
            tasks.deleteReminder(idx);
            return "Deleted the reminder: " + gone;
        }
        return null;
    }

    // removes whatever was added last
    private String undoLast() {
        if (lastAddedType.isEmpty()) return "There's nothing recent to undo.";
        String what = lastAddedText;
        String type = lastAddedType;
        lastAddedType = "";
        if (type.equals("task")) {
            for (int i = tasks.todos().size() - 1; i >= 0; i--) {
                if (tasks.todos().get(i).text.equalsIgnoreCase(what)) {
                    tasks.deleteTodo(i);
                    return "Okay, I removed the task: " + what;
                }
            }
        } else if (type.equals("reminder")) {
            for (int i = tasks.reminders().size() - 1; i >= 0; i--) {
                ReminderManager.Reminder r = tasks.reminders().get(i);
                if (r.text.equalsIgnoreCase(what) && r.due == lastAddedDue) {
                    tasks.deleteReminder(i);
                    return "Okay, I removed the reminder: " + what;
                }
            }
        } else if (type.equals("note")) {
            List<MemoryManager.Entry> all = memory.memories();
            for (int i = all.size() - 1; i >= 0; i--) {
                if (all.get(i).text.equalsIgnoreCase(what)) {
                    memory.deleteMemory(i);
                    return "Okay, I forgot that note.";
                }
            }
        }
        return "I couldn't find it anymore, it may already be gone.";
    }

    private String agendaCommands(String text, String clean) {
        if (clean.matches("(what is |whats )?(on|in) (my )?(agenda|schedule|plan|day)( today)?|(what do i have|what have i got|what is planned|what is happening|what is on)( for)? ?(today|my day|my schedule)?|"
                + "(my )?(agenda|schedule|daily summary|day summary|summary|plan for today|todays plan|today plan)")) {
            return tasks.agenda();
        }
        if (clean.matches(".*\\b(what|anything|something|plans?|schedule|agenda|free|busy|reminders?|tasks?|have i got|do i have)\\b.*\\b(" + DAYS + ")\\b.*")
                && !clean.matches("^(add|put|create|remind|set|i have to|i need to).*")
                && !clean.matches(".*\\b(add|remind me to|remember|date|day|time)\\b.*")) {
            long day = TimeParser.parseDay(text, System.currentTimeMillis());
            if (day >= 0) {
                if (TimeParser.dayName(day).equals("today")) return tasks.agenda();
                return tasks.agendaFor(day);
            }
        }
        return null;
    }

    private String memoryCommands(String text, String clean) {
        Matcher m;
        if (clean.matches("(take|make) a note|note this|remember this|remember that|remember something|save a note")) {
            pending = Pending.ASK_NOTE;
            return "Okay, what should I remember?";
        }
        if (clean.matches("(forget|delete|clear) (everything|all|all memories|all my memories|my memories)")) {
            if (memory.memories().isEmpty()) return "There's nothing in memory to forget.";
            pending = Pending.CONFIRM_FORGET_ALL;
            return "This will erase all " + memory.memories().size() + " memories. Are you sure? (yes / no)";
        }
        m = DELETE_NOTE.matcher(text);
        if (m.matches()) {
            int n = memory.forget(m.group(1));
            return n == 0 ? "I couldn't find a note about that." : "Done, I removed " + n + " note(s) about that.";
        }
        m = FORGET.matcher(text);
        if (m.matches()) {
            int n = memory.forget(m.group(1));
            return n == 0 ? "I couldn't find anything about that in my memory." : "Okay, I forgot " + n + " thing(s) about that.";
        }
        if (clean.matches("(what do you remember|what have i told you|what did i tell you|what do you know)|((show|list|read|open) (me )?(my |all )?(memories|notes|memory))|(my )?(memories|notes)")) {
            return memory.recall();
        }
        if (clean.matches("(show|read|tell|what is|what s|open)( me)?( my)? life ?story|my life ?story")) {
            return memory.readLifeStory();
        }
        m = SEARCH_MEMORY.matcher(text);
        if (m.matches() && !m.group(1).trim().matches("(?i)me|myself")) {
            List<MemoryManager.Entry> hits = memory.search(m.group(1));
            if (hits.isEmpty()) return "I don't remember anything about that. You can tell me with \"remember that ...\".";
            return listHits(hits);
        }
        m = LIFE_STORY.matcher(text);
        if (m.matches()) return memory.addLifeStory(TextUtil.trimContent(m.group(1)));
        for (Pattern p : new Pattern[]{REMEMBER, MAKE_NOTE}) {
            m = p.matcher(text);
            if (m.matches()) {
                String note = TextUtil.trimContent(m.group(1)).replaceAll("(?i)^(that|to) ", "");
                if (note.isEmpty()) break;
                return saveNote(TextUtil.capitalize(note));
            }
        }
        if (FACT.matcher(text).matches() && !text.trim().endsWith("?")) return saveNote(TextUtil.capitalize(TextUtil.trimContent(text)));
        return null;
    }

    // if the note has a time in it, offer a reminder too
    private String saveNote(String note) {
        String saved = memory.addMemory(note);
        lastAddedType = "note";
        lastAddedText = note;
        TimeParser.Result t = TimeParser.parse(note, System.currentTimeMillis());
        if (t.due > System.currentTimeMillis() && (t.hasClock || TimeParser.parseDay(note, System.currentTimeMillis()) >= 0)) {
            offerText = TextUtil.capitalize(t.text.replaceAll("(?i)\\s+(is|are|was|will be|starts|ends)$", ""));
            if (offerText.isEmpty()) offerText = note;
            offerDue = t.due;
            pending = Pending.CONFIRM_REMINDER;
            return saved + " Want a reminder for " + TimeParser.describe(t.due, user.settings.use24Hour()) + " too? (yes / no)";
        }
        return saved;
    }

    private String listHits(List<MemoryManager.Entry> hits) {
        if (hits.size() == 1) return "You told me: " + hits.get(0).text;
        StringBuilder sb = new StringBuilder("Here's what I have:");
        for (MemoryManager.Entry e : hits) sb.append("\n- ").append(e.text);
        return sb.toString();
    }

    private String memoryQuestion(String text, String clean) {
        if (!clean.matches("^(?:when|what|where|who|which|how|do you know|do you remember|tell me)\\b.*\\b(?:my|i|me)\\b.*")) return null;
        if (clean.matches(".*\\b(my name|who am i|task|tasks|todo|reminder|reminders|password|passkey|mood)\\b.*")) return null;
        List<MemoryManager.Entry> hits = memory.search(text);
        if (hits.isEmpty()) return null;
        return listHits(hits);
    }

    // a sentence with a clock time that nothing else understood
    private String offerReminder(String text, String clean) {
        if (clean.matches("^(?:what|when|where|who|which|how|why|is|are|do|does|did|can|could|will|would)\\b.*") || text.trim().endsWith("?")) return null;
        if (clean.split(" ").length < 3) return null;
        long now = System.currentTimeMillis();
        TimeParser.Result t = TimeParser.parse(text, now);
        if (t.due <= now || !t.hasClock || t.text.length() < 3) return null;
        offerText = TextUtil.capitalize(t.text.replaceAll("(?i)\\s+(is|are|was|will be|starts|ends)$", ""));
        offerDue = t.due;
        pending = Pending.CONFIRM_REMINDER;
        return "Want me to remind you about \"" + offerText + "\" (" + TimeParser.describe(t.due, user.settings.use24Hour()) + ")? (yes / no)";
    }

    private Intent bestIntent(String clean) {
        String[] words = TextUtil.words(clean);
        Intent best = null;
        int bestScore = 0;
        for (Intent in : INTENTS) {
            if (in.maxWords > 0 && words.length > in.maxWords) continue;
            // "i have no time" is not asking for the clock
            if (in.name.equals("time") && clean.matches(".*\\b(no|any|free|on|every|next|first|last|more|much|long|spare|some) time\\b.*")) continue;
            int matched = 0;
            for (String phrase : in.phrases) {
                if (containsPhrase(words, phrase.split(" "))) matched++;
            }
            if (matched == 0) continue;
            int score = in.weight + matched - 1;
            if (score > bestScore) {
                bestScore = score;
                best = in;
            }
        }
        return best;
    }

    private boolean containsPhrase(String[] words, String[] phrase) {
        for (int i = 0; i + phrase.length <= words.length; i++) {
            boolean ok = true;
            for (int j = 0; j < phrase.length; j++) {
                if (!TextUtil.similar(words[i + j], phrase[j])) {
                    ok = false;
                    break;
                }
            }
            if (ok) return true;
        }
        return false;
    }

    private String pick(String... options) {
        return options[rnd.nextInt(options.length)];
    }

    private String answerIntent(String intent, String clean) {
        String name = user.displayName();
        boolean h24 = user.settings.use24Hour();
        switch (intent) {
            case "greeting":
                if (clean.contains("salam") || clean.contains("assalamualaikum")) return "Walaikum assalam, " + name + "! How can I help?";
                if (clean.contains("good morning")) return "Good morning, " + name + "! Ready for a productive day?";
                if (clean.contains("good afternoon")) return "Good afternoon, " + name + "! How is your day going?";
                if (clean.contains("good evening")) return "Good evening, " + name + "! How was your day?";
                return pick("Hey " + name + "! How can I help you today?", "Hello " + name + "! What can I do for you?",
                        "Hi " + name + "! Good to see you.");
            case "howareyou":
                return pick("I'm doing great, " + name + ", thanks for asking! How about you?",
                        "All good on my side! How are you feeling today?");
            case "botname":
                return "My name is " + botName() + ". I'm your personal assistant. You can rename me from Settings or by saying \"change your name to ...\".";
            case "username":
                return "You're " + user.fullName + " (username: " + user.username + "). I call you " + name + ". Tell me \"call me ...\" if you prefer something else.";
            case "aboutme":
                return aboutMe();
            case "creator":
                return "I'm a Java Swing project made for the AI lab course. No servers or cloud, everything runs on your own computer.";
            case "age":
                return "I don't have a birthday, I'm just a program. But I get a little smarter with every update!";
            case "human":
                return "I'm a program, not a person. I understand sentences using rules and pattern matching, and everything stays on your computer.";
            case "doing":
                return pick("Just waiting to help you, " + name + ".", "Keeping your lists and reminders in order.");
            case "time":
            case "date": {
                boolean wantTime = clean.contains("time") || clean.contains("clock") || clean.contains("baje") || clean.contains("somoy");
                boolean wantDate = clean.contains("date") || clean.contains("day") || clean.contains("today") || clean.contains("tarikh");
                if (clean.contains("tomorrow") || clean.contains("yesterday")) {
                    Calendar c = Calendar.getInstance();
                    c.add(Calendar.DAY_OF_YEAR, clean.contains("tomorrow") ? 1 : -1);
                    return (clean.contains("tomorrow") ? "Tomorrow" : "Yesterday") + " is " + new SimpleDateFormat("EEEE, MMMM dd, yyyy").format(c.getTime()) + ".";
                }
                if (wantTime && wantDate) return timeText(h24) + " " + dateText();
                return intent.equals("time") ? timeText(h24) : dateText();
            }
            case "weather":
                return "I run offline, so I can't check the weather. A quick look outside (or your phone) will be more accurate than me!";
            case "joke":
                return Motivator.getJoke();
            case "fact":
                return Motivator.getFact();
            case "motivate":
                return Motivator.getQuote();
            case "coin":
                return "I flipped a coin: " + (rnd.nextBoolean() ? "Heads" : "Tails") + ".";
            case "dice":
                return "I rolled a dice and got " + (rnd.nextInt(6) + 1) + ".";
            case "help":
                return helpText();
            case "forgotpw":
                return "If you saved it with me, say \"show gmail password\" (use the account name you saved). Your vault passkey can be changed in Settings. If you forgot the vault passkey itself, Settings can reset the vault, but that deletes the saved entries.";
            case "thanks":
                return pick("You're welcome, " + name + "!", "Anytime!", "Happy to help!");
            case "sorry":
                return pick("No problem at all, " + name + ".", "That's okay!");
            case "bye":
                return pick("Goodbye, " + name + "! Take care.", "See you later, " + name + "!", "Bye! I'll keep your reminders safe.");
            case "love":
                return pick("That's sweet of you, " + name + "! I'm happy to be your assistant.", "Aww, thank you! You're pretty great yourself.");
            case "insult":
                return "Ouch. I'm still learning, so tell me what I got wrong and I'll try to do better.";
            case "laugh":
                return pick("Glad that made you smile!", "Haha, good one.");
            case "ack":
                return pick("Alright!", "Okay. Anything else?", "Got it.");
            default:
                return "I'm not sure what to say to that.";
        }
    }

    private String aboutMe() {
        StringBuilder sb = new StringBuilder("Here's what I know about you, " + user.displayName() + ":");
        sb.append("\n- You are ").append(user.fullName).append(" (@").append(user.username).append("), member since ").append(user.createdOn);
        sb.append("\n- ").append(tasks.pendingCount()).append(" pending task(s), ").append(tasks.reminders().size()).append(" reminder(s)");
        List<MemoryManager.Entry> mem = memory.memories();
        sb.append("\n- ").append(mem.size()).append(" thing(s) you asked me to remember");
        for (int i = 0; i < Math.min(3, mem.size()); i++) sb.append("\n    ").append(mem.get(i).text);
        sb.append("\n- ").append(vault.count()).append(" account(s) in your vault");
        return sb.toString();
    }

    private String timeText(boolean h24) {
        return "The time is " + new SimpleDateFormat(h24 ? "HH:mm:ss" : "hh:mm:ss a").format(new Date()) + ".";
    }

    private String dateText() {
        return "Today is " + new SimpleDateFormat("EEEE, MMMM dd, yyyy").format(new Date()) + ".";
    }

    private String helpText() {
        return "Here's what I can do. Just talk normally, the exact words don't matter much:\n"
                + "- Tasks: \"add task buy milk\", \"i have to submit the report by 5 pm\", \"show tasks\", \"done 2\", \"undo\"\n"
                + "- Reminders: \"remind me to call mom in 30 minutes\", \"wake me up at 6 am\", \"snooze 10 minutes\"\n"
                + "- Notes: \"remember that my exam is on Friday\", \"when is my exam?\", \"what do you remember?\"\n"
                + "- Vault: \"my facebook password is 1234, user name mutant, phone 01912345678\", \"show gmail password\", \"show my gmail data\"\n"
                + "- Maths: \"what is 12 * (3 + 4)\", \"20% of 150\"\n"
                + "- Fun: \"tell me a joke\", \"fun fact\", \"motivate me\", \"flip a coin\"\n"
                + "- Also: time and date, \"what do i have tomorrow\", \"dark mode\", \"what is NLP\", \"change your name to ...\"\n"
                + "Tell me how you feel too, I'm happy to listen.";
    }

    private String fallback(String text, String clean) {
        String name = user.displayName();
        unknownCount++;
        if (TextUtil.isYes(clean)) return "Great! What would you like to do?";
        if (TextUtil.isNo(clean)) return "Okay, no problem.";
        if (clean.matches("^(?:when|what|where|who|which)\\b.*\\bmy\\b.*")) {
            return "I don't remember anything about that yet. Tell me with \"remember that ...\" and I'll keep it.";
        }
        if (clean.contains("remind")) return "If you want a reminder, try: \"remind me to call mom at 5 pm\".";
        if (clean.contains("task") || clean.contains("todo")) return "For tasks try \"add task ...\", \"show tasks\" or \"done 1\".";
        if (clean.contains("password") || clean.contains("passkey") || clean.contains("username")) {
            return "To save account details just say them, like \"my gmail password is abc123 and username is bob\". To read them: \"show gmail password\".";
        }
        if (clean.contains("remember") || clean.contains("note")) return "To save something say \"remember that ...\". To read it back, \"what do you remember?\".";
        if (clean.matches(".*\\d.*")) return "If you wanted a calculation, write it like \"12 * 4\" or \"what is 15% of 200\".";
        if (unknownCount >= 3) {
            unknownCount = 0;
            return "I still can't make sense of that, " + name + ". If it's something you want to keep, say \"remember that ...\" and I'll save it. Or type \"help\" to see what I understand.";
        }
        if (unknownCount == 2) return "Hmm, that one is new to me. I can handle tasks, reminders, notes, your vault, maths and small talk. Type \"help\" for examples.";
        return pick("I'm not sure I understood that, " + name + ". Could you say it another way?",
                "Sorry, I didn't get that. Try rephrasing, or type \"help\".");
    }

    public String welcome() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String part = hour < 5 ? "Hello" : hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : hour < 22 ? "Good evening" : "Hello";
        StringBuilder sb = new StringBuilder(part + ", " + user.displayName() + "! I'm " + botName() + ".");
        int left = tasks.pendingCount();
        if (left > 0) sb.append(" You have ").append(left).append(left == 1 ? " task" : " tasks").append(" on your list.");
        else sb.append(" How can I help today?");
        return sb.toString();
    }

    public List<String> quickReplies() {
        List<String> list = new ArrayList<>();
        list.add("What's on today?");
        list.add("Show tasks");
        list.add("Tell me a joke");
        list.add("Help");
        return list;
    }
}
