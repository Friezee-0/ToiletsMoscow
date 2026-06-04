package com.moscow.toilets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    private final OnToiletClickListener listener;

    public ToiletListAdapter(OnToiletClickListener listener) {
        this.listener = listener;
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
        h.ratingBar.setRating((float) t.rating);
        h.tvRating.setText(String.format("%.1f", t.rating));
        h.tvHours.setText(t.workingHours != null ? t.workingHours : "");

        // Тип
        String typeLabel = typeRu(t.type);
        h.tvType.setText(typeLabel);
        h.tvType.setBackground(h.itemView.getContext().getResources()
                .getDrawable(typeBgRes(t.type), null));
        h.tvType.setTextColor(typeTextColor(h.itemView.getContext(), t.type));

        // Цветная полоса слева
        h.typeStripe.setBackgroundColor(stripeColor(h.itemView.getContext(), t.type));

        // Доступность
        if (Boolean.TRUE.equals(t.accessible)) {
            h.tvAccessible.setVisibility(View.VISIBLE);
        } else {
            h.tvAccessible.setVisibility(View.GONE);
        }

        // Расстояние
        if (t.distanceMeters != null && t.distanceMeters > 0) {
            h.tvDistance.setText(formatDistance(t.distanceMeters));
            h.tvDistance.setVisibility(View.VISIBLE);
        } else {
            h.tvDistance.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> listener.onToiletClick(t));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View typeStripe;
        final TextView tvName, tvAddress, tvType, tvAccessible, tvDistance, tvRating, tvHours;
        final RatingBar ratingBar;

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

    private int typeTextColor(Context ctx, String type) {
        if (type == null) return 0xFF424242;
        switch (type) {
            case "FREE":   return 0xFF2E7D32; // dark green
            case "PAID":   return 0xFFC62828; // dark red
            case "TROIKA": return 0xFF6A1B9A; // dark purple
            case "MALL":   return 0xFFE65100; // dark orange
            default:       return 0xFF424242;
        }
    }

    private int stripeColor(Context ctx, String type) {
        if (type == null) return 0xFF1E88E5;
        switch (type) {
            case "FREE":   return 0xFF43A047;
            case "PAID":   return 0xFFE53935;
            case "TROIKA": return 0xFF8E24AA;
            case "MALL":   return 0xFFFB8C00;
            default:       return 0xFF1E88E5;
        }
    }

    private String formatDistance(double metres) {
        if (metres < 1000) return String.format("%.0f м", metres);
        return String.format("%.1f км", metres / 1000);
    }
}
