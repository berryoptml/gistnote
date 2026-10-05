# GistNote Desktop widget

This is the first native macOS companion slice. Run it with:

```sh
swift run
```

The app configures its window as a floating desktop widget: keep it small,
place it where wanted, and use the stack control to expand/collapse the notes.
The local `NoteStore` is deliberately isolated so the existing GitHub Gist sync
engine can be connected without changing the widget interaction.
