package com.moscow.toilets;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/** Хранит избранные туалеты в SharedPreferences. Потокобезопасен для UI-потока. */
public class FavoritesManager {

    private static final String PREFS = "favorites_prefs";
    private static final String KEY   = "favorite_ids";

    private final SharedPreferences prefs;

    public FavoritesManager(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isFavorite(long id) {
        return prefs.getStringSet(KEY, new HashSet<>()).contains(String.valueOf(id));
    }

    public void toggle(long id) {
        Set<String> ids = new HashSet<>(prefs.getStringSet(KEY, new HashSet<>()));
        String key = String.valueOf(id);
        if (ids.contains(key)) ids.remove(key);
        else ids.add(key);
        prefs.edit().putStringSet(KEY, ids).apply();
    }

    public int count() {
        return prefs.getStringSet(KEY, new HashSet<>()).size();
    }
}
