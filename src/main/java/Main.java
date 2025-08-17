import java.io.IOException;
import java.util.Scanner;

public class Main {
  public static void main(String[] args){
    if (args.length != 2 || !args[0].equals("-E")) {
      System.out.println("Usage: ./your_program.sh -E <pattern>");
      System.exit(1);
    }

    String pattern = args[1];  
    Scanner scanner = new Scanner(System.in);
    String inputLine = scanner.nextLine();
    System.err.println("Logs from your program will appear here!");

    if (matchPattern(inputLine, pattern)) {
        System.exit(0);
    } else {
        System.exit(1);
    }
  }

  public static boolean matchPattern(String inputLine, String pattern) {
    for (int start = 0; start < inputLine.length(); start++) {
      if (matchesFrom(inputLine, start, pattern)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesFrom(String input, int startIndex, String pattern) {
    int i = startIndex;
    int p = 0;

    while (p < pattern.length()) {
      if (i >= input.length()) {
        return false;
      }

      char pc = pattern.charAt(p);

      if (pc == '\\') {
        if (p + 1 >= pattern.length()) {
          throw new RuntimeException("Unhandled pattern: dangling escape at end of pattern");
        }
        char cls = pattern.charAt(p + 1);
        char ch = input.charAt(i);
        if (cls == 'd') {
          if (!Character.isDigit(ch)) {
            return false;
          }
          p += 2;
          i += 1;
          continue;
        } else if (cls == 'w') {
          if (!(Character.isLetterOrDigit(ch) || ch == '_')) {
            return false;
          }
          p += 2;
          i += 1;
          continue;
        } else {
          throw new RuntimeException("Unhandled escape: \\" + cls);
        }
      }

      if (pc == '[') {
        int end = pattern.indexOf(']', p + 1);
        if (end == -1) {
          throw new RuntimeException("Unhandled pattern: missing closing ]");
        }
        boolean negative = (p + 1 < end) && pattern.charAt(p + 1) == '^';
        int contentStart = negative ? p + 2 : p + 1;
        String group = pattern.substring(contentStart, end);
        char ch = input.charAt(i);
        boolean contains = group.indexOf(ch) != -1;
        if ((negative && contains) || (!negative && !contains)) {
          return false;
        }
        p = end + 1;
        i += 1;
        continue;
      }

      // Literal character
      if (input.charAt(i) != pc) {
        return false;
      }
      i += 1;
      p += 1;
    }

    return true;
  }
}
