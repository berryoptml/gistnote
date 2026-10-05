import dev.gistnote.app.data.MarkdownTasks;

public final class MarkdownTasksSelfTest {
    public static void main(String[] args) {
        String nested = "- [ ] Parent\n  - [x] Child\nParagraph";
        check(MarkdownTasks.parse(nested).size() == 3, "parse line count");
        check(MarkdownTasks.parse(nested).get(1).checked, "checked child");
        check(MarkdownTasks.parse(nested).get(1).indent == 2, "nested indent");
        check(MarkdownTasks.toggle(nested, 0, true).startsWith("- [x] Parent"), "toggle");
        MarkdownTasks.Edit inserted = MarkdownTasks.insertTask("hello", 5);
        check(inserted.content.equals("hello\n- [ ] "), "insert task");
        MarkdownTasks.Edit indented = MarkdownTasks.changeIndent("one\ntwo", 6, true);
        check(indented.content.equals("one\n  two"), "indent");
        MarkdownTasks.Edit outdented = MarkdownTasks.changeIndent(indented.content, 8, false);
        check(outdented.content.equals("one\ntwo"), "outdent");
        System.out.println("MarkdownTasksSelfTest: all checks passed");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}

