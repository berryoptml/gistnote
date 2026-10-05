package dev.gistnote.app.data;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoteStoreTest {
    @Test public void localIdsAreSeparatedFromGithubIds() {
        assertTrue(NoteStore.isLocalId("local-1234"));
        assertFalse(NoteStore.isLocalId("abcdef1234"));
        assertFalse(NoteStore.isLocalId(null));
    }
}
