package com.moscow.toilets;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
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
import com.yandex.mapkit.user_location.UserLocationLayer;
import com.yandex.runtime.image.ImageProvider;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private static final Point MOSCOW_CENTER = new Point(55.751244, 37.618423);

    // Карта
    private MapView mapView;
    private Map map;
    private MapObjectCollection toiletsCollection;
    private UserLocationLayer userLocationLayer;
    private final java.util.Map<Integer, ImageProvider> iconCache = new HashMap<>();

    // Список
    private RecyclerView recyclerView;
    private ToiletListAdapter listAdapter;
    private TextView tvResultCount;
    private View emptyState;
    private EditText etSearch;
    private ImageButton btnClearSearch;

    // UI-переключатели
    private View listContainer;
    private View chipsContainer;
    private FloatingActionButton fabMyLocation;

    // Данные
    private final List<Toilet> allToilets = new ArrayList<>();
    private String activeTypeFilter = null;
    private boolean accessibleOnly  = false;
    private String  searchQuery     = "";
    private boolean isListMode      = false;
    private boolean isFavoritesMode = false;
    private Point   userLocation    = null;

    // Избранное
    private FavoritesManager favoritesManager;

    // Геолокация
    private FusedLocationProviderClient fusedLocationClient;

    private final ActivityResultLauncher<String> locationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    locateUser();
                } else {
                    Toast.makeText(this, getString(R.string.no_location_fallback),
                            Toast.LENGTH_LONG).show();
                }
            });

    private final MapObjectTapListener markerTapListener = (mapObject, point) -> {
        if (mapObject.getUserData() instanceof Toilet) {
            showToiletBottomSheet((Toilet) mapObject.getUserData());
        }
        return true;
    };

    // ================================================================
    //  Lifecycle
    // ================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY);
        MapKitFactory.initialize(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        favoritesManager = new FavoritesManager(this);

        // Карта
        mapView           = findViewById(R.id.mapview);
        map               = mapView.getMapWindow().getMap();
        toiletsCollection = map.getMapObjects().addCollection();
        userLocationLayer = MapKitFactory.getInstance()
                .createUserLocationLayer(mapView.getMapWindow());
        userLocationLayer.setVisible(true);
        userLocationLayer.setHeadingEnabled(false);

        // Список
        recyclerView   = findViewById(R.id.recyclerView);
        tvResultCount  = findViewById(R.id.tvResultCount);
        listContainer  = findViewById(R.id.listContainer);
        chipsContainer = findViewById(R.id.chipsContainer);
        fabMyLocation  = findViewById(R.id.fabMyLocation);
        emptyState     = findViewById(R.id.emptyState);
        etSearch       = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        listAdapter = new ToiletListAdapter(
                this::openToiletDetail,
                favoritesManager,
                this::onFavoritesChanged
        );
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(listAdapter);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        moveCamera(MOSCOW_CENTER, 12f);
        setupBottomNav();
        setupFilterChips();
        setupFab();
        setupSearch();
        setupAddFab();
        setupSettingsButton();

        allToilets.addAll(loadMockToiletsFromBackend());
        applyFiltersAndRender();

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

    @Override
    protected void onResume() {
        super.onResume();
        if (isListMode || isFavoritesMode) updateList();
    }

    // ================================================================
    //  Нижняя навигация (Карта / Список / Избранное)
    // ================================================================

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_map) {
                showMapMode();
                return true;
            } else if (id == R.id.nav_list) {
                showListMode();
                return true;
            } else if (id == R.id.nav_favorites) {
                showFavoritesMode();
                return true;
            }
            return false;
        });
    }

    private void showMapMode() {
        isListMode = false;
        isFavoritesMode = false;
        listContainer.setVisibility(View.GONE);
        chipsContainer.setVisibility(View.VISIBLE);
        fabMyLocation.setVisibility(View.VISIBLE);
    }

    private void showListMode() {
        isListMode = true;
        isFavoritesMode = false;
        listContainer.setVisibility(View.VISIBLE);
        chipsContainer.setVisibility(View.GONE);
        fabMyLocation.setVisibility(View.GONE);
        updateList();
    }

    private void showFavoritesMode() {
        isListMode = false;
        isFavoritesMode = true;
        listContainer.setVisibility(View.VISIBLE);
        chipsContainer.setVisibility(View.GONE);
        fabMyLocation.setVisibility(View.GONE);
        updateList();
    }

    private void onFavoritesChanged() {
        if (isFavoritesMode) updateList();
    }

    // ================================================================
    //  Разрешения
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
    //  Геолокация
    // ================================================================

    private void locateUser() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                userLocation = new Point(location.getLatitude(), location.getLongitude());
                for (Toilet t : allToilets) {
                    t.distanceMeters = haversineMeters(
                            userLocation.getLatitude(), userLocation.getLongitude(),
                            t.lat, t.lng);
                }
                allToilets.sort((a, b) -> Double.compare(
                        a.distanceMeters != null ? a.distanceMeters : Double.MAX_VALUE,
                        b.distanceMeters != null ? b.distanceMeters : Double.MAX_VALUE));

                moveCamera(userLocation, 15f);
                applyFiltersAndRender();
            } else {
                Toast.makeText(this, getString(R.string.location_unavailable),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void moveCamera(Point target, float zoom) {
        map.move(new CameraPosition(target, zoom, 0f, 0f),
                new Animation(Animation.Type.SMOOTH, 1.2f), null);
    }

    // ================================================================
    //  FAB
    // ================================================================

    private void setupFab() {
        fabMyLocation.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                locateUser();
            } else {
                requestLocationPermission();
            }
        });
    }

    private void setupAddFab() {
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.add_toilet_coming_soon),
                        Toast.LENGTH_SHORT).show());
    }

    // ================================================================
    //  Настройки
    // ================================================================

    private void setupSettingsButton() {
        ImageButton btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v ->
                    startActivity(new Intent(this, SettingsActivity.class)));
        }
    }

    // ================================================================
    //  Чипы-фильтры (режим карты)
    // ================================================================

    private void setupFilterChips() {
        ChipGroup chipGroup = findViewById(R.id.chipGroupFilters);
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
                    accessibleOnly   = true;
                    activeTypeFilter = null;
                } else {
                    accessibleOnly   = false;
                    activeTypeFilter = tag;
                }
                applyFiltersAndRender();
            });
            chipGroup.addView(chip);
        }
    }

    // ================================================================
    //  Поиск (режим списка)
    // ================================================================

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase(Locale.ROOT);
                btnClearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                updateList();
            }
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.clearFocus();
        });
    }

    // ================================================================
    //  Фильтрация и рендер
    // ================================================================

    private List<Toilet> getFilteredToilets() {
        if (isFavoritesMode) {
            return allToilets.stream()
                    .filter(t -> favoritesManager.isFavorite(t.id))
                    .collect(Collectors.toList());
        }
        return allToilets.stream()
                .filter(t -> activeTypeFilter == null || activeTypeFilter.equals(t.type))
                .filter(t -> !accessibleOnly  || Boolean.TRUE.equals(t.accessible))
                .filter(t -> searchQuery.isEmpty()
                        || (t.title   != null && t.title.toLowerCase(Locale.ROOT).contains(searchQuery))
                        || (t.address != null && t.address.toLowerCase(Locale.ROOT).contains(searchQuery)))
                .collect(Collectors.toList());
    }

    private void applyFiltersAndRender() {
        List<Toilet> filtered = getFilteredToilets();
        renderToilets(filtered);
        if (isListMode || isFavoritesMode) updateList(filtered);
    }

    private void updateList() {
        updateList(getFilteredToilets());
    }

    private void updateList(List<Toilet> filtered) {
        listAdapter.submitList(filtered);
        boolean isEmpty = filtered.isEmpty();
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        if (isFavoritesMode) {
            tvResultCount.setText(getString(R.string.favorites_count, filtered.size()));
            if (isEmpty) {
                ((TextView) findViewById(R.id.tvEmptyTitle))
                        .setText(R.string.empty_favorites_title);
                ((TextView) findViewById(R.id.tvEmptySubtitle))
                        .setText(R.string.empty_favorites_subtitle);
            }
        } else {
            tvResultCount.setText(filtered.size() + " " + getString(R.string.toilets_nearby));
            if (isEmpty) {
                ((TextView) findViewById(R.id.tvEmptyTitle))
                        .setText(R.string.empty_title);
                ((TextView) findViewById(R.id.tvEmptySubtitle))
                        .setText(R.string.empty_subtitle);
            }
        }
    }

    // ================================================================
    //  Маркеры на карте
    // ================================================================

    private void renderToilets(List<Toilet> toilets) {
        toiletsCollection.clear();
        for (Toilet t : toilets) {
            PlacemarkMapObject p = toiletsCollection.addPlacemark(new Point(t.lat, t.lng));
            p.setIcon(getMarkerIcon(t.type));
            p.setUserData(t);
            p.addTapListener(markerTapListener);
        }
    }

    private ImageProvider getMarkerIcon(String type) {
        int resId = markerResId(type);
        ImageProvider cached = iconCache.get(resId);
        if (cached != null) return cached;
        ImageProvider provider = ImageProvider.fromBitmap(vectorToBitmap(resId));
        iconCache.put(resId, provider);
        return provider;
    }

    private int markerResId(String type) {
        if (type == null) return R.drawable.ic_marker_default;
        switch (type) {
            case "FREE":   return R.drawable.ic_marker_free;
            case "PAID":   return R.drawable.ic_marker_paid;
            case "TROIKA": return R.drawable.ic_marker_troika;
            case "MALL":   return R.drawable.ic_marker_mall;
            default:       return R.drawable.ic_marker_default;
        }
    }

    private Bitmap vectorToBitmap(int drawableResId) {
        Drawable drawable = ContextCompat.getDrawable(this, drawableResId);
        if (drawable == null) throw new IllegalArgumentException("Drawable not found: " + drawableResId);
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0) w = 96;
        if (h <= 0) h = 128;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, w, h);
        drawable.draw(canvas);
        return bmp;
    }

    // ================================================================
    //  Открытие детального экрана
    // ================================================================

    private void openToiletDetail(Toilet toilet) {
        Intent intent = new Intent(this, ToiletDetailActivity.class);
        intent.putExtra(ToiletDetailActivity.EXTRA_TOILET, toilet);
        startActivity(intent);
    }

    // ================================================================
    //  BottomSheet (быстрый просмотр при тапе на маркер карты)
    // ================================================================

    private void showToiletBottomSheet(Toilet toilet) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.bottom_sheet_toilet);

        bindText(dialog, R.id.tvTitle,   toilet.title);
        bindText(dialog, R.id.tvAddress, toilet.address);

        TextView tvType = dialog.findViewById(R.id.tvType);
        if (tvType != null) {
            String s = typeRu(toilet.type);
            if (Boolean.TRUE.equals(toilet.accessible)) s += "  ♿";
            tvType.setText(s);
            tvType.setBackgroundColor(typeBgColor(toilet.type));
        }

        TextView tvRating = dialog.findViewById(R.id.tvRating);
        if (tvRating != null) tvRating.setText(String.format("%.1f ★", toilet.rating));

        TextView tvHours = dialog.findViewById(R.id.tvWorkingHours);
        if (tvHours != null) {
            boolean hasHours = toilet.workingHours != null && !toilet.workingHours.isEmpty();
            tvHours.setVisibility(hasHours ? View.VISIBLE : View.GONE);
            if (hasHours) tvHours.setText(toilet.workingHours);
        }

        TextView tvDist = dialog.findViewById(R.id.tvDistance);
        if (tvDist != null) {
            if (userLocation != null) {
                double d = haversineMeters(userLocation.getLatitude(), userLocation.getLongitude(),
                        toilet.lat, toilet.lng);
                tvDist.setText(formatDistance(d));
                tvDist.setVisibility(View.VISIBLE);
            } else {
                tvDist.setVisibility(View.GONE);
            }
        }

        Button btnRoute = dialog.findViewById(R.id.btnRoute);
        if (btnRoute != null) btnRoute.setOnClickListener(v -> {
            openRoute(toilet);
            dialog.dismiss();
        });

        View btnDetail = dialog.findViewById(R.id.btnDetail);
        if (btnDetail != null) btnDetail.setOnClickListener(v -> {
            openToiletDetail(toilet);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void bindText(BottomSheetDialog d, int viewId, String text) {
        TextView tv = d.findViewById(viewId);
        if (tv != null) tv.setText(text != null ? text : "");
    }

    // ================================================================
    //  Маршрут
    // ================================================================

    private void openRoute(Toilet t) {
        Uri appUri = Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~" + t.lat + "," + t.lng + "&rtt=pd");
        Intent appIntent = new Intent(Intent.ACTION_VIEW, appUri);
        if (appIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(appIntent);
        } else {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://yandex.ru/maps/?rtext=~" + t.lat + "," + t.lng + "&rtt=pd")));
        }
    }

    // ================================================================
    //  Данные (имитация GET /api/toilets/nearby)
    // ================================================================

    private List<Toilet> loadMockToiletsFromBackend() {
        String json = "["
                + "{\"id\":1,\"title\":\"Парк Зарядье\",\"address\":\"ул. Варварка, 6\","
                +  "\"lat\":55.7508,\"lng\":37.6291,\"type\":\"FREE\",\"accessible\":true,"
                +  "\"rating\":4.0,\"workingHours\":\"Круглосуточно\"},"
                + "{\"id\":2,\"title\":\"Александровский сад\",\"address\":\"ул. Воздвиженка, 1\","
                +  "\"lat\":55.7513,\"lng\":37.6099,\"type\":\"FREE\",\"accessible\":true,"
                +  "\"rating\":3.8,\"workingHours\":\"09:00–21:00\"},"
                + "{\"id\":3,\"title\":\"Туалет на Красной площади\",\"address\":\"Красная площадь, 1\","
                +  "\"lat\":55.7539,\"lng\":37.6208,\"type\":\"PAID\",\"accessible\":true,"
                +  "\"rating\":4.2,\"workingHours\":\"08:00–22:00\"},"
                + "{\"id\":4,\"title\":\"Арбат (у д. 28)\",\"address\":\"Арбат, 28\","
                +  "\"lat\":55.7490,\"lng\":37.5893,\"type\":\"PAID\",\"accessible\":false,"
                +  "\"rating\":3.5,\"workingHours\":\"10:00–22:00\"},"
                + "{\"id\":5,\"title\":\"Ст. м. Охотный ряд\",\"address\":\"Охотный ряд, вестибюль\","
                +  "\"lat\":55.7559,\"lng\":37.6144,\"type\":\"TROIKA\",\"accessible\":false,"
                +  "\"rating\":3.2,\"workingHours\":\"05:30–01:00\"},"
                + "{\"id\":6,\"title\":\"Ст. м. Китай-город\",\"address\":\"Китай-город, вестибюль\","
                +  "\"lat\":55.7568,\"lng\":37.6310,\"type\":\"TROIKA\",\"accessible\":false,"
                +  "\"rating\":3.0,\"workingHours\":\"05:30–01:00\"},"
                + "{\"id\":7,\"title\":\"ТЦ \\\"Охотный ряд\\\"\",\"address\":\"Манежная пл., 1\","
                +  "\"lat\":55.7560,\"lng\":37.6140,\"type\":\"MALL\",\"accessible\":true,"
                +  "\"rating\":4.6,\"workingHours\":\"10:00–22:00\"},"
                + "{\"id\":8,\"title\":\"ТЦ \\\"Европейский\\\"\",\"address\":\"пл. Киевского вокзала, 2\","
                +  "\"lat\":55.7447,\"lng\":37.5664,\"type\":\"MALL\",\"accessible\":true,"
                +  "\"rating\":4.5,\"workingHours\":\"10:00–22:00\"},"
                + "{\"id\":9,\"title\":\"ГУМ\",\"address\":\"Красная площадь, 3\","
                +  "\"lat\":55.7549,\"lng\":37.6215,\"type\":\"MALL\",\"accessible\":true,"
                +  "\"rating\":4.8,\"workingHours\":\"10:00–22:00\"},"
                + "{\"id\":10,\"title\":\"ТЦ \\\"Атриум\\\"\",\"address\":\"Земляной вал, 33\","
                +  "\"lat\":55.7493,\"lng\":37.6511,\"type\":\"MALL\",\"accessible\":true,"
                +  "\"rating\":4.4,\"workingHours\":\"10:00–22:00\"},"
                + "{\"id\":11,\"title\":\"Парк Горького\",\"address\":\"Крымский вал, 9\","
                +  "\"lat\":55.7302,\"lng\":37.6030,\"type\":\"FREE\",\"accessible\":true,"
                +  "\"rating\":4.3,\"workingHours\":\"08:00–22:00\"},"
                + "{\"id\":12,\"title\":\"Воробьёвы горы\",\"address\":\"Воробьёвы горы, 1\","
                +  "\"lat\":55.7102,\"lng\":37.5430,\"type\":\"FREE\",\"accessible\":false,"
                +  "\"rating\":3.6,\"workingHours\":\"09:00–20:00\"},"
                + "{\"id\":13,\"title\":\"Ст. м. Арбатская\",\"address\":\"Арбатская, вестибюль\","
                +  "\"lat\":55.7516,\"lng\":37.5966,\"type\":\"TROIKA\",\"accessible\":false,"
                +  "\"rating\":2.9,\"workingHours\":\"05:30–01:00\"}"
                + "]";
        Type listType = new TypeToken<List<Toilet>>() {}.getType();
        return new Gson().fromJson(json, listType);
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

    private int typeBgColor(String type) {
        if (type == null) return 0xFFEEECE8;
        switch (type) {
            case "FREE":   return 0xFFD6EDDA;
            case "PAID":   return 0xFFF9DEDE;
            case "TROIKA": return 0xFFE3DAFF;
            case "MALL":   return 0xFFFDEBD0;
            default:       return 0xFFEEECE8;
        }
    }

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
        if (metres < 1000) return String.format("%.0f м от вас", metres);
        return String.format("%.1f км от вас", metres / 1000);
    }
}
