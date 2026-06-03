package com.moscow.toilets;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yandex.mapkit.Animation;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.Map;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.MapObjectTapListener;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.runtime.image.ImageProvider;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Главный экран «Туалеты Москвы».
 *
 * 1. Запрашивает runtime-разрешение ACCESS_FINE_LOCATION (ActivityResultLauncher).
 * 2. Определяет координаты устройства и плавно центрирует карту.
 * 3. Отображает туалеты из JSON (имитация GET /api/toilets/nearby) как маркеры
 *    четырёх цветов по типу (FREE/PAID/TROIKA/MALL).
 * 4. По тапу на маркер открывает BottomSheet: адрес, тип, рейтинг,
 *    расписание, расстояние и кнопка маршрута.
 * 5. Фильтрация через ChipGroup вверху экрана.
 * 6. FAB внизу справа для повторного центрирования на пользователе.
 */
public class MainActivity extends AppCompatActivity {

    /** Центр Москвы — стартовая позиция, пока нет геолокации. */
    private static final Point MOSCOW_CENTER = new Point(55.751244, 37.618423);

    private MapView mapView;
    private Map map;
    private MapObjectCollection toiletsCollection;
    private FusedLocationProviderClient fusedLocationClient;

    private final List<Toilet> allToilets = new ArrayList<>();
    private String activeTypeFilter = null; // null → показать все типы
    private boolean accessibleOnly    = false;
    private Point   userLocation      = null;

    // ----------------------------------------------------------------
    //  Разрешение геолокации (ActivityResultContract — не deprecated)
    // ----------------------------------------------------------------

    private final ActivityResultLauncher<String> locationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    locateUser();
                } else {
                    Toast.makeText(this,
                            getString(R.string.no_location_fallback),
                            Toast.LENGTH_LONG).show();
                }
            });

    // ----------------------------------------------------------------
    //  Слушатель тапов по маркерам; userData каждого маркера — Toilet
    // ----------------------------------------------------------------

    private final MapObjectTapListener markerTapListener = (mapObject, point) -> {
        Object data = mapObject.getUserData();
        if (data instanceof Toilet) {
            showToiletBottomSheet((Toilet) data);
        }
        return true;
    };

    // ================================================================
    //  Lifecycle
    // ================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Ключ и инициализация MapKit — обязательно ДО setContentView
        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY);
        MapKitFactory.initialize(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView           = findViewById(R.id.mapview);
        map               = mapView.getMapWindow().getMap();
        toiletsCollection = map.getMapObjects().addCollection();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Стартовая позиция — центр Москвы
        moveCamera(MOSCOW_CENTER, 12f);

        setupFilterChips();
        setupFab();

        allToilets.addAll(loadMockToiletsFromBackend());
        applyFiltersAndRender();

        // Шаг 1: запросить доступ к геолокации
        requestLocationPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        MapKitFactory.getInstance().onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        mapView.onStop();
        MapKitFactory.getInstance().onStop();
        super.onStop();
    }

    // ================================================================
    //  1. Runtime-разрешения
    // ================================================================

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locateUser();
        } else {
            locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    // ================================================================
    //  2. Геолокация и плавное центрирование
    // ================================================================

    private void locateUser() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                userLocation = new Point(location.getLatitude(), location.getLongitude());
                moveCamera(userLocation, 15f);
            } else {
                Toast.makeText(this,
                        getString(R.string.location_unavailable),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void moveCamera(Point target, float zoom) {
        map.move(
                new CameraPosition(target, zoom, 0f, 0f),
                new Animation(Animation.Type.SMOOTH, 1.2f),
                null
        );
    }

    // ================================================================
    //  3. FAB «Моё местоположение»
    // ================================================================

    private void setupFab() {
        FloatingActionButton fab = findViewById(R.id.fabMyLocation);
        fab.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                locateUser();
            } else {
                requestLocationPermission();
            }
        });
    }

    // ================================================================
    //  4. Чипы-фильтры
    // ================================================================

    private void setupFilterChips() {
        ChipGroup chipGroup = findViewById(R.id.chipGroupFilters);

        // Пары: [отображаемое название, внутренний тег]
        String[][] filters = {
                {"Все",        null},
                {"Бесплатные", "FREE"},
                {"Платные",    "PAID"},
                {"По Тройке",  "TROIKA"},
                {"В ТЦ",       "MALL"},
                {"Доступные",  "ACCESSIBLE"}
        };

        for (int i = 0; i < filters.length; i++) {
            Chip chip = new Chip(this);
            chip.setText(filters[i][0]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);

            final String tag = filters[i][1];
            chip.setOnCheckedChangeListener((v, checked) -> {
                if (!checked) return;
                if ("ACCESSIBLE".equals(tag)) {
                    accessibleOnly    = true;
                    activeTypeFilter  = null;
                } else {
                    accessibleOnly    = false;
                    activeTypeFilter  = tag; // null → «Все»
                }
                applyFiltersAndRender();
            });
            chipGroup.addView(chip);
        }
    }

    // ================================================================
    //  5. Отрисовка маркеров
    // ================================================================

    private void applyFiltersAndRender() {
        List<Toilet> filtered = allToilets.stream()
                .filter(t -> activeTypeFilter == null || activeTypeFilter.equals(t.type))
                .filter(t -> !accessibleOnly  || Boolean.TRUE.equals(t.accessible))
                .collect(Collectors.toList());
        renderToilets(filtered);
    }

    private void renderToilets(List<Toilet> toilets) {
        toiletsCollection.clear();
        for (Toilet t : toilets) {
            PlacemarkMapObject placemark =
                    toiletsCollection.addPlacemark(new Point(t.lat, t.lng));
            placemark.setIcon(getMarkerIcon(t.type));
            placemark.setUserData(t);
            placemark.addTapListener(markerTapListener);
        }
    }

    /** Возвращает иконку нужного цвета в зависимости от типа туалета. */
    private ImageProvider getMarkerIcon(String type) {
        if (type == null) {
            return ImageProvider.fromResource(this, R.drawable.ic_marker_default);
        }
        switch (type) {
            case "FREE":   return ImageProvider.fromResource(this, R.drawable.ic_marker_free);
            case "PAID":   return ImageProvider.fromResource(this, R.drawable.ic_marker_paid);
            case "TROIKA": return ImageProvider.fromResource(this, R.drawable.ic_marker_troika);
            case "MALL":   return ImageProvider.fromResource(this, R.drawable.ic_marker_mall);
            default:       return ImageProvider.fromResource(this, R.drawable.ic_marker_default);
        }
    }

    // ================================================================
    //  6. Тестовые данные (имитация GET /api/toilets/nearby)
    //     В боевом приложении здесь будет Retrofit / OkHttp запрос.
    // ================================================================

    private List<Toilet> loadMockToiletsFromBackend() {
        String json = "["
                // ── Бесплатные ──────────────────────────────────────────
                + "{\"id\":1,\"title\":\"Парк Зарядье\","
                +  "\"address\":\"ул. Варварка, 6\","
                +  "\"lat\":55.7508,\"lng\":37.6291,"
                +  "\"type\":\"FREE\",\"accessible\":true,"
                +  "\"rating\":4.0,\"workingHours\":\"Круглосуточно\"},"
                + "{\"id\":2,\"title\":\"Александровский сад\","
                +  "\"address\":\"ул. Воздвиженка, 1\","
                +  "\"lat\":55.7513,\"lng\":37.6099,"
                +  "\"type\":\"FREE\",\"accessible\":true,"
                +  "\"rating\":3.8,\"workingHours\":\"09:00–21:00\"},"
                + "{\"id\":11,\"title\":\"Парк Горького\","
                +  "\"address\":\"Крымский вал, 9\","
                +  "\"lat\":55.7302,\"lng\":37.6030,"
                +  "\"type\":\"FREE\",\"accessible\":true,"
                +  "\"rating\":4.3,\"workingHours\":\"08:00–22:00\"},"
                + "{\"id\":12,\"title\":\"Воробьёвы горы (смотровая)\","
                +  "\"address\":\"Воробьёвы горы, 1\","
                +  "\"lat\":55.7102,\"lng\":37.5430,"
                +  "\"type\":\"FREE\",\"accessible\":false,"
                +  "\"rating\":3.6,\"workingHours\":\"09:00–20:00\"},"
                // ── Платные ─────────────────────────────────────────────
                + "{\"id\":3,\"title\":\"Туалет на Красной площади\","
                +  "\"address\":\"Красная площадь, 1\","
                +  "\"lat\":55.7539,\"lng\":37.6208,"
                +  "\"type\":\"PAID\",\"accessible\":true,"
                +  "\"rating\":4.2,\"workingHours\":\"08:00–22:00\"},"
                + "{\"id\":4,\"title\":\"Арбат (у д. 28)\","
                +  "\"address\":\"Арбат, 28\","
                +  "\"lat\":55.7490,\"lng\":37.5893,"
                +  "\"type\":\"PAID\",\"accessible\":false,"
                +  "\"rating\":3.5,\"workingHours\":\"10:00–22:00\"},"
                // ── По Тройке ───────────────────────────────────────────
                + "{\"id\":5,\"title\":\"Ст. м. Охотный ряд\","
                +  "\"address\":\"Охотный ряд, вестибюль\","
                +  "\"lat\":55.7559,\"lng\":37.6144,"
                +  "\"type\":\"TROIKA\",\"accessible\":false,"
                +  "\"rating\":3.2,\"workingHours\":\"05:30–01:00\"},"
                + "{\"id\":6,\"title\":\"Ст. м. Китай-город\","
                +  "\"address\":\"Китай-город, вестибюль\","
                +  "\"lat\":55.7568,\"lng\":37.6310,"
                +  "\"type\":\"TROIKA\",\"accessible\":false,"
                +  "\"rating\":3.0,\"workingHours\":\"05:30–01:00\"},"
                + "{\"id\":13,\"title\":\"Ст. м. Арбатская\","
                +  "\"address\":\"Арбатская, вестибюль\","
                +  "\"lat\":55.7516,\"lng\":37.5966,"
                +  "\"type\":\"TROIKA\",\"accessible\":false,"
                +  "\"rating\":2.9,\"workingHours\":\"05:30–01:00\"},"
                // ── В ТЦ ────────────────────────────────────────────────
                + "{\"id\":7,\"title\":\"ТЦ \\\"Охотный ряд\\\"\","
                +  "\"address\":\"Манежная пл., 1\","
                +  "\"lat\":55.7560,\"lng\":37.6140,"
                +  "\"type\":\"MALL\",\"accessible\":true,"
                +  "\"rating\":4.6,\"workingHours\":\"10:00–22:00\"},"
                + "{\"id\":8,\"title\":\"ТЦ \\\"Европейский\\\"\","
                +  "\"address\":\"пл. Киевского вокзала, 2\","
                +  "\"lat\":55.7447,\"lng\":37.5664,"
                +  "\"type\":\"MALL\",\"accessible\":true,"
                +  "\"rating\":4.5,\"workingHours\":\"10:00–22:00\"},"
                + "{\"id\":9,\"title\":\"ГУМ\","
                +  "\"address\":\"Красная площадь, 3\","
                +  "\"lat\":55.7549,\"lng\":37.6215,"
                +  "\"type\":\"MALL\",\"accessible\":true,"
                +  "\"rating\":4.8,\"workingHours\":\"10:00–22:00\"},"
                + "{\"id\":10,\"title\":\"ТЦ \\\"Атриум\\\"\","
                +  "\"address\":\"Земляной вал, 33\","
                +  "\"lat\":55.7493,\"lng\":37.6511,"
                +  "\"type\":\"MALL\",\"accessible\":true,"
                +  "\"rating\":4.4,\"workingHours\":\"10:00–22:00\"}"
                + "]";

        Type listType = new TypeToken<List<Toilet>>() {}.getType();
        return new Gson().fromJson(json, listType);
    }

    // ================================================================
    //  7. BottomSheet с детальной информацией + маршрут
    // ================================================================

    private void showToiletBottomSheet(Toilet toilet) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.bottom_sheet_toilet);

        bindText(dialog, R.id.tvTitle,   toilet.title);
        bindText(dialog, R.id.tvAddress, toilet.address);

        // Тип + доступность
        TextView tvType = dialog.findViewById(R.id.tvType);
        if (tvType != null) {
            String typeStr = typeRu(toilet.type);
            if (Boolean.TRUE.equals(toilet.accessible)) typeStr += "  ♿";
            tvType.setText(typeStr);
            tvType.setBackgroundColor(typeBgColor(toilet.type));
        }

        // Рейтинг
        TextView tvRating = dialog.findViewById(R.id.tvRating);
        if (tvRating != null) {
            tvRating.setText(String.format("%.1f ★", toilet.rating));
        }

        // График работы
        TextView tvHours = dialog.findViewById(R.id.tvWorkingHours);
        if (tvHours != null) {
            boolean hasHours = toilet.workingHours != null && !toilet.workingHours.isEmpty();
            tvHours.setVisibility(hasHours ? View.VISIBLE : View.GONE);
            if (hasHours) tvHours.setText(toilet.workingHours);
        }

        // Расстояние (только если известно местоположение пользователя)
        TextView tvDist = dialog.findViewById(R.id.tvDistance);
        if (tvDist != null) {
            if (userLocation != null) {
                double metres = haversineMeters(
                        userLocation.getLatitude(), userLocation.getLongitude(),
                        toilet.lat, toilet.lng);
                tvDist.setText(formatDistance(metres));
                tvDist.setVisibility(View.VISIBLE);
            } else {
                tvDist.setVisibility(View.GONE);
            }
        }

        // Кнопка маршрута
        Button btnRoute = dialog.findViewById(R.id.btnRoute);
        if (btnRoute != null) {
            btnRoute.setOnClickListener(v -> {
                openRoute(toilet);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void bindText(BottomSheetDialog d, int viewId, String text) {
        TextView tv = d.findViewById(viewId);
        if (tv != null) tv.setText(text);
    }

    // ================================================================
    //  8. Построение маршрута (Яндекс.Карты → браузер как fallback)
    // ================================================================

    private void openRoute(Toilet t) {
        // yandexmaps deep-link (пешеход)
        Uri appUri = Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~"
                + t.lat + "," + t.lng + "&rtt=pd");
        Intent appIntent = new Intent(Intent.ACTION_VIEW, appUri);
        if (appIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(appIntent);
        } else {
            // Fallback: веб-версия Яндекс.Карт
            Uri webUri = Uri.parse("https://yandex.ru/maps/?rtext=~"
                    + t.lat + "," + t.lng + "&rtt=pd");
            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
        }
    }

    // ================================================================
    //  Вспомогательные методы
    // ================================================================

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

    /** Мягкий фоновый цвет бейджа типа. */
    private int typeBgColor(String type) {
        if (type == null) return 0xFFE3F2FD;
        switch (type) {
            case "FREE":   return 0xFFE8F5E9; // light green
            case "PAID":   return 0xFFFFEBEE; // light red
            case "TROIKA": return 0xFFF3E5F5; // light purple
            case "MALL":   return 0xFFFFF3E0; // light orange
            default:       return 0xFFE3F2FD; // light blue
        }
    }

    /** Расстояние по формуле Хаверсина (в метрах). */
    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String formatDistance(double metres) {
        if (metres < 1000) {
            return String.format("%.0f м от вас", metres);
        }
        return String.format("%.1f км от вас", metres / 1000);
    }
}
