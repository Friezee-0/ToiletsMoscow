package com.moscow.toilets;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

public class ToiletDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TOILET = "toilet";

    private Toilet toilet;
    private FavoritesManager favoritesManager;
    private MenuItem favoriteMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toilet_detail);

        toilet = (Toilet) getIntent().getSerializableExtra(EXTRA_TOILET);
        if (toilet == null) { finish(); return; }

        favoritesManager = new FavoritesManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(toilet.title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        bindViews();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detail, menu);
        favoriteMenuItem = menu.findItem(R.id.action_favorite);
        updateFavoriteIcon();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_favorite) {
            favoritesManager.toggle(toilet.id);
            updateFavoriteIcon();
            boolean now = favoritesManager.isFavorite(toilet.id);
            Snackbar.make(findViewById(android.R.id.content),
                    now ? getString(R.string.added_to_favorites) : getString(R.string.removed_from_favorites),
                    Snackbar.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_share) {
            shareToilet();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateFavoriteIcon() {
        if (favoriteMenuItem == null) return;
        boolean fav = favoritesManager.isFavorite(toilet.id);
        favoriteMenuItem.setIcon(fav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        favoriteMenuItem.setTitle(fav ? R.string.remove_from_favorites : R.string.action_favorite);
    }

    private void bindViews() {
        // Тип
        TextView tvType = findViewById(R.id.tvType);
        tvType.setText(typeRu(toilet.type));
        tvType.setBackground(getResources().getDrawable(typeBgRes(toilet.type), null));
        tvType.setTextColor(typeTextColor(toilet.type));

        // Доступность
        TextView tvAccessible = findViewById(R.id.tvAccessible);
        tvAccessible.setVisibility(Boolean.TRUE.equals(toilet.accessible) ? View.VISIBLE : View.GONE);

        setText(R.id.tvTitle, toilet.title);

        // Адрес с копированием по тапу
        TextView tvAddress = findViewById(R.id.tvAddress);
        if (tvAddress != null) {
            tvAddress.setText(toilet.address);
            tvAddress.setOnClickListener(v -> copyToClipboard(toilet.address));
        }

        // Рейтинг
        RatingBar ratingBar = findViewById(R.id.ratingBar);
        ratingBar.setRating((float) toilet.rating);
        setText(R.id.tvRating, String.format("%.1f / 5", toilet.rating));

        // График работы
        boolean hasHours = toilet.workingHours != null && !toilet.workingHours.isEmpty();
        if (hasHours) {
            setText(R.id.tvHours, toilet.workingHours);
        } else {
            View rowHours = findViewById(R.id.rowHours);
            View divHours = findViewById(R.id.divHours);
            if (rowHours != null) rowHours.setVisibility(View.GONE);
            if (divHours != null) divHours.setVisibility(View.GONE);
        }

        // Расстояние
        if (toilet.distanceMeters != null && toilet.distanceMeters > 0) {
            setText(R.id.tvDistance, formatDistance(toilet.distanceMeters));
            View rowDist = findViewById(R.id.rowDistance);
            if (rowDist != null) rowDist.setVisibility(View.VISIBLE);
        }

        // Маршрут
        MaterialButton btnRoute = findViewById(R.id.btnRoute);
        btnRoute.setOnClickListener(v -> openRoute());
    }

    private void copyToClipboard(String text) {
        if (text == null) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("address", text));
        Snackbar.make(findViewById(android.R.id.content),
                getString(R.string.address_copied), Snackbar.LENGTH_SHORT).show();
    }

    private void shareToilet() {
        String text = toilet.title + "\n"
                + toilet.address + "\n"
                + "https://yandex.ru/maps/?pt=" + toilet.lng + "," + toilet.lat
                + "&z=17&l=map";
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)));
    }

    private void openRoute() {
        Uri appUri = Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~"
                + toilet.lat + "," + toilet.lng + "&rtt=pd");
        Intent appIntent = new Intent(Intent.ACTION_VIEW, appUri);
        if (appIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(appIntent);
        } else {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://yandex.ru/maps/?rtext=~" + toilet.lat + "," + toilet.lng + "&rtt=pd")));
        }
    }

    private void setText(int viewId, String text) {
        TextView tv = findViewById(viewId);
        if (tv != null) tv.setText(text != null ? text : "");
    }

    private String typeRu(String type) {
        if (type == null) return "—";
        switch (type) {
            case "FREE":   return "Бесплатный";
            case "PAID":   return "Платный";
            case "TROIKA": return "По «Тройке»";
            case "MALL":   return "В ТЦ";
            default:       return type;
        }
    }

    private int typeBgRes(String type) {
        if (type == null) return R.drawable.bg_chip_rounded;
        switch (type) {
            case "FREE":   return R.drawable.bg_type_free;
            case "PAID":   return R.drawable.bg_type_paid;
            case "TROIKA": return R.drawable.bg_type_troika;
            case "MALL":   return R.drawable.bg_type_mall;
            default:       return R.drawable.bg_chip_rounded;
        }
    }

    private int typeTextColor(String type) {
        if (type == null) return 0xFF424242;
        switch (type) {
            case "FREE":   return 0xFF2E7D32;
            case "PAID":   return 0xFFC62828;
            case "TROIKA": return 0xFF6A1B9A;
            case "MALL":   return 0xFFE65100;
            default:       return 0xFF424242;
        }
    }

    private String formatDistance(double metres) {
        if (metres < 1000) return String.format("%.0f м от вас", metres);
        return String.format("%.1f км от вас", metres / 1000);
    }
}
