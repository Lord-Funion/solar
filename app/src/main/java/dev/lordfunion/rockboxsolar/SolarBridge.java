package dev.lordfunion.rockboxsolar;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

final class SolarBridge {
    static final String SOLAR_PACKAGE = "com.solar.launcher";

    static boolean installed(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SOLAR_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean open(Context context, String feature) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(SOLAR_PACKAGE);
        if (launch == null) return false;
        launch.putExtra("rockbox_solar_feature", feature);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(launch);
        return true;
    }
}
