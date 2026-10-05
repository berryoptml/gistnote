package dev.gistnote.app.data;

public final class GistRevision {
    public final String version;
    public final String committedAt;
    public final int additions;
    public final int deletions;

    public GistRevision(String version, String committedAt, int additions, int deletions) {
        this.version = version;
        this.committedAt = committedAt;
        this.additions = additions;
        this.deletions = deletions;
    }

    public String shortVersion() {
        return version.length() <= 8 ? version : version.substring(0, 8);
    }
}

