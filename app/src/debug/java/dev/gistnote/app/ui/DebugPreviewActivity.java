package dev.gistnote.app.ui;

import android.app.Activity;
import android.os.Bundle;

import dev.gistnote.app.data.LineDiff;

/** Debug-only host for visual previews of dialogs that normally require network data. */
public final class DebugPreviewActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra("mode");
        if ("picker".equals(mode)) {
            DetailedColorPickerDialog.show(this, "Note background", 0xFFFFAD29,
                    color -> { });
        } else {
            RevisionDiffDialog.show(this, "debug-version", "Compared with previous revision",
                    LineDiff.between("# Research scratchpad\n\nOld line\nKeep this",
                            "# Research scratchpad\n\nNew line\nKeep this\nAdded line"),
                    () -> { });
        }
    }
}
