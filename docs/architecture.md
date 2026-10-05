# GistNote implementation notes

## GitHub setup

1. In **Settings**, choose **Create GitHub token**.
2. Create a token with the `gist` permission only.
3. Paste it into GistNote and choose **Save and test**.

Gists created with `public: false` are **secret Gists**, not private Gists:
they are unlisted, but anyone with the URL can read them. Do not use GistNote
for passwords or similarly high-risk data.

## Sync model

The app caches notes locally. An explicit save first records the draft on the
device and then attempts to commit it to GitHub. Failed commits remain marked as
unsynced until the user explicitly tries again.

Before replacing a remote Gist revision, GistNote checks for a changed version.
If the remote and local edits differ, it asks the user whether to use GitHub,
keep editing, or explicitly replace GitHub.

## Background behaviour

GistNote does not schedule periodic work. It has no automatic background sync,
job scheduler entry, alarm, polling loop, or persistent background service.
GitHub is contacted only after a user action such as opening or refreshing a
note, tapping a widget's sync control, creating a Gist, viewing history, testing
a token, or committing an edit.

## Credential storage

The GitHub token is encrypted with AES-GCM. The encryption key is generated and
held by Android Keystore; Android backups are disabled for the encrypted token
data.

## Distribution note

The first GitHub release contains a debug APK for personal testing. A broadly
distributed release should use a dedicated signing key and a release-signed APK
or Android App Bundle.
