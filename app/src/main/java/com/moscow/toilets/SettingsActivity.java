package com.moscow.toilets;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    static final String PREFS        = "app_settings";
    static final String KEY_RADIUS   = "search_radius";
    static final int    DEFAULT_RADIUS = 2000; // метров

    private static final int[] RADIUS_VALUES = {500, 1000, 2000, 5000, 10000};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.settings_title));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int savedRadius = prefs.getInt(KEY_RADIUS, DEFAULT_RADIUS);

        RadioGroup rgRadius = findViewById(R.id.rgRadius);
        int[] ids = {R.id.rb500, R.id.rb1000, R.id.rb2000, R.id.rb5000, R.id.rb10000};
        for (int i = 0; i < RADIUS_VALUES.length; i++) {
            if (RADIUS_VALUES[i] == savedRadius) {
                rgRadius.check(ids[i]);
                break;
            }
        }

        rgRadius.setOnCheckedChangeListener((group, checkedId) -> {
            int radius = DEFAULT_RADIUS;
            if      (checkedId == R.id.rb500)   radius = 500;
            else if (checkedId == R.id.rb1000)  radius = 1000;
            else if (checkedId == R.id.rb2000)  radius = 2000;
            else if (checkedId == R.id.rb5000)  radius = 5000;
            else if (checkedId == R.id.rb10000) radius = 10000;
            prefs.edit().putInt(KEY_RADIUS, radius).apply();
        });
    }
}
