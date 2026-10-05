package dev.gistnote.app.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownTasks {
    private static final Pattern TASK = Pattern.compile("^(\\s*)[-*+]\\s+\\[([ xX])\\]\\s?(.*)$");

    private MarkdownTasks() { }

    public static List<Line> parse(String content) {
        String[] source = content.split("\\n", -1);
        List<Line> result = new ArrayList<>(source.length);
        for (int i = 0; i < source.length; i++) {
            Matcher matcher = TASK.matcher(source[i]);
            if (matcher.matches()) {
                result.add(new Line(i, true, matcher.group(2).equalsIgnoreCase("x"),
                        matcher.group(1).length(), matcher.group(3), source[i]));
            } else {
                result.add(new Line(i, false, false, leadingSpaces(source[i]), source[i], source[i]));
            }
        }
        return result;
    }

    public static String toggle(String content, int lineNumber, boolean checked) {
        String[] lines = content.split("\\n", -1);
        if (lineNumber < 0 || lineNumber >= lines.length) return content;
        Matcher matcher = TASK.matcher(lines[lineNumber]);
        if (!matcher.matches()) return content;
        lines[lineNumber] = matcher.group(1) + "- [" + (checked ? "x" : " ") + "] " + matcher.group(3);
        return join(lines);
    }

    public static Edit insertTask(String content, int cursor) {
        int safe = Math.max(0, Math.min(cursor, content.length()));
        String prefix = (safe > 0 && content.charAt(safe - 1) != '\n') ? "\n" : "";
        String insertion = prefix + "- [ ] ";
        return new Edit(content.substring(0, safe) + insertion + content.substring(safe),
                safe + insertion.length());
    }

    public static Edit changeIndent(String content, int cursor, boolean indent) {
        int safe = Math.max(0, Math.min(cursor, content.length()));
        int start = content.lastIndexOf('\n', safe - 1) + 1;
        if (indent) {
            return new Edit(content.substring(0, start) + "  " + content.substring(start), safe + 2);
        }
        int remove = 0;
        while (remove < 2 && start + remove < content.length() && content.charAt(start + remove) == ' ') {
            remove++;
        }
        return new Edit(content.substring(0, start) + content.substring(start + remove),
                Math.max(start, safe - remove));
    }

    private static int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') count++;
        return count;
    }

    private static String join(String[] lines) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) result.append('\n');
            result.append(lines[i]);
        }
        return result.toString();
    }

    public static final class Line {
        public final int lineNumber;
        public final boolean task;
        public final boolean checked;
        public final int indent;
        public final String text;
        public final String source;

        Line(int lineNumber, boolean task, boolean checked, int indent, String text, String source) {
            this.lineNumber = lineNumber;
            this.task = task;
            this.checked = checked;
            this.indent = indent;
            this.text = text;
            this.source = source;
        }
    }

    public static final class Edit {
        public final String content;
        public final int cursor;

        Edit(String content, int cursor) {
            this.content = content;
            this.cursor = cursor;
        }
    }
}
