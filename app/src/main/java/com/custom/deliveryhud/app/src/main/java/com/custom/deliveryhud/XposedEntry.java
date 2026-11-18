package com.custom.deliveryhud;

import android.app.Activity;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedEntry implements IXposedHookLoadPackage {

    private static Activity currentActivity;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.smartapps.tawseeldelivery")) {
            return;
        }

        XposedBridge.log("DeliveryHUD: تم تحميل الوحدة بنجاح داخل تطبيق التوصيل");

        XposedHelpers.findAndHookMethod(
                "com.smartapps.tawseeldelivery.MainActivity",
                lpparam.classLoader,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        currentActivity = (Activity) param.thisObject;
                    }
                }
        );

        try {
            Class<?> remoteMessageClass = XposedHelpers.findClass(
                    "com.google.firebase.messaging.RemoteMessage",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    "com.google.firebase.messaging.FirebaseMessagingService",
                    lpparam.classLoader,
                    "onMessageReceived",
                    remoteMessageClass,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object remoteMessage = param.args[0];
                            java.util.Map<String, String> data = (java.util.Map<String, String>)
                                    XposedHelpers.callMethod(remoteMessage, "getData");

                            if (data != null && !data.isEmpty() && currentActivity != null) {
                                XposedBridge.log("DeliveryHUD Data: " + data.toString());

                                String restAddress = data.getOrDefault("restaurant_address", "");
                                String custAddress = data.getOrDefault("customer_address", "");
                                String feeStr = data.getOrDefault("delivery_fee", "0");

                                double fee = 0;
                                try {
                                    fee = Double.parseDouble(feeStr);
                                } catch (NumberFormatException ignored) {}

                                if (!restAddress.isEmpty() || !custAddress.isEmpty()) {
                                    ProfitCalculatorHUD.analyzeAndShowOrder(
                                            currentActivity, restAddress, custAddress, fee
                                    );
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log("DeliveryHUD Error: " + t.getMessage());
        }
    }
}
