# GistNote

GistNote is a native Android sticky-note widget backed by GitHub Gists. A widget
opens a reading overlay without raising the keyboard; tapping the note body a
second time enters the editor. Each successful save becomes a GitHub gist
revision, and the in-app History screen can inspect or restore earlier versions.

## Current vertical slice

- Connect a GitHub fine-grained personal access token from Settings.
- List all gists for the authenticated account and create unlisted notes.
- Bind multiple resizable home-screen widgets to different gists.
- Pin a selected gist directly from the app on launchers that support Android's
  widget-pinning API; the normal widget picker has a selection/configuration UI.
- Customize each widget's paper color and text size.
- Edit plain Markdown and add, indent, outdent, and toggle nested `- [ ]` tasks.
- Read from the local cache offline; offline edits remain marked as unsynced.
- Refresh widgets periodically with Android `JobScheduler`.
- Detect a changed remote revision before saving and require an explicit conflict
  decision instead of silently overwriting it.
- Inspect up to 100 recent gist revisions and restore an old revision as a new one.

## GitHub setup

1. In Settings, tap **Create gist-only GitHub token**. This opens GitHub's
   prefilled classic-token form with only the `gist` scope selected.
2. Create and copy the token. It has Gist access but no repository scope.
3. In GistNote, open Settings, paste the token, then choose **Save and test**.

The token is encrypted with AES-GCM. Its encryption key is generated and held by
Android Keystore; backups are disabled so encrypted credentials are not restored
onto a device that lacks the corresponding key.

### Privacy warning

GitHub calls a gist created with `public: false` a **secret gist**, not a private
gist. It is unlisted, but anyone who obtains its URL can read it. Do not put
passwords, financial data, or other sensitive information in GistNote. A private
repository storage adapter is the appropriate next backend if access control is
required.

## Build

The project uses Android Gradle Plugin 9.1.1 and Gradle 9.3.1.

Requirements:

- JDK 17 or newer (Android Studio's bundled JBR is supported)
- Android SDK Platform 37 and Build Tools 36.0.0
- An Android 6.0 (API 23) or newer device

Open the folder in Android Studio and run the `app` configuration, or use:

```sh
./gradlew test assembleDebug
```

The debug APK is emitted at `app/build/outputs/apk/debug/app-debug.apk`.

## Sync and conflict behavior

The cached note makes the widget useful without a network connection. Each edit
also carries the GitHub revision from which it began. Before updating a gist,
GistNote fetches the latest revision:

- unchanged remote: update it and cache the returned revision;
- same content already online: mark the local copy clean;
- changed remote with different content: retain the local draft and ask whether
  to use GitHub, continue editing, or explicitly replace GitHub.

Background sync never chooses a winner for a conflict. It leaves the draft dirty
until the foreground UI can ask the user.

## Deliberate MVP boundaries

- Authentication is bring-your-own-token. A production release should use a
  GitHub OAuth device/web flow so users do not manually handle tokens.
- A gist with several files is represented by its first Markdown file (or its
  first file when none is Markdown). Widget configuration remembers that file.
- Formatting is portable Markdown rather than a rich-text document model.
- Widgets show task state but task toggling happens in the reading overlay,
  where conflict handling and sync status can be shown clearly.
