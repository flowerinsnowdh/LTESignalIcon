# LTESignalIcon
Xposed 模块，针对 OnePlus 手机，将状态栏图标“4G”改为“LTE”，原本只是一时兴起，想学写 Xposed，没想到为了这一简单的操作，一折腾就是一天

我觉得这次的探索非常有意义，非常希望将其记录下来

# 探索过程
## 尝试之前
我使用 Android Studio 建了一个无 Activity 的安卓项目，简单修改了 Gradle 和 Git 配置后便开始尝试

第一个问题就把我难住了，Xposed/LSPosed 官方都没有提供文档，我只能去参考别的项目。我就去参考了一下别的简单的 Xposed 项目，学着样子

```toml
[versions]
xposedapi = "82"

[libraries]
xposedapi = { module = "de.robv.android.xposed:api", version.ref = "xposedapi" }
```

```kotlin
compileOnly(libs.xposedapi)
```

然后在我试图 `implements IXposedHookLoadPackage` 时，问题出现了——它始终找不到这个类，我尝试了无数次重新同步 Gradle、重启重置 IDE，都没有效果

后来我才发现，Xposed 是不在中央仓库中的，需要手动指定一下仓库

```kotlin
maven(url = "https://api.xposed.info/")
```

## 首次尝试
我先是问了 AI，AI 立马给我回复，建议我 Hook `com.android.systemui.statusbar.policy.NetworkControllerImpl`，然后修改`TextView`，具体写法如下

```java
XposedHelpers.findAndHookMethod(
    "com.android.systemui.statusbar.policy.NetworkControllerImpl",
    lpparam.classLoader,
    "updateDataActivity",
    new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Object mobileSignalController = XposedHelpers.getObjectField(param.thisObject, "mMobileSignalController");
            if (mobileSignalController == null) return;

            TextView textView = (TextView) XposedHelpers.getObjectField(mobileSignalController, "mNetworkTypeTextView");
            if (textView != null && "4G".equals(textView.getText().toString())) {
                textView.setText("LTE");
            }
        }
    }
);
```

我尝试了一下，出现了`ClassNotFoundException`，系统找不到 `NetworkControllerImpl` 类。

## 再次尝试
我想着，这很正常，难免会遇到一些新版本更改了内容的情况

第二次尝试，我使用 apktool 将 SystemUI 解包，获得了一大堆资源文件和 `smali` 文件，就这样我开始了我的逆向...

我就这样在不熟练逆向的情况下分析了好几个小时，其中不乏 Hook 了 `ClassLoader` 的 `loadClass` 方法、使用 `grep` 和 `find` 寻找关键词等操作...这一次我找到了 `com.android.systemui.statusbar.connectivity.NetworkControllerImpl`，我发现它其中引用了许多 `com.android.settingslib.mobile.TelephonyIcons` 中的图标，但引用于 `dispatchDemoCommand` 方法，我一听这个方法名字就觉得它应该不是干正事的。但是不管了，找了这么久，总要先试试吧？然后果不其然地...什么效果也没有

### 第三次尝试
和第二次差不多，这次我找到了 `com.android.systemui.statusbar.connectivity.MobileSignalController` 有一个方法叫作 `updateTelephony`，这可是黄金名称啊！我尝试将其 Hook

```java
XposedHelpers.findAndHookMethod(
        "com.android.systemui.statusbar.connectivity.MobileSignalController",
        lpparam.classLoader,
        "updateTelephony",
        new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                // SignalController mCurrentState = this.mCurrentState;
                Object mCurrentState = XposedHelpers.getObjectField(param.thisObject, "mCurrentState");
                // mCurrentState.iconGroup = TelephonyIcons.LTE;
                XposedHelpers.setObjectField(
                        mCurrentState, "iconGroup", XposedHelpers.getStaticObjectField(
                                XposedHelpers.findClass("com.android.settingslib.mobile.TelephonyIcons", lpparam.classLoader),
                        "LTE"
                        )
                );
            }
        }
```

似乎还是没有效果

## 第三次尝试
后来我一想，我是不是可以直接从字符串下手？我把字符串替换掉了，不就直接完成替换了吗？Hook 其实就是这样，和 Minecraft 的 Transform 修改一样，实现方法有千千万，挑一个就行。

于是我在 `strings.xml` 中找到了它

```xml
<string name="data_connection_4g">4G</string>
<string name="data_connection_lte">LTE</string>
```

并在 `R$string.smali` 中找到了

```plain
.field public static final data_connection_4g:I = 0x7f13041e
.field public static final data_connection_lte:I = 0x7f130432
```

顺藤摸瓜写出了

```java
XposedHelpers.findAndHookMethod(
        Resources.class,
        "getDrawable", int.class, Resources.Theme.class,
        new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (((int) param.args[0]) == 0x7f13041e) {
                    param.args[0] = 0x7f130432;
//                  XposedBridge.log(result + " = " + param.args[0]);
//                  for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
//                      XposedBridge.log(stackTraceElement.toString());
//                  }
                }
            }
        }
);
```

还是没有效果，但是看到那堆注释了吗？那就是我在继续探索的过程，那就是希望

## 第四次尝试
没错！我开始尝试从 StackTrace 中寻找答案了，它必然有一个调用的地方吧？我发现输出中有好多 `com.oplus` 开头的元素，嗯...一加肯定对它动了什么手脚吧

我又在 `com.oplus` 下的逆向代码中寻找了半晌，每一次都是看似无限接近于真相，但仍在瓶颈，这时的我无限接近放弃的念头，但我对移动通信技术如此热爱，想了半天最终没有放弃，事实证明我做了正确的决定

我发现 Color OS 似乎正在使用 ImageView 作为图标而不是 TextView，这很有可能就是修改 `strings` 无效的原因，我开始了我的分析

```java
XposedHelpers.findAndHookMethod(
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder",
        lpparam.classLoader,
        "bindCustEx$updateNetworkType",
        ImageView.class, "com.android.systemui.common.shared.model.Icon$Resource",
        new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("1: " + param.args[0]);
                XposedBridge.log("2: " + param.args[1]);
            }
        }
);
```

终于让我找到了该 ImageView，并通过对应的 `drawable` 找到了 `stat_signal_connected_4g_lte_big.xml`

你以为它是个 XML 文件就能难倒我了吗？它很明显是个矢量图啊！我把它复制到 Android Studio 里后一睹芳容

根据 R 类找到它们的 ID

```plain
.field public static final stat_signal_connected_4g_lte_big:I = 0x7f081868
```

最终写出

```java
XposedHelpers.findAndHookMethod(
        Resources.class,
        "getDrawable", int.class, Resources.Theme.class,
        new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (((int) param.args[0]) == 0x7f081868) {
                    param.args[0] = 0x7f081872;
                }
            }
        }
);
```

完美实现