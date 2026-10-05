package dev.gistnote.app.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.gistnote.app.R;
import dev.gistnote.app.data.GitHubApi;
import dev.gistnote.app.security.TokenStore;

public final class SettingsActivity extends Activity {
    private static final Uri CREATE_TOKEN_URI = Uri.parse(
            "https://github.com/settings/tokens/new"
                    + "?scopes=gist"
                    + "&description=GistNote+Android+gist+sync");
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        SystemBars.apply(findViewById(R.id.settings_root));
        addFavoriteSettings();
        TokenStore tokenStore = new TokenStore(this);
        EditText input = findViewById(R.id.token_input);
        CheckBox show = findViewById(R.id.show_token);
        TextView status = findViewById(R.id.token_status);
        Button save = findViewById(R.id.save_token_button);
        Button clear = findViewById(R.id.clear_token_button);

        input.setText(tokenStore.getToken());
        input.setSelection(input.length());
        status.setText(tokenStore.hasToken() ? "A token is stored on this device." : "Not connected.");
        show.setOnCheckedChangeListener((button, checked) -> {
            input.setTransformationMethod(checked ? null : PasswordTransformationMethod.getInstance());
            input.setSelection(input.length());
        });
        findViewById(R.id.create_token_button).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, CREATE_TOKEN_URI));
            } catch (ActivityNotFoundException error) {
                android.widget.Toast.makeText(this, R.string.github_token_link_failed,
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });

        save.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                status.setText("Paste a GitHub token first.");
                return;
            }
            save.setEnabled(false);
            status.setText("Encrypting token and testing GitHub…");
            executor.execute(() -> {
                try {
                    tokenStore.saveToken(value);
                    String login = new GitHubApi(value).getLogin();
                    tokenStore.setUsername(login);
                    runOnUiThread(() -> {
                        status.setText("Connected as @" + login);
                        save.setEnabled(true);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        status.setText(error.getMessage());
                        save.setEnabled(true);
                    });
                }
            });
        });

        clear.setOnClickListener(v -> {
            tokenStore.clear();
            input.setText("");
            status.setText("Disconnected. Cached widget text remains on this device.");
        });
    }

    private void addFavoriteSettings() {
        android.widget.LinearLayout parent = findViewById(R.id.settings_content);
        for (boolean background : new boolean[] {false, true}) {
            TextView title = new TextView(this);
            title.setText(background ? "Widget background favorites" : "Widget text favorites");
            title.setTextColor(getColor(R.color.ink));
            title.setTextSize(16);
            title.setPadding(0, 32, 0, 12);
            parent.addView(title);
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            parent.addView(row);
            renderFavorites(row, background);
        }
    }

    private void renderFavorites(android.widget.LinearLayout row, boolean background) {
        row.removeAllViews();
        int[] colors = dev.gistnote.app.widget.WidgetStyleFavorites.load(this, background);
        int size = Math.round(48 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < colors.length; i++) {
            final int slot = i;
            android.widget.FrameLayout target = new android.widget.FrameLayout(this);
            View circle = new View(this);
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            drawable.setColor(colors[i]);
            drawable.setStroke(2, getColor(R.color.surface_stroke));
            circle.setBackground(drawable);
            target.addView(circle, new android.widget.FrameLayout.LayoutParams(size / 2, size / 2, android.view.Gravity.CENTER));
            target.setContentDescription((background ? "Background" : "Text") + " favorite " + (i + 1));
            target.setOnClickListener(v -> DetailedColorPickerDialog.show(this, "Favorite color", colors[slot], color -> {
                colors[slot] = color | 0xFF000000;
                dev.gistnote.app.widget.WidgetStyleFavorites.save(this, background, colors);
                renderFavorites(row, background);
            }));
            row.addView(target, new android.widget.LinearLayout.LayoutParams(0, size, 1));
        }
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
