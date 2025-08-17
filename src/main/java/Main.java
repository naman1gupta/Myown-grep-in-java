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
    if (pattern.startsWith("^")) {
      String anchored = pattern.substring(1);
      return matchesFrom(inputLine, 0, anchored);
    }

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
        // No more input: succeed only if remaining pattern can match empty
        return canMatchEmptyFromPatternIndex(pattern, p);
      }

      char pc = pattern.charAt(p);

      if (pc == '$') {
        if (p != pattern.length() - 1) {
          throw new RuntimeException("Unhandled pattern: '$' must be at end of pattern");
        }
        return i == input.length();
      }

      // Determine the current atom and whether it has '+' or '?' quantifier
      int atomLen = determineAtomLength(pattern, p);
      boolean hasPlus = (p + atomLen < pattern.length()) && pattern.charAt(p + atomLen) == '+';
      boolean hasQuestion = (p + atomLen < pattern.length()) && pattern.charAt(p + atomLen) == '?';

      if (hasPlus) {
        // Must match the atom at least once
        if (!matchesAtom(input.charAt(i), pattern, p, atomLen)) {
          return false;
        }
        int j = i + 1;
        while (j < input.length() && matchesAtom(input.charAt(j), pattern, p, atomLen)) {
          j++;
        }
        int nextP = p + atomLen + 1; // skip atom and '+'
        // Backtrack: try the longest match first, then shorten until one works
        for (int k = j; k >= i + 1; k--) {
          if (matchesFrom(input, k, pattern.substring(nextP))) {
            return true;
          }
        }
        return false;
      } else if (hasQuestion) {
        int nextP = p + atomLen + 1; // skip atom and '?'
        // Try consuming one if possible (greedy)
        if (matchesAtom(input.charAt(i), pattern, p, atomLen)) {
          if (matchesFrom(input, i + 1, pattern.substring(nextP))) {
            return true;
          }
        }
        // Or consume zero
        return matchesFrom(input, i, pattern.substring(nextP));
      } else {
        // Single occurrence
        if (!matchesAtom(input.charAt(i), pattern, p, atomLen)) {
          return false;
        }
        i += 1;
        p += atomLen;
      }
    }

    return true;
  }

  private static int determineAtomLength(String pattern, int p) {
    char pc = pattern.charAt(p);
    if (pc == '\\') {
      if (p + 1 >= pattern.length()) {
        throw new RuntimeException("Unhandled pattern: dangling escape at end of pattern");
      }
      char cls = pattern.charAt(p + 1);
      if (cls == 'd' || cls == 'w') {
        return 2;
      }
      throw new RuntimeException("Unhandled escape: \\" + cls);
    }
    if (pc == '[') {
      int end = pattern.indexOf(']', p + 1);
      if (end == -1) {
        throw new RuntimeException("Unhandled pattern: missing closing ]");
      }
      if (p + 1 == end) {
        throw new RuntimeException("Unhandled pattern: empty character group []");
      }
      return end - p + 1;
    }
    // Literal (including '.', space and others). '$' is handled before.
    return 1;
  }

  private static boolean matchesAtom(char ch, String pattern, int p, int atomLen) {
    char pc = pattern.charAt(p);
    if (pc == '.') {
      return true;
    }
    if (pc == '\\') {
      char cls = pattern.charAt(p + 1);
      if (cls == 'd') {
        return Character.isDigit(ch);
      }
      if (cls == 'w') {
        return Character.isLetterOrDigit(ch) || ch == '_';
      }
      throw new RuntimeException("Unhandled escape: \\" + cls);
    }
    if (pc == '[') {
      int end = p + atomLen - 1;
      boolean negative = (p + 1 < end) && pattern.charAt(p + 1) == '^';
      int contentStart = negative ? p + 2 : p + 1;
      String group = pattern.substring(contentStart, end);
      boolean contains = group.indexOf(ch) != -1;
      return negative ? !contains : contains;
    }
    // Literal
    return ch == pc;
  }

  private static boolean canMatchEmptyFromPatternIndex(String pattern, int p) {
    while (p < pattern.length()) {
      char pc = pattern.charAt(p);
      if (pc == '$' && p == pattern.length() - 1) {
        return true;
      }
      int atomLen = determineAtomLength(pattern, p);
      if ((p + atomLen < pattern.length()) && pattern.charAt(p + atomLen) == '?') {
        p += atomLen + 1;
        continue;
      }
      return false;
    }
    return true;
  }
}
