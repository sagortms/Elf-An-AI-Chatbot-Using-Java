import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// tiny helper for reading and writing the txt files
public class FileStore {

    public static List<String> read(File file) {
        List<String> lines = new ArrayList<>();
        if (!file.exists()) return lines;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) lines.add(line);
            }
        } catch (IOException e) {
            System.out.println("Could not read " + file.getName() + ": " + e.getMessage());
        }
        return lines;
    }

    public static boolean write(File file, List<String> lines) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (String line : lines) out.println(line);
            return true;
        } catch (IOException e) {
            System.out.println("Could not write " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean append(File file, String line) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            out.println(line);
            return true;
        } catch (IOException e) {
            System.out.println("Could not write " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }
}
