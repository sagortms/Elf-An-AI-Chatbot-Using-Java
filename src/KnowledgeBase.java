import java.util.regex.Matcher;
import java.util.regex.Pattern;

// short answers about the topics of this project, nice for a demo
public class KnowledgeBase {

    private static final String[][] TOPICS = {
            {"artificial intelligence|ai", "Artificial intelligence (AI) is the field of making computers do things that normally need human intelligence, like understanding language, learning from data and making decisions. Elf is a small rule-based example of it."},
            {"machine learning|ml", "Machine learning is a part of AI where a program learns patterns from examples instead of following hand-written rules. Elf itself uses rules, not trained models."},
            {"natural language processing|nlp", "NLP is how computers work with human language. Elf does a small version of it: it cleans your sentence, fixes typos, finds the intent (what you want) and pulls out details like times, names and task text."},
            {"chatbot|chat bot", "A chatbot is a program you talk to in everyday language. Some use trained models, some like Elf use rules and pattern matching."},
            {"intent", "An intent is what the user wants to do, for example \"set a reminder\" or \"ask the time\". Elf scores every message against its intents and picks the best one."},
            {"entity|entities", "An entity is a useful piece of information inside a sentence, like \"tomorrow at 9 am\" or \"buy milk\". Elf extracts these with patterns and a time parser."},
            {"regex|regular expression|regular expressions", "A regular expression is a pattern for matching text. Elf uses them to pick out the task, note or reminder from what you type."},
            {"turing test", "The Turing test, proposed by Alan Turing in 1950, asks whether a machine can chat well enough that a person cannot tell it from another human."},
            {"alan turing", "Alan Turing was a British mathematician and one of the founders of computer science and AI. He proposed the Turing test in 1950."},
            {"java", "Java is the programming language Elf is written in. It is object oriented and runs on any system with a Java runtime."},
            {"swing|java swing", "Swing is Java's built-in toolkit for desktop windows. Elf's login screen, sidebar and chat bubbles are all Swing components."},
            {"algorithm", "An algorithm is a step by step method for solving a problem. Elf uses simple ones: edit distance for typo matching and recursive descent for the calculator."},
            {"edit distance|levenshtein", "Edit distance counts how many single-letter changes turn one word into another. Elf uses it to understand typos like \"tiem\" for \"time\"."},
            {"encryption|aes", "Encryption scrambles data so only someone with the key can read it. Elf's vault encrypts every entry with AES-GCM using a key made from your vault passkey."},
            {"hash|hashing|pbkdf2", "A hash is a one-way fingerprint of a password. Elf stores only a salted PBKDF2 hash of your login password, so the real password is never saved."},
            {"bangladesh", "Bangladesh is a country in South Asia. Its capital is Dhaka."},
            {"dhaka", "Dhaka is the capital and largest city of Bangladesh."},
    };

    private static final Pattern ASK = Pattern.compile(
            "^(?:what is|what are|what s|whats|define|explain|tell me about|tell me what is|who is|what do you mean by|meaning of|describe)\\s+(?:an |a |the )?(.+)$");

    public static String answer(String clean) {
        Matcher m = ASK.matcher(clean);
        if (!m.find()) return null;
        String topic = m.group(1).trim();
        for (String[] t : TOPICS) {
            for (String name : t[0].split("\\|")) {
                if (topic.equals(name)) return t[1];
            }
        }
        return null;
    }

    public static String aboutElf(String clean) {
        if (clean.matches(".*\\b(how do you work|how do you understand|how does elf work|how do you think|how were you made|how are you built)\\b.*")) {
            return "I read your message in steps: first I clean it and fix small typos, then I look for commands with details (a task, a time, a note), then maths, then your mood, and finally I score my intents to find what you mean. Anything I can't place gets a hint instead of a blank answer.";
        }
        if (clean.matches(".*\\b(where is my data|where are my data|where do you (store|save|keep)|where is the data|where is my data stored|where is my data saved)\\b.*")) {
            return "Everything is stored as text files in the data folder next to the program, in a sub-folder with your username (data/<username>/). Vault entries are encrypted, the rest is plain text.";
        }
        if (clean.matches(".*\\b(what technology|which language|what language|built with|made with|written in)\\b.*")) {
            return "I'm written in plain Java with Swing for the windows, and everything is stored in simple text files on your computer.";
        }
        return null;
    }
}
