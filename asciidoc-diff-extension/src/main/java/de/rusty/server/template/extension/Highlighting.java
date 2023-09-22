package de.rusty.server.template.extension;

import java.util.regex.Pattern;
import java.util.stream.Stream;

public enum Highlighting {

    DESCRIPTION_LIST(Pattern.compile("^(.+)::$"), "[.highlight]#$1#::"),
    TITLE(Pattern.compile("^\\.([^.]+)"), ".[.highlight]#$1#"),
    TABLE_CELL(Pattern.compile("\\|\\s*([^|]+)"), ".[.highlight]#$1#"),
    ADMONITION(Pattern.compile("^(NOTE|IMPORTANT|TIP|CAUTION|WARNING):\\s?(.+)"), "$1: [.highlight]#$2#"),
    LIST_ITEM(Pattern.compile("\\*\\s*([^*]+)"), "* [.highlight]#$1#"),
    DEFAULT(Pattern.compile("^([^=\\.]+)"), "[.highlight]#$1#");

    private final Pattern pattern;
    private final String replacement;

    Highlighting(Pattern pattern, String replacement) {

        this.pattern = pattern;
        this.replacement = replacement;
    }

    public static Highlighting findByPattern(Pattern pattern) {
        return Stream.of(Highlighting.values())
                     .filter(p -> p.pattern.equals(pattern))
                     .findAny()
                     .orElseThrow(IllegalArgumentException::new);
    }

    public String getReplacement() {

        return replacement;
    }

    public Pattern getPattern() {

        return pattern;
    }
}
