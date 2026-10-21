import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// chat side of the vault: save, show, list, delete.
// if the vault is locked it asks for the passkey first and then does what the user wanted
public class VaultChat {
    private final Vault vault;

    private enum Pending { NONE, UNLOCK, NEED_SERVICE_SAVE, NEED_SERVICE_SHOW, CONFIRM_DELETE }

    private Pending pending = Pending.NONE;
    private Supplier<String> afterUnlock;
    private int tries = 0;
    private VaultParser.Parsed partial;
    private Set<String> partialWanted;
    private Vault.Entry deleteEntry;
    private String deleteField;

    // true when the reply shows saved details, so the chat history skips it
    public boolean secretReply = false;

    public VaultChat(Vault vault) {
        this.vault = vault;
    }

    private static final Pattern QUERY_START = Pattern.compile(
            "^(?:what|whats|what's|which|show|tell|give|get|reveal|display|find|retrieve|see|read|open|check|print|"
                    + "do i have|did i save|where is|can i see|could i see|let me see|i forgot|forgot|i dont remember|"
                    + "i do not remember|i cannot remember|i lost)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUERY_END = Pattern.compile(
            ".*\\b(?:show me|tell me|give me|let me see|show it|show|please show|reveal)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_WORDS = Pattern.compile(
            ".*\\b(?:data|details|detail|info|information|everything|all|credentials|login|logins)\\b.*");
    private static final Pattern DELETE_START = Pattern.compile("^(?:delete|remove|erase|forget|clear|wipe)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_WORDS = Pattern.compile(
            ".*\\b(?:password|passwords|passkey|passkeys|pin|passcode|username|login|credentials|vault|account details)\\b.*");

    // what goes to the chat history, without passkeys or saved details
    public String logSafe(String text) {
        if (pending == Pending.UNLOCK) return "********";
        Matcher m = Pattern.compile("^(?:please\\s+)?(?:unlock|open)\\s+(?:my\\s+|the\\s+)?(?:vault|passwords?)(?:\\s+(?:with|using))?\\s+\\S+$",
                Pattern.CASE_INSENSITIVE).matcher(text.trim());
        if (m.matches()) return "unlock vault ********";
        String clean = TextUtil.clean(text);
        if (looksLikeSave(text, clean)) {
            VaultParser.Parsed p = VaultParser.parseSave(text);
            if (p != null) {
                return "(saved account details" + (p.service != null ? " for " + p.service : "") + ": "
                        + describe(p.fields.keySet()) + ")";
            }
        }
        return text;
    }

    public boolean isWaiting() {
        return pending != Pending.NONE;
    }

    public String pendingAnswer(String text, String clean) {
        switch (pending) {
            case UNLOCK: {
                if (TextUtil.isNo(clean)) {
                    pending = Pending.NONE;
                    return "Okay, I left the vault locked.";
                }
                String attempt = text.trim().replaceFirst("(?i)^(?:unlock(?:\\s+(?:the\\s+|my\\s+)?vault)?\\s+|(?:my\\s+)?(?:passkey|password|pin)(?:\\s+is)?\\s+)", "")
                        .replaceAll("^[\"']|[\"']$", "");
                if (vault.unlock(attempt)) {
                    pending = Pending.NONE;
                    tries = 0;
                    Supplier<String> next = afterUnlock;
                    afterUnlock = null;
                    String result = next == null ? "" : next.get();
                    secretReply = true;
                    return "Vault unlocked." + (result.isEmpty() ? "" : "\n" + result);
                }
                tries++;
                if (tries >= 3) {
                    pending = Pending.NONE;
                    tries = 0;
                    return "That's three wrong tries, so I stopped. The vault stays locked.";
                }
                return "That passkey isn't right. Try again, or say cancel.";
            }
            case NEED_SERVICE_SAVE: {
                pending = Pending.NONE;
                if (TextUtil.isNo(clean)) return "Okay, I didn't save anything.";
                String service = cleanService(text);
                if (service.isEmpty()) return "I didn't catch the name. Tell me again, for example \"gmail\".";
                partial.service = service;
                return finishSave(partial);
            }
            case NEED_SERVICE_SHOW: {
                pending = Pending.NONE;
                if (TextUtil.isNo(clean)) return "Okay.";
                Vault.Entry e = vault.findIn(text);
                if (e == null) return notFound(text);
                return show(e, partialWanted);
            }
            case CONFIRM_DELETE: {
                pending = Pending.NONE;
                if (TextUtil.isYes(clean)) {
                    boolean ok = deleteField == null ? vault.delete(deleteEntry.service) : vault.deleteField(deleteEntry.service, deleteField);
                    return ok ? "Deleted." : "I couldn't delete that.";
                }
                return TextUtil.isNo(clean) ? "Okay, I kept it." : null;
            }
            default:
                return null;
        }
    }

    public String handle(String text, String clean) {
        secretReply = false;
        Matcher m;

        m = Pattern.compile("^(?:please\\s+)?(?:unlock|open)\\s+(?:my\\s+|the\\s+)?(?:vault|passwords?|password manager)(?:\\s+(?:with|using))?\\s*(\\S*)$",
                Pattern.CASE_INSENSITIVE).matcher(text.trim());
        if (m.matches()) {
            if (!vault.isSetUp()) return notSetUp();
            if (vault.isUnlocked()) return "The vault is already unlocked.";
            if (!m.group(1).isEmpty() && !m.group(1).equalsIgnoreCase("please")) {
                if (vault.unlock(m.group(1))) return "Vault unlocked.";
                return "That passkey isn't right.";
            }
            pending = Pending.UNLOCK;
            tries = 0;
            afterUnlock = null;
            return "Type your vault passkey to unlock it.";
        }
        if (clean.matches("(?:please )?(lock|close) (my |the )?(vault|passwords?|password manager)")) {
            vault.lock();
            return vault.isSetUp() ? "The vault is locked." : notSetUp();
        }

        boolean list = isList(clean);
        boolean query = !list && isQuery(text, clean);
        boolean delete = !list && !query && DELETE_START.matcher(clean).matches()
                && (SECRET_WORDS.matcher(clean).matches() || clean.matches(".*\\baccount\\b.*")
                || vault.isUnlocked() && vault.findIn(text) != null);
        boolean save = !list && !query && !delete && looksLikeSave(text, clean);
        if (!list && !query && !delete && !save) return null;

        if (!vault.isSetUp()) return notSetUp();

        if (save) {
            final VaultParser.Parsed p = VaultParser.parseSave(text);
            if (p == null) return null;
            return whenUnlocked(() -> startSave(p));
        }
        if (list) return whenUnlocked(this::listAll);
        final String original = text;
        if (delete) return whenUnlocked(() -> startDelete(original));
        return whenUnlocked(() -> answerQuery(original));
    }

    // locked? ask for the passkey and run the action after it opens
    private String whenUnlocked(Supplier<String> action) {
        if (vault.isUnlocked()) {
            String r = action.get();
            secretReply = true;
            return r;
        }
        pending = Pending.UNLOCK;
        tries = 0;
        afterUnlock = action;
        return "Your vault is locked. Type your vault passkey to continue (or say cancel).";
    }

    private String notSetUp() {
        return "You haven't created a vault passkey yet. Open Vault in the sidebar and set one, then I can keep your passwords safe.";
    }

    private boolean isList(String clean) {
        return clean.matches("(list|show|see|display|open|check|view)( me)?( my| all| the)?( all)?( saved| stored)? "
                + "(passwords|accounts|logins|vault|passkeys|credentials|pins)( i have| i saved)?")
                || clean.matches("(what|which) (accounts|passwords|passkeys|logins)( do i have| have i saved| are saved| did i save)?.*")
                || clean.matches("(my )?(vault|saved passwords)");
    }

    private boolean isQuery(String text, String clean) {
        if (looksLikeAssignment(clean)) return false;
        Set<String> labels = VaultParser.labelsIn(text);
        boolean credential = labels.contains("password") || labels.contains("passkey") || labels.contains("username")
                || clean.matches(".*\\b(?:credentials|passwords|logins)\\b.*");
        boolean data = clean.matches(".*\\b(?:data|details|detail|login|logins)\\b.*");
        boolean asking = QUERY_START.matcher(clean).matches() || QUERY_END.matcher(clean).matches() || text.trim().endsWith("?");
        if (asking && credential) return true;
        if (asking && !labels.isEmpty() && vault.isUnlocked() && vault.findIn(text) != null) return true;
        if (asking && data) {
            if (vault.isUnlocked()) return vault.findIn(text) != null;
            return clean.split(" ").length <= 6;
        }
        return clean.matches("(my )?[a-z0-9 ]{1,25} (password|passkey|pin|username|login|credentials|passcode)");
    }

    private boolean looksLikeAssignment(String clean) {
        return clean.matches(".*\\b(?:password|pass|pwd|pin|passkey|passcode|username|user name|email|phone|number)\\b.* (?:is|are|was) \\S+.*")
                && !clean.matches("^(?:what|whats|which|tell|show|give|where).*");
    }

    // needs a secret in the sentence, or a save word with 2+ fields
    private boolean looksLikeSave(String text, String clean) {
        if (clean.matches("^(?:what|whats|which|how|why|when|where|who|do|does|did|can|could|is|are|show|tell|give|get|reveal|find|list|delete|remove|erase|forget|remind|add task|todo).*")
                && !clean.matches("^(?:do not|dont) forget.*")) return false;
        if (QUERY_END.matcher(clean).matches()) return false;
        VaultParser.Parsed p = VaultParser.parseSave(text);
        if (p == null || p.fields.isEmpty()) return false;
        boolean hasVerb = clean.matches("^(?:save|store|add|remember|note|keep|write down|put|record|log|update|change|set|reset)\\b.*");
        return p.hasSecret() || (hasVerb && p.fields.size() >= 2);
    }

    private String startSave(VaultParser.Parsed p) {
        if (p.service == null) {
            partial = p;
            pending = Pending.NEED_SERVICE_SAVE;
            return "Got the details (" + describe(p.fields.keySet()) + "). Which account or website are they for?";
        }
        return finishSave(p);
    }

    private String finishSave(VaultParser.Parsed p) {
        boolean updated = vault.save(p.service, p.kind(), p.fields);
        secretReply = true;
        String name = new Vault.Entry(p.service, p.kind()).title();
        return (updated ? "Updated " : "Saved ") + name + ": " + describe(p.fields.keySet())
                + ". It's stored encrypted in your vault.";
    }

    private String cleanService(String text) {
        String s = text.trim().replaceAll("(?i)^(?:it(?:'s| is)|its|for|the|my|this is|that is|they are|account is|service is)\\s+", "")
                .replaceAll("(?i)\\s+(?:account|login)$", "").replaceAll("[.!?\"']", "").trim();
        s = s.replaceAll("(?i)^(?:for|the|my)\\s+", "");
        if (s.split("\\s+").length > 4) return "";
        return s.toLowerCase();
    }

    private String listAll() {
        List<Vault.Entry> all = vault.entries();
        if (all.isEmpty()) return "Your vault is empty. Tell me something like \"my gmail password is abc123\" and I'll save it.";
        StringBuilder sb = new StringBuilder("Saved in your vault:");
        for (Vault.Entry e : all) sb.append("\n- ").append(e.title()).append(": ").append(describe(e.fields.keySet()));
        return sb.toString();
    }

    private String answerQuery(String text) {
        List<Vault.Entry> all = vault.entries();
        if (all.isEmpty()) return "Your vault is empty. Tell me something like \"my gmail password is abc123\" and I'll save it.";
        Set<String> wanted = VaultParser.labelsIn(text);
        boolean everything = wanted.isEmpty() || DATA_WORDS.matcher(TextUtil.clean(text)).matches()
                && !wanted.contains("password") && !wanted.contains("passkey");
        if (everything) wanted = new java.util.LinkedHashSet<>();
        Vault.Entry e = vault.findIn(text);
        if (e == null && all.size() == 1) e = all.get(0);
        if (e == null) {
            partialWanted = wanted;
            pending = Pending.NEED_SERVICE_SHOW;
            return "Which account? You have: " + names(all) + ".";
        }
        return show(e, wanted);
    }

    private String show(Vault.Entry e, Set<String> wanted) {
        secretReply = true;
        if (wanted == null || wanted.isEmpty()) {
            StringBuilder sb = new StringBuilder(e.title() + ":");
            for (Map.Entry<String, String> f : e.fields.entrySet()) {
                sb.append("\n- ").append(VaultParser.display(f.getKey())).append(": ").append(f.getValue());
            }
            return sb.toString();
        }
        List<String[]> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String key : wanted) {
            String v = e.fields.get(key);
            if (v == null && key.equals("password")) v = e.fields.get("passkey");
            if (v == null && key.equals("passkey")) v = e.fields.get("password");
            if (v == null) missing.add(VaultParser.display(key).toLowerCase());
            else found.add(new String[]{VaultParser.display(key), v});
        }
        if (found.size() == 1 && missing.isEmpty()) {
            return "Your " + e.title() + " " + found.get(0)[0].toLowerCase() + " is: " + found.get(0)[1];
        }
        StringBuilder sb = new StringBuilder(e.title() + ":");
        for (String[] f : found) sb.append("\n- ").append(f[0]).append(": ").append(f[1]);
        if (!missing.isEmpty()) {
            sb.append("\nI don't have a ").append(String.join(" or ", missing)).append(" saved for ").append(e.title())
                    .append(". What I have: ").append(describe(e.fields.keySet())).append(".");
        }
        return sb.toString();
    }

    private String notFound(String text) {
        List<Vault.Entry> all = vault.entries();
        return "I couldn't find that account. You have: " + (all.isEmpty() ? "nothing saved yet" : names(all)) + ".";
    }

    private String startDelete(String text) {
        Vault.Entry e = vault.findIn(text);
        if (e == null) return notFound(text);
        Set<String> labels = VaultParser.labelsIn(text);
        deleteEntry = e;
        deleteField = null;
        // "delete netflix phone" removes just the phone, anything else removes the whole account
        if (labels.size() == 1) {
            String only = labels.iterator().next();
            if (!only.equals("password") && !only.equals("passkey") && e.fields.containsKey(only)) deleteField = only;
        }
        pending = Pending.CONFIRM_DELETE;
        if (deleteField != null) return "Remove the " + VaultParser.display(deleteField).toLowerCase() + " from " + e.title() + "? (yes / no)";
        return "Delete everything saved for " + e.title() + " (" + describe(e.fields.keySet()) + ")? (yes / no)";
    }

    private String names(List<Vault.Entry> all) {
        List<String> n = new ArrayList<>();
        for (Vault.Entry e : all) n.add(e.title());
        return String.join(", ", n);
    }

    public static String describe(java.util.Collection<String> keys) {
        List<String> names = new ArrayList<>();
        for (String k : keys) names.add(VaultParser.display(k).toLowerCase());
        if (names.isEmpty()) return "nothing";
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}
