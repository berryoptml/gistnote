package dev.gistnote.app.data;

public final class SyncEngine {
    private final GitHubApi api;
    private final NoteStore store;

    public SyncEngine(GitHubApi api, NoteStore store) {
        this.api = api;
        this.store = store;
    }

    public GistNote refresh(String gistId, String filename) throws Exception {
        GistNote remote = api.getGist(gistId, filename);
        NoteStore.CachedNote local = store.getNote(gistId);
        if (local == null || !local.dirty) store.putRemote(remote);
        return remote;
    }

    public GistNote save(NoteStore.CachedNote base, String newContent) throws Exception {
        store.putDraft(base, newContent);
        GistNote remote = api.getGist(base.gistId, base.filename);
        boolean changedElsewhere = (!base.version.isEmpty()
                && !remote.version.isEmpty()
                && !base.version.equals(remote.version))
                || (base.version.isEmpty() && !base.content.equals(remote.content));
        if (changedElsewhere && !remote.content.equals(newContent)) {
            store.setConflict(base.gistId, true);
            throw new ConflictException(remote, newContent);
        }
        if (remote.content.equals(newContent)) {
            store.putRemote(remote);
            return remote;
        }
        GistNote saved = api.updateGist(base.gistId, base.filename, newContent);
        store.putRemote(saved);
        return saved;
    }

    public static final class ConflictException extends Exception {
        public final GistNote remote;
        public final String localContent;

        public ConflictException(GistNote remote, String localContent) {
            super("This gist changed on GitHub after the note was opened.");
            this.remote = remote;
            this.localContent = localContent;
        }
    }
}
