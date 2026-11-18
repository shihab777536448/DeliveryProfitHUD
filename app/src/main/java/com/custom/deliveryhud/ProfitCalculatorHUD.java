package com.custom.deliveryhud;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class ProfitCalculatorHUD {

    private static final double FUEL_COST_PER_KM = 55.0; // تكلفة الوقود والصيانة لكل 1 كم
    private static final double ROAD_FACTOR = 1.35;      // معامل انحناء الشوارع داخل المدينة
    private static final double MIN_PROFIT_PER_KM = 150.0; // الحد الأدنى المقبول للربح لكل كم

    private static LinearLayout hudView;

    public static void analyzeAndShowOrder(final Activity activity, final String restaurantLocationName,
                                           final String customerLocationName, final double deliveryFee) {
        new Thread(() -> {
            try {
                Location currentLoc = getLastKnownLocation(activity);
                if (currentLoc == null) return;

                Geocoder geocoder = new Geocoder(activity, new Locale("ar"));
                Location restLoc = getLocationFromName(geocoder, restaurantLocationName, currentLoc);
                Location custLoc = getLocationFromName(geocoder, customerLocationName, currentLoc);

                double distToRest = (restLoc != null) ? (currentLoc.distanceTo(restLoc) / 1000.0) * ROAD_FACTOR : 0;
                double distToCust = (restLoc != null && custLoc != null) ? (restLoc.distanceTo(custLoc) / 1000.0) * ROAD_FACTOR : 0;
                double totalKm = distToRest + distToCust;

                double estimatedFuelCost = totalKm * FUEL_COST_PER_KM;
                double netProfit = deliveryFee - estimatedFuelCost;
                double profitPerKm = (totalKm > 0) ? (netProfit / totalKm) : netProfit;
                int estimatedMinutes = (int) ((totalKm / 25.0) * 60) + 5;

                new Handler(Looper.getMainLooper()).post(() ->
                    showFloatingCard(activity, distToRest, distToCust, totalKm, estimatedMinutes, netProfit, profitPerKm)
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static Location getLastKnownLocation(Context context) {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps != null && net != null) {
                return (gps.getTime() > net.getTime()) ? gps : net;
            }
            return (gps != null) ? gps : net;
        } catch (SecurityException e) {
            return null;
        }
    }

    private static Location getLocationFromName(Geocoder geocoder, String placeName, Location biasLocation) {
        if (placeName == null || placeName.trim().isEmpty()) return null;
        try {
            List<Address> addresses = geocoder.getFromLocationName(
                    placeName, 1,
                    biasLocation.getLatitude() - 0.3, biasLocation.getLongitude() - 0.3,
                    biasLocation.getLatitude() + 0.3, biasLocation.getLongitude() + 0.3
            );
            if (addresses != null && !addresses.isEmpty()) {
                Location loc = new Location("Geocoder");
                loc.setLatitude(addresses.get(0).getLatitude());
                loc.setLongitude(addresses.get(0).getLongitude());
                return loc;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void showFloatingCard(Activity activity, double dRest, double dCust,
                                         double totalKm, int minutes, double netProfit, double profitPerKm) {
        removeHUD(activity);

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 120;

        hudView = new LinearLayout(activity);
        hudView.setOrientation(LinearLayout.VERTICAL);
        hudView.setPadding(32, 24, 32, 24);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(24f);
        int borderColor = (profitPerKm >= MIN_PROFIT_PER_KM) ? Color.parseColor("#00E676") : Color.parseColor("#FF5252");
        bg.setColor(Color.parseColor("#E61A1A1A"));
        bg.setStroke(4, borderColor);
        hudView.setBackground(bg);

        TextView title = new TextView(activity);
        String statusBadge = (profitPerKm >= MIN_PROFIT_PER_KM) ? "🟢 طلب مربح" : "🔴 مسافة بعيدة / ربح منخفض";
        title.setText(statusBadge + "  (" + String.format(Locale.US, "%.0f", profitPerKm) + " / كم)");
        title.setTextColor(borderColor);
        title.setTextSize(15f);

        TextView details = new TextView(activity);
        details.setText(String.format(Locale.US,
                "المسافة: %.1f كم (للمطعم %.1f + للعميل %.1f)\nالوقت التقديري: %d دقيقة | الصافي: %.0f",
                totalKm, dRest, dCust, minutes, netProfit));
        details.setTextColor(Color.WHITE);
        details.setTextSize(13f);

        hudView.addView(title);
        hudView.addView(details);

        hudView.setOnClickListener(v -> removeHUD(activity));
        wm.addView(hudView, params);

        new Handler(Looper.getMainLooper()).postDelayed(() -> removeHUD(activity), 15000);
    }

    public static void removeHUD(Activity activity) {
        if (hudView != null && hudView.getParent() != null) {
            try {
                ((WindowManager) activity.getSystemService(Context.WINDOW_SERVICE)).removeView(hudView);
            } catch (Exception ignored) {}
            hudView = null;
        }
    }
}
