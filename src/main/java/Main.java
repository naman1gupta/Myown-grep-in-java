import java.io.IOException;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface for pattern matching components
 */
interface PatternMatcher {
    MatchResult match(String input, int position);
}

/**
 * Result of a pattern match attempt
 */
class MatchResult {
    public final boolean matched;
    public final int endPosition;
    
    public MatchResult(boolean matched, int endPosition) {
        this.matched = matched;
        this.endPosition = endPosition;
    }
    
    public static MatchResult success(int endPosition) {
        return new MatchResult(true, endPosition);
    }
    
    public static MatchResult failure() {
        return new MatchResult(false, -1);
    }
}

/**
 * Basic pattern implementations
 */
class EmptyPattern implements PatternMatcher {
    @Override
    public MatchResult match(String input, int position) {
        return MatchResult.success(position);
    }
}

class LiteralCharacterPattern implements PatternMatcher {
    private final char character;
    
    public LiteralCharacterPattern(char character) {
        this.character = character;
    }
    
    @Override
    public MatchResult match(String input, int position) {
        if (position >= input.length() || input.charAt(position) != character) {
            return MatchResult.failure();
        }
        return MatchResult.success(position + 1);
    }
}

class AnyCharacterPattern implements PatternMatcher {
    @Override
    public MatchResult match(String input, int position) {
        if (position >= input.length()) {
            return MatchResult.failure();
        }
        return MatchResult.success(position + 1);
    }
}

class DigitClassPattern implements PatternMatcher {
    @Override
    public MatchResult match(String input, int position) {
        if (position >= input.length() || !Character.isDigit(input.charAt(position))) {
            return MatchResult.failure();
        }
        return MatchResult.success(position + 1);
    }
}

class WordClassPattern implements PatternMatcher {
    @Override
    public MatchResult match(String input, int position) {
        if (position >= input.length()) {
            return MatchResult.failure();
        }
        char c = input.charAt(position);
        if (Character.isLetterOrDigit(c) || c == '_') {
            return MatchResult.success(position + 1);
        }
        return MatchResult.failure();
    }
}

class CharacterGroupPattern implements PatternMatcher {
    private final String group;
    private final boolean negated;
    
    public CharacterGroupPattern(String group) {
        if (group.startsWith("^")) {
            this.negated = true;
            this.group = group.substring(1);
        } else {
            this.negated = false;
            this.group = group;
        }
    }
    
    @Override
    public MatchResult match(String input, int position) {
        if (position >= input.length()) {
            return MatchResult.failure();
        }
        
        char c = input.charAt(position);
        boolean contains = group.indexOf(c) != -1;
        
        if (negated ? !contains : contains) {
            return MatchResult.success(position + 1);
        }
        return MatchResult.failure();
    }
}

/**
 * Sequence and composite patterns
 */
class SequencePattern implements PatternMatcher {
    private final List<PatternMatcher> patterns;
    
    public SequencePattern(List<PatternMatcher> patterns) {
        this.patterns = patterns;
    }
    
    @Override
    public MatchResult match(String input, int position) {
        int currentPos = position;
        
        for (PatternMatcher pattern : patterns) {
            MatchResult result = pattern.match(input, currentPos);
            if (!result.matched) {
                return MatchResult.failure();
            }
            currentPos = result.endPosition;
        }
        
        return MatchResult.success(currentPos);
    }
}

class AlternationPattern implements PatternMatcher {
    private final List<PatternMatcher> alternatives;
    
    public AlternationPattern(List<PatternMatcher> alternatives) {
        this.alternatives = alternatives;
    }
    
    @Override
    public MatchResult match(String input, int position) {
        for (PatternMatcher alternative : alternatives) {
            MatchResult result = alternative.match(input, position);
            if (result.matched) {
                return result;
            }
        }
        return MatchResult.failure();
    }
}

/**
 * Quantifier patterns
 */
class ZeroOrOnePattern implements PatternMatcher {
    private final PatternMatcher pattern;
    
    public ZeroOrOnePattern(PatternMatcher pattern) {
        this.pattern = pattern;
    }
    
    @Override
    public MatchResult match(String input, int position) {
        MatchResult result = pattern.match(input, position);
        if (result.matched) {
            return result;
        }
        // If pattern doesn't match, that's also valid for ?
        return MatchResult.success(position);
    }
}

class OneOrMorePattern implements PatternMatcher {
    private final PatternMatcher pattern;
    
    public OneOrMorePattern(PatternMatcher pattern) {
        this.pattern = pattern;
    }
    
    @Override
    public MatchResult match(String input, int position) {
        // Must match at least once
        MatchResult first = pattern.match(input, position);
        if (!first.matched) {
            return MatchResult.failure();
        }
        
        int currentPos = first.endPosition;
        
        // Try to match as many more times as possible (greedy)
        while (currentPos <= input.length()) {
            MatchResult next = pattern.match(input, currentPos);
            if (!next.matched) {
                break;
            }
            currentPos = next.endPosition;
        }
        
        return MatchResult.success(currentPos);
    }
}

/**
 * Anchor patterns
 */
class StartAnchorPattern implements PatternMatcher {
    private final PatternMatcher pattern;
    
    public StartAnchorPattern(PatternMatcher pattern) {
        this.pattern = pattern;
    }
    
    @Override
    public MatchResult match(String input, int position) {
        if (position != 0) {
            return MatchResult.failure();
        }
        return pattern.match(input, position);
    }
}

class EndAnchorPattern implements PatternMatcher {
    private final PatternMatcher pattern;
    
    public EndAnchorPattern(PatternMatcher pattern) {
        this.pattern = pattern;
    }
    
    @Override
    public MatchResult match(String input, int position) {
        MatchResult result = pattern.match(input, position);
        if (!result.matched) {
            return MatchResult.failure();
        }
        
        // Check if we've reached the end of input
        if (result.endPosition == input.length()) {
            return result;
        }
        
        return MatchResult.failure();
    }
}

/**
 * Factory for creating pattern matchers from regex strings.
 */
class PatternFactory {
    
    public PatternMatcher createPattern(String regex) {
        if (regex == null || regex.isEmpty()) {
            return new EmptyPattern();
        }
        
        // Handle anchors
        if (regex.startsWith("^") && regex.endsWith("$")) {
            String innerRegex = regex.substring(1, regex.length() - 1);
            PatternMatcher innerPattern = parsePattern(innerRegex);
            return new StartAnchorPattern(new EndAnchorPattern(innerPattern));
        } else if (regex.startsWith("^")) {
            String innerRegex = regex.substring(1);
            PatternMatcher innerPattern = parsePattern(innerRegex);
            return new StartAnchorPattern(innerPattern);
        } else if (regex.endsWith("$")) {
            String innerRegex = regex.substring(0, regex.length() - 1);
            PatternMatcher innerPattern = parsePattern(innerRegex);
            return new EndAnchorPattern(innerPattern);
        }
        
        return parsePattern(regex);
    }
    
    private PatternMatcher parsePattern(String regex) {
        if (regex.isEmpty()) {
            return new EmptyPattern();
        }
        
        List<PatternMatcher> sequence = new ArrayList<>();
        int i = 0;
        
        while (i < regex.length()) {
            ElementParseResult result = parseElementWithQuantifier(regex, i);
            
            sequence.add(result.pattern);
            i = result.nextPosition;
        }
        
        if (sequence.size() == 1) {
            return sequence.get(0);
        }
        
        return new SequencePattern(sequence);
    }
    
    private ElementParseResult parseElementWithQuantifier(String regex, int position) {
        ElementParseResult baseResult = parseElementBase(regex, position);
        int nextPos = baseResult.nextPosition;
        
        // Check for quantifiers
        if (nextPos < regex.length()) {
            char quantifier = regex.charAt(nextPos);
            if (quantifier == '+') {
                return new ElementParseResult(
                    new OneOrMorePattern(baseResult.pattern),
                    nextPos + 1
                );
            } else if (quantifier == '?') {
                return new ElementParseResult(
                    new ZeroOrOnePattern(baseResult.pattern),
                    nextPos + 1
                );
            }
        }
        
        return baseResult;
    }
    
    private ElementParseResult parseElementBase(String regex, int position) {
        char c = regex.charAt(position);
        
        switch (c) {
            case '\\':
                return parseEscapeSequence(regex, position);
            case '.':
                return new ElementParseResult(new AnyCharacterPattern(), position + 1);
            case '[':
                return parseCharacterGroup(regex, position);
            case '(':
                return parseGroup(regex, position);
            default:
                return new ElementParseResult(new LiteralCharacterPattern(c), position + 1);
        }
    }
    
    private ElementParseResult parseEscapeSequence(String regex, int position) {
        if (position + 1 >= regex.length()) {
            return new ElementParseResult(new LiteralCharacterPattern('\\'), position + 1);
        }
        
        char escaped = regex.charAt(position + 1);
        switch (escaped) {
            case 'd':
                return new ElementParseResult(new DigitClassPattern(), position + 2);
            case 'w':
                return new ElementParseResult(new WordClassPattern(), position + 2);
            default:
                return new ElementParseResult(new LiteralCharacterPattern(escaped), position + 2);
        }
    }
    
    private ElementParseResult parseCharacterGroup(String regex, int position) {
        int endPos = regex.indexOf(']', position + 1);
        if (endPos == -1) {
            return new ElementParseResult(new LiteralCharacterPattern('['), position + 1);
        }
        
        String group = regex.substring(position + 1, endPos);
        return new ElementParseResult(new CharacterGroupPattern(group), endPos + 1);
    }
    
    private ElementParseResult parseGroup(String regex, int position) {
        int endPos = findMatchingParen(regex, position);
        if (endPos == -1) {
            return new ElementParseResult(new LiteralCharacterPattern('('), position + 1);
        }
        
        String groupContent = regex.substring(position + 1, endPos);
        
        if (groupContent.contains("|")) {
            return parseAlternation(groupContent, endPos + 1);
        } else {
            PatternMatcher groupPattern = parsePattern(groupContent);
            return new ElementParseResult(groupPattern, endPos + 1);
        }
    }
    
    private ElementParseResult parseAlternation(String content, int nextPosition) {
        List<String> alternatives = splitOnTopLevelPipe(content);
        List<PatternMatcher> patterns = new ArrayList<>();
        
        for (String alt : alternatives) {
            patterns.add(parsePattern(alt));
        }
        
        return new ElementParseResult(new AlternationPattern(patterns), nextPosition);
    }
    
    private List<String> splitOnTopLevelPipe(String content) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int depth = 0;
        boolean escaped = false;
        boolean inBracket = false;
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
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
                } else if (c == '|' && depth == 0) {
                    // Found top-level pipe
                    parts.add(content.substring(start, i));
                    start = i + 1;
                }
            }
        }
        
        // Add the last part
        parts.add(content.substring(start));
        return parts;
    }
    
    private int findMatchingParen(String regex, int openPos) {
        int depth = 0;
        boolean escaped = false;
        boolean inBracket = false;
        
        for (int i = openPos; i < regex.length(); i++) {
            char c = regex.charAt(i);
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
        return -1;
    }
    
    private static class ElementParseResult {
        final PatternMatcher pattern;
        final int nextPosition;
        
        ElementParseResult(PatternMatcher pattern, int nextPosition) {
            this.pattern = pattern;
            this.nextPosition = nextPosition;
        }
    }
}

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
    PatternFactory factory = new PatternFactory();
    PatternMatcher matcher = factory.createPattern(pattern);
    
    // For non-anchored patterns, try matching at each position
    if (!pattern.startsWith("^")) {
      for (int i = 0; i <= inputLine.length(); i++) {
        MatchResult result = matcher.match(inputLine, i);
        if (result.matched) {
          return true;
        }
      }
      return false;
    } else {
      // For anchored patterns, only try at position 0
      MatchResult result = matcher.match(inputLine, 0);
      return result.matched;
    }
  }
}