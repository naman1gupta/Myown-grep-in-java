import java.io.IOException;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;

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
    boolean anchoredAtStart = false;
    if (pattern.startsWith("^")) {
      anchoredAtStart = true;
      pattern = pattern.substring(1);
    }

    // Support simple grouping and alternation when the whole pattern is wrapped in parentheses
    pattern = stripOuterParentheses(pattern);
    List<String> alternatives = splitByTopLevelOr(pattern);

    if (alternatives.size() > 1) {
      for (String alt : alternatives) {
        String altPattern = stripOuterParentheses(alt);
        if (anchoredAtStart) {
          if (matchesFrom(inputLine, 0, altPattern)) {
            return true;
          }
        } else {
          for (int start = 0; start < inputLine.length(); start++) {
            if (matchesFrom(inputLine, start, altPattern)) {
              return true;
            }
          }
        }
      }
      return false;
    }

    if (anchoredAtStart) {
      return matchesFrom(inputLine, 0, pattern);
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

      // Handle parentheses with potential alternation (but not when followed by quantifiers)
      int atomLen = determineAtomLength(pattern, p);
      if (pc == '(' && (p + atomLen >= pattern.length() || (pattern.charAt(p + atomLen) != '+' && pattern.charAt(p + atomLen) != '?'))) {
        int closeParen = findMatchingCloseParen(pattern, p);
        if (closeParen == -1) {
          throw new RuntimeException("Unhandled pattern: missing closing )");
        }
        
        String subPattern = pattern.substring(p + 1, closeParen);
        List<String> alternatives = splitByTopLevelOr(subPattern);
        
        if (alternatives.size() > 1) {
          // Handle alternation within parentheses
          for (String alt : alternatives) {
            String altPattern = stripOuterParentheses(alt);
            if (matchesFrom(input, i, altPattern + pattern.substring(closeParen + 1))) {
              return true;
            }
          }
          return false;
        } else {
          // Simple parentheses without alternation
          if (!matchesFrom(input, i, subPattern)) {
            return false;
          }
          // For now, just advance by the length of the subpattern
          // This is a simplified approach
          i += subPattern.length();
          p = closeParen + 1;
          continue;
        }
      }

      // Determine the current atom and whether it has '+' or '?' quantifier
      boolean hasPlus = (p + atomLen < pattern.length()) && pattern.charAt(p + atomLen) == '+';
      boolean hasQuestion = (p + atomLen < pattern.length()) && pattern.charAt(p + atomLen) == '?';

      if (hasPlus) {
        // Must match the atom at least once
        if (pc == '(') {
          // Handle + quantifier on parentheses group
          String subPattern = pattern.substring(p + 1, p + atomLen - 1);
          int nextP = p + atomLen + 1; // skip atom and '+'
          
          // Use backtracking approach: try different numbers of repetitions
          for (int maxReps = 10; maxReps >= 1; maxReps--) {
            int currentPos = i;
            boolean allMatched = true;
            
            // Try to match exactly maxReps repetitions
            for (int rep = 0; rep < maxReps && allMatched; rep++) {
              if (currentPos >= input.length()) {
                allMatched = false;
                break;
              }
              
              // Try to find a match for the subpattern at currentPos
              boolean foundMatch = false;
              for (int testEnd = currentPos + 1; testEnd <= input.length() && !foundMatch; testEnd++) {
                String testSubstring = input.substring(currentPos, testEnd);
                if (matchesFrom(testSubstring, 0, subPattern)) {
                  currentPos = testEnd;
                  foundMatch = true;
                }
              }
              
              if (!foundMatch) {
                allMatched = false;
              }
            }
            
            // If we successfully matched all repetitions, try to match the rest
            if (allMatched && matchesFrom(input, currentPos, pattern.substring(nextP))) {
              return true;
            }
          }
          
          return false;
        } else {
          // Handle + quantifier on simple atom (not parentheses)
          if (pc == '(') {
            // This should not happen since parentheses are handled above
            return false;
          }
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
        }
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
    if (pc == '(') {
      int closeParen = findMatchingCloseParen(pattern, p);
      if (closeParen == -1) {
        throw new RuntimeException("Unhandled pattern: missing closing )");
      }
      return closeParen - p + 1;
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
    if (pc == '(') {
      // For parentheses, we need to match the entire subpattern
      // This should not be called for parentheses groups
      return false;
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

  private static String stripOuterParentheses(String pattern) {
    while (pattern.length() >= 2 && pattern.charAt(0) == '(' && pattern.charAt(pattern.length() - 1) == ')') {
      int depth = 0;
      boolean valid = true;
      boolean escaped = false;
      boolean inBracket = false;
      for (int i = 0; i < pattern.length(); i++) {
        char c = pattern.charAt(i);
        if (escaped) {
          escaped = false;
          continue;
        }
        if (c == '\\') {
          escaped = true;
          continue;
        }
        if (c == '[') {
          if (!inBracket) inBracket = true;
        } else if (c == ']' && inBracket) {
          inBracket = false;
        } else if (!inBracket) {
          if (c == '(') depth++;
          else if (c == ')') depth--;
          if (depth == 0 && i != pattern.length() - 1) {
            valid = false;
            break;
          }
        }
      }
      if (valid && depth == 0) {
        pattern = pattern.substring(1, pattern.length() - 1);
      } else {
        break;
      }
    }
    return pattern;
  }

  private static List<String> splitByTopLevelOr(String pattern) {
    List<String> parts = new ArrayList<>();
    int last = 0;
    int depth = 0;
    boolean escaped = false;
    boolean inBracket = false;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '[') {
        if (!inBracket) inBracket = true;
      } else if (c == ']' && inBracket) {
        inBracket = false;
      } else if (!inBracket) {
        if (c == '(') depth++;
        else if (c == ')') depth--;
        else if (c == '|' && depth == 0) {
          parts.add(pattern.substring(last, i));
          last = i + 1;
        }
      }
    }
    if (parts.isEmpty()) {
      return List.of(pattern);
    }
    parts.add(pattern.substring(last));
    return parts;
  }

  private static boolean matchesExactly(String input, String pattern) {
    // Check if the input matches the pattern exactly by simulating the match process
    return matchesFromExactly(input, 0, pattern) == input.length();
  }
  
  private static int matchesFromExactly(String input, int startIndex, String pattern) {
    // Similar to matchesFrom but returns the number of characters consumed, or -1 if no match
    int i = startIndex;
    int p = 0;

    while (p < pattern.length()) {
      if (i >= input.length()) {
        // No more input: succeed only if remaining pattern can match empty
        if (canMatchEmptyFromPatternIndex(pattern, p)) {
          return i - startIndex;
        }
        return -1;
      }

      char pc = pattern.charAt(p);

      if (pc == '$') {
        if (p != pattern.length() - 1) {
          throw new RuntimeException("Unhandled pattern: '$' must be at end of pattern");
        }
        return i == input.length() ? i - startIndex : -1;
      }

      // Handle parentheses with potential alternation (but not when followed by quantifiers)
      int atomLen = determineAtomLength(pattern, p);
      if (pc == '(' && (p + atomLen >= pattern.length() || (pattern.charAt(p + atomLen) != '+' && pattern.charAt(p + atomLen) != '?'))) {
        int closeParen = findMatchingCloseParen(pattern, p);
        if (closeParen == -1) {
          throw new RuntimeException("Unhandled pattern: missing closing )");
        }
        
        String subPattern = pattern.substring(p + 1, closeParen);
        List<String> alternatives = splitByTopLevelOr(subPattern);
        
        if (alternatives.size() > 1) {
          // Handle alternation within parentheses
          for (String alt : alternatives) {
            String altPattern = stripOuterParentheses(alt);
            int consumed = matchesFromExactly(input, i, altPattern);
            if (consumed >= 0) {
              i += consumed;
              p = closeParen + 1;
              break;
            }
          }
        } else {
          // Simple parentheses without alternation
          int consumed = matchesFromExactly(input, i, subPattern);
          if (consumed < 0) {
            return -1;
          }
          i += consumed;
          p = closeParen + 1;
        }
        continue;
      }

      // Determine the current atom and whether it has '+' or '?' quantifier
      boolean hasPlus = (p + atomLen < pattern.length()) && pattern.charAt(p + atomLen) == '+';
      boolean hasQuestion = (p + atomLen < pattern.length()) && pattern.charAt(p + atomLen) == '?';

      if (hasQuestion) {
        // Optional - try to match, but don't fail if it doesn't
        if (i < input.length() && matchesAtom(input.charAt(i), pattern, p, atomLen)) {
          i++;
        }
        p += atomLen + 1;
      } else if (hasPlus) {
        // This shouldn't happen in the exact match context since + is handled separately
        return -1;
      } else {
        // Single occurrence
        if (i >= input.length() || !matchesAtom(input.charAt(i), pattern, p, atomLen)) {
          return -1;
        }
        i++;
        p += atomLen;
      }
    }

    return i - startIndex;
  }

  private static int findMatchLength(String input, int startIndex, String pattern) {
    // Use a more sophisticated approach to find the exact length consumed by the pattern
    return findConsumedLength(input, startIndex, pattern);
  }
  
  private static int findConsumedLength(String input, int startIndex, String pattern) {
    // Simulate the matching process to find exactly how many characters are consumed
    int consumed = 0;
    int p = 0;
    int i = startIndex;
    
    while (p < pattern.length() && i < input.length()) {
      char pc = pattern.charAt(p);
      
      if (pc == '$') {
        // End anchor - no more characters should be consumed
        return consumed;
      }
      
      if (pc == '(') {
        // Handle parentheses groups
        int closeParen = findMatchingCloseParen(pattern, p);
        if (closeParen == -1) {
          return -1; // Invalid pattern
        }
        
        String subPattern = pattern.substring(p + 1, closeParen);
        List<String> alternatives = splitByTopLevelOr(subPattern);
        
        boolean matched = false;
        for (String alt : alternatives) {
          int altConsumed = findConsumedLength(input, i, alt);
          if (altConsumed > 0) {
            consumed += altConsumed;
            i += altConsumed;
            matched = true;
            break;
          }
        }
        
        if (!matched) {
          return -1; // No alternative matched
        }
        
        p = closeParen + 1;
        continue;
      }
      
      int atomLen = determineAtomLength(pattern, p);
      boolean hasPlus = (p + atomLen < pattern.length()) && pattern.charAt(p + atomLen) == '+';
      boolean hasQuestion = (p + atomLen < pattern.length()) && pattern.charAt(p + atomLen) == '?';
      
      if (hasQuestion) {
        // Optional - try to match, but don't fail if it doesn't
        if (i < input.length() && matchesAtom(input.charAt(i), pattern, p, atomLen)) {
          consumed++;
          i++;
        }
        p += atomLen + 1;
      } else if (hasPlus) {
        // This shouldn't happen in our case since we're already in + quantifier handling
        return -1;
      } else {
        // Required single match
        if (i >= input.length() || !matchesAtom(input.charAt(i), pattern, p, atomLen)) {
          return -1; // Can't match
        }
        consumed++;
        i++;
        p += atomLen;
      }
    }
    
    // Check if we consumed the entire pattern
    if (p >= pattern.length()) {
      return consumed;
    }
    
    return -1; // Didn't match complete pattern
  }

  private static int findMatchingCloseParen(String pattern, int openParenIndex) {
    int depth = 0;
    boolean escaped = false;
    boolean inBracket = false;
    
    for (int i = openParenIndex; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '[') {
        if (!inBracket) inBracket = true;
      } else if (c == ']' && inBracket) {
        inBracket = false;
      } else if (!inBracket) {
        if (c == '(') {
          depth++;
        } else if (c == ')') {
          depth--;
          if (depth == 0) {
            return i;
          }
        }
      }
    }
    return -1; // No matching close parenthesis found
  }


}
