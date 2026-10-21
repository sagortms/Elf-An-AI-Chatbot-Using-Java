import java.util.regex.Matcher;
import java.util.regex.Pattern;

// small calculator for + - * / % ^ brackets and sqrt (recursive descent)
public class Calculator {

    private String src;
    private int pos;

    public static String tryAnswer(String message) {
        String s = message.toLowerCase().trim();
        boolean asked = s.matches("^(what is|what's|whats|calculate|calc|solve|compute|how much is|find|evaluate).*");
        s = s.replaceAll("^(what is|what's|whats|calculate|calc|solve|compute|how much is|find|evaluate|tell me)\\s+", "");
        s = s.replaceAll("[=?]+$", "").trim();

        Matcher pm = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)\\s*of\\s*(\\d+(?:\\.\\d+)?)$").matcher(s);
        if (pm.find()) {
            double v = Double.parseDouble(pm.group(1)) / 100 * Double.parseDouble(pm.group(2));
            return pm.group(1) + "% of " + pm.group(2) + " = " + format(v);
        }

        s = s.replace("square root of", "sqrt").replace("root of", "sqrt");
        s = s.replaceAll("(\\d)\\s*x\\s*(\\d)", "$1*$2");
        s = s.replace("multiplied by", "*").replace("times", "*").replace("into", "*").replace("×", "*");
        s = s.replace("divided by", "/").replace("over", "/").replace("÷", "/");
        s = s.replace("plus", "+").replace("minus", "-").replace("mod", "%");
        s = s.replace("to the power of", "^").replace("power", "^").replace("squared", "^2").replace("cubed", "^3");
        s = s.replaceAll("sqrt\\s*(\\d+(?:\\.\\d+)?)", "sqrt($1)");

        String compact = s.replaceAll("\\s+", "");
        if (!compact.matches("[0-9.+\\-*/^%()sqrt]+")) return null;
        if (!compact.matches(".*\\d.*")) return null;
        boolean hasOp = compact.matches(".*[+*/^%].*") || compact.contains("sqrt")
                || s.matches(".*\\d\\s+-\\s+\\d.*") || (asked && compact.matches(".*\\d-\\d.*"));
        if (!hasOp) return null;

        try {
            double result = new Calculator().eval(compact);
            if (Double.isNaN(result) || Double.isInfinite(result)) return "That doesn't give a real number.";
            return compact.replace("*", " * ").replace("/", " / ").replace("+", " + ") + " = " + format(result);
        } catch (ArithmeticException e) {
            return "I can't divide by zero.";
        } catch (RuntimeException e) {
            return asked ? "I couldn't work that expression out. Check the brackets and operators." : null;
        }
    }

    static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        String s = String.format("%.6f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private double eval(String expr) {
        src = expr;
        pos = 0;
        double v = parseExpression();
        if (pos != src.length()) throw new IllegalArgumentException("unexpected " + src.charAt(pos));
        return v;
    }

    // expression = term { (+|-) term }
    private double parseExpression() {
        double v = parseTerm();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '+') {
                pos++;
                v += parseTerm();
            } else if (c == '-') {
                pos++;
                v -= parseTerm();
            } else {
                break;
            }
        }
        return v;
    }

    // term = power { (*|/|%) power }
    private double parseTerm() {
        double v = parsePower();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '*') {
                pos++;
                v *= parsePower();
            } else if (c == '/') {
                pos++;
                double d = parsePower();
                if (d == 0) throw new ArithmeticException("divide by zero");
                v /= d;
            } else if (c == '%') {
                pos++;
                double d = parsePower();
                if (d == 0) throw new ArithmeticException("divide by zero");
                v %= d;
            } else {
                break;
            }
        }
        return v;
    }

    // power = unary [ ^ power ]
    private double parsePower() {
        double base = parseUnary();
        if (pos < src.length() && src.charAt(pos) == '^') {
            pos++;
            return Math.pow(base, parsePower());
        }
        return base;
    }

    private double parseUnary() {
        if (pos < src.length() && src.charAt(pos) == '-') {
            pos++;
            return -parseUnary();
        }
        if (pos < src.length() && src.charAt(pos) == '+') {
            pos++;
            return parseUnary();
        }
        return parsePrimary();
    }

    private double parsePrimary() {
        if (src.startsWith("sqrt", pos)) {
            pos += 4;
            double v = parsePrimary();
            if (v < 0) throw new IllegalArgumentException("negative root");
            return Math.sqrt(v);
        }
        if (pos < src.length() && src.charAt(pos) == '(') {
            pos++;
            double v = parseExpression();
            if (pos >= src.length() || src.charAt(pos) != ')') throw new IllegalArgumentException("missing )");
            pos++;
            return v;
        }
        int start = pos;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++;
        if (start == pos) throw new IllegalArgumentException("number expected");
        return Double.parseDouble(src.substring(start, pos));
    }
}
