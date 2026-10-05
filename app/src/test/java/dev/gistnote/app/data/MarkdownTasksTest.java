package dev.gistnote.app.data;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MarkdownTasksTest {
    @Test public void parsesNestedCheckboxesWithoutLosingIndentation() {
        List<MarkdownTasks.Line> lines = MarkdownTasks.parse(
                "- [ ] Parent\n  - [x] Child\nParagraph");
        assertTrue(lines.get(0).task);
        assertFalse(lines.get(0).checked);
        assertEquals(0, lines.get(0).indent);
        assertTrue(lines.get(1).task);
        assertTrue(lines.get(1).checked);
        assertEquals(2, lines.get(1).indent);
        assertEquals("Child", lines.get(1).text);
        assertFalse(lines.get(2).task);
    }

    @Test public void togglesOnlyTheSelectedTaskLine() {
        String source = "- [ ] One\n  - [x] Two\n- ordinary";
        assertEquals("- [ ] One\n  - [ ] Two\n- ordinary",
                MarkdownTasks.toggle(source, 1, false));
    }

    @Test public void insertsTaskAtCursorAndReturnsNewCursor() {
        MarkdownTasks.Edit edit = MarkdownTasks.insertTask("hello", 5);
        assertEquals("hello\n- [ ] ", edit.content);
        assertEquals(edit.content.length(), edit.cursor);
    }

    @Test public void indentsAndOutdentsCurrentLine() {
        MarkdownTasks.Edit indented = MarkdownTasks.changeIndent("one\ntwo", 6, true);
        assertEquals("one\n  two", indented.content);
        assertEquals(8, indented.cursor);
        MarkdownTasks.Edit outdented = MarkdownTasks.changeIndent(indented.content, 8, false);
        assertEquals("one\ntwo", outdented.content);
        assertEquals(6, outdented.cursor);
    }

    @Test public void outdentAtStartDoesNotSkipFirstLine() {
        MarkdownTasks.Edit edit = MarkdownTasks.changeIndent("  task", 0, false);
        assertEquals("task", edit.content);
        assertEquals(0, edit.cursor);
    }
}

