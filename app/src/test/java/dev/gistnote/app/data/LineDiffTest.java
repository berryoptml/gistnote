package dev.gistnote.app.data;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class LineDiffTest {
    @Test public void emitsClassicalDeleteThenAddForReplacement() {
        List<LineDiff.Row> rows = LineDiff.between("one\nold\nthree", "one\nnew\nthree");
        assertEquals(LineDiff.Kind.CONTEXT, rows.get(0).kind);
        assertEquals(LineDiff.Kind.DELETED, rows.get(1).kind);
        assertEquals("old", rows.get(1).text);
        assertEquals(LineDiff.Kind.ADDED, rows.get(2).kind);
        assertEquals("new", rows.get(2).text);
        assertEquals(LineDiff.Kind.CONTEXT, rows.get(3).kind);
    }

    @Test public void tracksOldAndNewLineNumbers() {
        List<LineDiff.Row> rows = LineDiff.between("a\nb", "x\na\nb\nc");
        assertEquals(0, rows.get(0).oldLine);
        assertEquals(1, rows.get(0).newLine);
        assertEquals(1, rows.get(1).oldLine);
        assertEquals(2, rows.get(1).newLine);
        assertEquals(0, rows.get(3).oldLine);
        assertEquals(4, rows.get(3).newLine);
    }

    @Test public void emptyInitialRevisionIsAllAdditions() {
        List<LineDiff.Row> rows = LineDiff.between("", "first\nsecond");
        assertEquals(2, rows.size());
        assertEquals(LineDiff.Kind.ADDED, rows.get(0).kind);
        assertEquals(LineDiff.Kind.ADDED, rows.get(1).kind);
    }

    @Test public void identicalTextIsContextOnly() {
        List<LineDiff.Row> rows = LineDiff.between("same", "same");
        assertEquals(1, rows.size());
        assertEquals(LineDiff.Kind.CONTEXT, rows.get(0).kind);
    }
}

