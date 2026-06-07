package com.moscow.toilets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ToiletListAdapter extends RecyclerView.Adapter<ToiletListAdapter.ViewHolder> {

    public interface OnToiletClickListener {
        void onToiletClick(Toilet toilet);
    }

    private final List<Toilet> items = new ArrayList<>();
    private final OnToiletClickListener clickListener;
    private final FavoritesManager      favoritesManager;
    private final Runnable               onFavoritesChanged;

    public ToiletListAdapter(OnToiletClickListener clickListener,
                              FavoritesManager favoritesManager,
                              Runnable onFavoritesChanged) {
        this.clickListener      = clickListener;
        this.favoritesManager   = favoritesManager;
        this.onFavoritesChanged = onFavoritesChanged;
    }

    public void submitList(List<Toilet> toilets) {
        items.clear();
        items.addAll(toilets);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_toilet, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Toilet t = items.get(position);

        h.tvName.setText(t.title);
        h.tvAddress.setText(t.address);
        boolean hasRating = t.rating > 0;
        h.ratingBar.setVisibility(hasRating ? View.VISIBLE : View.GONE);
        h.tvRating.setVisibility(hasRating ? View.VISIBLE : View.GONE);
        if (hasRating) {
            h.ratingBar.setRating((float) t.rating);
            h.tvRating.setText(String.format("%.1f", t.rating));
        }
        h.tvHours.setText(t.workingHours != null ? t.workingHours : "");

        h.tvType.setText(typeRu(t.type));
        h.tvType.setBackground(h.itemView.getContext().getResources()
                .getDrawable(typeBgRes(t.type), null));
        h.tvType.setTextColor(typeTextColor(t.type));
        h.typeStripe.setBackgroundColor(stripeColor(t.type));

        h.tvAccessible.setVisibility(Boolean.TRUE.equals(t.accessible) ? View.VISIBLE : View.GONE);

        if (t.distanceMeters != null && t.distanceMeters > 0) {
            h.tvDistance.setText(formatDistance(t.distanceMeters));
            h.tvDistance.setVisibility(View.VISIBLE);
        } else {
            h.tvDistance.setVisibility(View.GONE);
        }

        // Звезда избранного
        boolean fav = favoritesManager.isFavorite(t.id);
        h.btnFavorite.setImageResource(fav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        h.btnFavorite.setOnClickListener(v -> {
            favoritesManager.toggle(t.id);
            boolean nowFav = favoritesManager.isFavorite(t.id);
            h.btnFavorite.setImageResource(nowFav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            if (onFavoritesChanged != null) onFavoritesChanged.run();
        });

        h.itemView.setOnClickListener(v -> clickListener.onToiletClick(t));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View typeStripe;
        final TextView tvName, tvAddress, tvType, tvAccessible, tvDistance, tvRating, tvHours;
        final RatingBar ratingBar;
        final ImageButton btnFavorite;

        ViewHolder(View v) {
            super(v);
            typeStripe   = v.findViewById(R.id.typeStripe);
            tvName       = v.findViewById(R.id.tvName);
            tvAddress    = v.findViewById(R.id.tvAddress);
            tvType       = v.findViewById(R.id.tvType);
            tvAccessible = v.findViewById(R.id.tvAccessible);
            tvDistance   = v.findViewById(R.id.tvDistance);
            tvRating     = v.findViewById(R.id.tvRating);
            tvHours      = v.findViewById(R.id.tvHours);
            ratingBar    = v.findViewById(R.id.ratingBar);
            btnFavorite  = v.findViewById(R.id.btnFavorite);
        }
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
        if (type == null) return 0xFF6A6A6A;
        switch (type) {
            case "FREE":   return 0xFF3D7A44;
            case "PAID":   return 0xFFA84040;
            case "TROIKA": return 0xFF5E4BA0;
            case "MALL":   return 0xFF9A6020;
            default:       return 0xFF6A6A6A;
        }
    }

    private int stripeColor(String type) {
        if (type == null) return 0xFFA8C4DC;
        switch (type) {
            case "FREE":   return 0xFFA2CDA7;
            case "PAID":   return 0xFFF0A8A8;
            case "TROIKA": return 0xFFBEB0EE;
            case "MALL":   return 0xFFF5C98A;
            default:       return 0xFFA8C4DC;
        }
    }

    private String formatDistance(double metres) {
        if (metres < 1000) return String.format("%.0f м", metres);
        return String.format("%.1f км", metres / 1000);
    }
}
