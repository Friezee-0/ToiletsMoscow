package com.moscow.toilets;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

public class ToiletDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TOILET = "toilet";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toilet_detail);

        Toilet toilet = (Toilet) getIntent().getSerializableExtra(EXTRA_TOILET);
        if (toilet == null) { finish(); return; }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(toilet.title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Тип
        TextView tvType = findViewById(R.id.tvType);
        tvType.setText(typeRu(toilet.type));
        tvType.setBackground(getResources().getDrawable(typeBgRes(toilet.type), null));
        tvType.setTextColor(typeTextColor(toilet.type));

        // Доступность
        TextView tvAccessible = findViewById(R.id.tvAccessible);
        tvAccessible.setVisibility(Boolean.TRUE.equals(toilet.accessible) ? View.VISIBLE : View.GONE);

        // Название и адрес
        setText(R.id.tvTitle,   toilet.title);
        setText(R.id.tvAddress, toilet.address);

        // Рейтинг
        RatingBar ratingBar = findViewById(R.id.ratingBar);
        ratingBar.setRating((float) toilet.rating);
        setText(R.id.tvRating, String.format("%.1f / 5", toilet.rating));

        // График работы
        boolean hasHours = toilet.workingHours != null && !toilet.workingHours.isEmpty();
        if (hasHours) {
            setText(R.id.tvHours, toilet.workingHours);
        } else {
            findViewById(R.id.rowHours).setVisibility(View.GONE);
            findViewById(R.id.divHours).setVisibility(View.GONE);
        }

        // Расстояние
        if (toilet.distanceMeters != null && toilet.distanceMeters > 0) {
            setText(R.id.tvDistance, formatDistance(toilet.distanceMeters));
            findViewById(R.id.rowDistance).setVisibility(View.VISIBLE);
        }

        // Маршрут
        MaterialButton btnRoute = findViewById(R.id.btnRoute);
        btnRoute.setOnClickListener(v -> openRoute(toilet));
    }

    private void openRoute(Toilet t) {
        Uri appUri = Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~"
                + t.lat + "," + t.lng + "&rtt=pd");
        Intent appIntent = new Intent(Intent.ACTION_VIEW, appUri);
        if (appIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(appIntent);
        } else {
            Uri webUri = Uri.parse("https://yandex.ru/maps/?rtext=~"
                    + t.lat + "," + t.lng + "&rtt=pd");
            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
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
