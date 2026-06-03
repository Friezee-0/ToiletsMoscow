package com.moscow.toilets;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
import java.util.List;

/**
 * Главный экран «Туалеты Москвы».
 *
 * Делает 4 вещи:
 *  1) запрашивает runtime-разрешение ACCESS_FINE_LOCATION;
 *  2) определяет координаты устройства и плавно центрирует карту;
 *  3) рисует туалеты из JSON (имитация ответа бэкенда) как placemark-объекты;
 *  4) по тапу на маркер открывает BottomSheet с инфо и кнопкой маршрута.
 */
public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_REQUEST_CODE = 1001;
    // Центр Москвы — fallback, если геолокация недоступна
    private static final Point MOSCOW = new Point(55.751244, 37.618423);

    private MapView mapView;
    private Map map;
    private MapObjectCollection toiletsCollection;
    private FusedLocationProviderClient fusedLocationClient;

    // Слушатель тапов по маркерам. UserData каждого маркера — объект Toilet.
    private final MapObjectTapListener tapListener = (mapObject, point) -> {
        Object data = mapObject.getUserData();
        if (data instanceof Toilet) {
            showToiletBottomSheet((Toilet) data);
        }
        return true; // событие обработано
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ВАЖНО: ключ и init вызываются ДО setContentView
        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY);
        MapKitFactory.initialize(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapview);
        map = mapView.getMapWindow().getMap();
        toiletsCollection = map.getMapObjects().addCollection();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Стартовое позиционирование на центр Москвы (пока нет геолокации)
        moveCamera(MOSCOW, 12f);

        // 3) Загружаем точки (имитация ответа бэкенда) и рисуем их
        renderToilets(loadMockToiletsFromBackend());

        // 1) Просим разрешение на геолокацию
        requestLocationPermission();
    }

    // ----------------------------------------------------------------
    //  1. Runtime-разрешения
    // ----------------------------------------------------------------
   //private void requestLocationPermission() {
     //   if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
       //         == PackageManager.PERMISSION_GRANTED) {
         //   locateUser();
        //} else {
          //  ActivityCompat.requestPermissions(this,
            //        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
              //      LOCATION_REQUEST_CODE);
        //}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locateUser();
            } else {
                Toast.makeText(this, "Без геолокации карта останется на центре Москвы",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ----------------------------------------------------------------
    //  2. Определение координат и плавное центрирование
    // ----------------------------------------------------------------
    private void locateUser() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                Point me = new Point(location.getLatitude(), location.getLongitude());
                moveCamera(me, 15f); // плавно приближаемся к пользователю
            } else {
                Toast.makeText(this, "Не удалось определить местоположение",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void moveCamera(Point target, float zoom) {
        map.move(
                new CameraPosition(target, zoom, 0.0f, 0.0f),
                new Animation(Animation.Type.SMOOTH, 1.0f),
                null
        );
    }

    // ----------------------------------------------------------------
    //  3. Отрисовка маркеров
    // ----------------------------------------------------------------
    private void renderToilets(List<Toilet> toilets) {
        toiletsCollection.clear();
        ImageProvider icon = ImageProvider.fromResource(this, R.drawable.ic_toilet_marker);
        for (Toilet t : toilets) {
            PlacemarkMapObject placemark =
                    toiletsCollection.addPlacemark(new Point(t.lat, t.lng));
            placemark.setIcon(icon);
            placemark.setUserData(t);            // храним данные прямо в маркере
            placemark.addTapListener(tapListener);
        }
    }

    /**
     * Имитация получения JSON от бэкенда (GET /api/toilets/nearby).
     * В реальном приложении здесь будет Retrofit/OkHttp-запрос.
     */
    private List<Toilet> loadMockToiletsFromBackend() {
        String json = "["
                + "{\"id\":1,\"title\":\"Туалет на Красной площади\",\"address\":\"Красная площадь, 1\","
                +   "\"lat\":55.75393,\"lng\":37.620795,\"type\":\"PAID\",\"accessible\":true,"
                +   "\"rating\":4.2,\"workingHours\":\"Пн-Вс 08:00-22:00\"},"
                + "{\"id\":2,\"title\":\"ТЦ Охотный ряд\",\"address\":\"Манежная пл., 1\","
                +   "\"lat\":55.756,\"lng\":37.614,\"type\":\"MALL\",\"accessible\":true,"
                +   "\"rating\":4.6,\"workingHours\":\"Пн-Вс 10:00-22:00\"},"
                + "{\"id\":3,\"title\":\"Парк Зарядье\",\"address\":\"ул. Варварка, 6\","
                +   "\"lat\":55.751,\"lng\":37.629,\"type\":\"FREE\",\"accessible\":true,"
                +   "\"rating\":4.0,\"workingHours\":\"Круглосуточно\"}"
                + "]";
        Type listType = new TypeToken<List<Toilet>>() {}.getType();
        return new Gson().fromJson(json, listType);
    }

    // ----------------------------------------------------------------
    //  4. BottomSheet с информацией + маршрут
    // ----------------------------------------------------------------
    private void showToiletBottomSheet(Toilet toilet) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.bottom_sheet_toilet);

        TextView tvTitle  = dialog.findViewById(R.id.tvTitle);
        TextView tvAddr   = dialog.findViewById(R.id.tvAddress);
        TextView tvType   = dialog.findViewById(R.id.tvType);
        TextView tvRating = dialog.findViewById(R.id.tvRating);
        Button   btnRoute = dialog.findViewById(R.id.btnRoute);

        if (tvTitle != null)  tvTitle.setText(toilet.title);
        if (tvAddr != null)   tvAddr.setText(toilet.address);
        if (tvType != null)   tvType.setText("Тип: " + typeRu(toilet.type)
                + (Boolean.TRUE.equals(toilet.accessible) ? " · доступно для МГН" : ""));
        if (tvRating != null) tvRating.setText("Рейтинг: " + toilet.rating + " ★");

        if (btnRoute != null) {
            btnRoute.setOnClickListener(v -> {
                openRoute(toilet);
                dialog.dismiss();
            });
        }
        dialog.show();
    }

    /** Строим маршрут: открываем Яндекс.Карты, при отсутствии — браузер. */
    private void openRoute(Toilet t) {
        Uri appUri = Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~"
                + t.lat + "," + t.lng + "&rtt=pd"); // pd = пешком
        Intent appIntent = new Intent(Intent.ACTION_VIEW, appUri);
        if (appIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(appIntent);
        } else {
            Uri webUri = Uri.parse("https://yandex.ru/maps/?rtext=~"
                    + t.lat + "," + t.lng + "&rtt=pd");
            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
        }
    }

    private String typeRu(String type) {
        if (type == null) return "—";
        switch (type) {
            case "PAID":   return "платный";
            case "FREE":   return "бесплатный";
            case "TROIKA": return "по «Тройке»";
            case "MALL":   return "в ТЦ";
            default:        return type;
        }
    }

    // ----------------------------------------------------------------
    //  Жизненный цикл MapView (обязателен для Yandex MapKit)
    // ----------------------------------------------------------------
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
}
