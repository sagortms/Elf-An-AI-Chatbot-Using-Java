import java.util.Random;

public class Motivator {
    private static final Random rnd = new Random();

    private static final String[] quotes = {
            "Keep going, you're doing great!",
            "Believe in yourself!",
            "You are capable of amazing things.",
            "Don't give up. The beginning is always the hardest.",
            "You can do it!",
            "Every step counts.",
            "Success starts with courage.",
            "Keep pushing forward.",
            "You are stronger than you think!",
            "Small progress is still progress.",
            "Discipline beats motivation. Just start with five minutes.",
            "A bad day doesn't make a bad life.",
            "You don't have to be perfect, just keep showing up."
    };

    private static final String[] jokes = {
            "Why do programmers prefer dark mode? Because light attracts bugs.",
            "I told my computer I needed a break. Now it won't stop sending me Kit-Kat ads.",
            "Why was the computer cold? It left its Windows open.",
            "There are 10 kinds of people: those who understand binary and those who don't.",
            "Why did the student eat his homework? The teacher said it was a piece of cake.",
            "A SQL query walks into a bar, walks up to two tables and asks: can I join you?",
            "Why do Java developers wear glasses? Because they don't C#.",
            "I would tell you a UDP joke, but you might not get it.",
            "What do you call a sleeping bull? A bulldozer.",
            "Why did the scarecrow win a prize? He was outstanding in his field."
    };

    private static final String[] facts = {
            "Honey never spoils. Edible honey has been found in ancient Egyptian tombs.",
            "The first computer bug was a real moth found inside a Harvard computer in 1947.",
            "Octopuses have three hearts and blue blood.",
            "Bangladesh has the longest natural sea beach in the world: Cox's Bazar, about 120 km.",
            "A day on Venus is longer than a year on Venus.",
            "The word \"robot\" comes from a Czech word meaning forced labour.",
            "Bananas are slightly radioactive because they contain potassium-40.",
            "The first programmer in history was Ada Lovelace, in the 1840s.",
            "Your brain uses about 20 percent of your body's energy."
    };

    public static String getQuote() {
        return quotes[rnd.nextInt(quotes.length)];
    }

    public static String getJoke() {
        return jokes[rnd.nextInt(jokes.length)];
    }

    public static String getFact() {
        return facts[rnd.nextInt(facts.length)];
    }
}
