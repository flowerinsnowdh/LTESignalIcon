package cn.flowerinsnow.ltesignalicon;

import android.content.res.Resources;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.android.systemui")) {
            return;
        }

        XposedHelpers.findAndHookMethod(
                Resources.class,
                "getDrawable", int.class, Resources.Theme.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (((int) param.args[0]) == 0x7f081883) {
                            param.args[0] = 0x7f08188d;
                        }
                    }
                }
        );
    }
}
