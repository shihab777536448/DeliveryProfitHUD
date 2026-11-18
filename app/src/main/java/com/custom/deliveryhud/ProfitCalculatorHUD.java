package com.custom.deliveryhud;

import android.app.Activity;
import android.os.Bundle;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedEntry implements IXposedHookLoadPackage {

    private static Activity currentActivity;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // التأكد من استهداف تطبيق التوصيل فقط
        if (!lpparam.packageName.equals("com.smartapps.tawseeldelivery")) {
            return;
        }

        XposedBridge.log("DeliveryHUD: تم تحميل الوحدة بنجاح داخل تطبيق التوصيل");

        // 1. التقاط مرجع الشاشة الرئيسية (MainActivity) لعرض البطاقة العائمة فوقها
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

        // 2. اعتراض الإشعارات الواردة من Firebase لاستخراج بيانات الطلب
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
                            // قراءة بيانات الإشعار (Map<String, String>)
                            java.util.Map<String, String> data = (java.util.Map<String, String>)
                                    XposedHelpers.callMethod(remoteMessage, "getData");

                            if (data != null && !data.isEmpty() && currentActivity != null) {
                                XposedBridge.log("DeliveryHUD Data: " + data.toString());

                                // استخراج الحقول (تُضبط حسب مفاتيح الإشعار التي تظهر في السجل)
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
