package com.titan2.toolkit;

import android.util.Log;
import android.view.KeyEvent;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * Makes the OEM letter-key shortcuts fire from any foreground app.
 *
 * <p>The stock AGUI logic only treats a key event as a shortcut candidate when the focused
 * window belongs to the home app or the Magic Key is held ({@code isHomePackage}). Returning
 * true from that gate for A-Z lets the OEM code below it ({@code ShortcutInterceptKey}) decide
 * whether the letter is configured; unassigned letters still reach the foreground app.
 * IME visibility and keyguard are checked by the OEM before this gate.
 */
final class ShortcutHooks {

    private static final String AGUI_SHORTCUT =
            "com.agui.server.functional.AguiKeyboardShortcut";

    static void install(XposedModule module, ClassLoader cl) {
        Class<?> cls;
        try {
            cls = Class.forName(AGUI_SHORTCUT, false, cl);
        } catch (ClassNotFoundException e) {
            module.log(Log.WARN, ModuleMain.TAG,
                    "AguiKeyboardShortcut missing; global letter shortcuts unavailable");
            return;
        }

        Method isHomePackage;
        try {
            isHomePackage = cls.getDeclaredMethod("isHomePackage", KeyEvent.class);
        } catch (NoSuchMethodException e) {
            module.log(Log.WARN, ModuleMain.TAG,
                    "AguiKeyboardShortcut.isHomePackage missing");
            return;
        }
        isHomePackage.setAccessible(true);

        module.hook(isHomePackage).intercept(chain -> {
            Object result = chain.proceed();
            if (Boolean.TRUE.equals(result)) {
                return result;
            }
            Object arg = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
            if (!(arg instanceof KeyEvent)) {
                return result;
            }
            KeyEvent event = (KeyEvent) arg;
            int code = event.getKeyCode();
            if (code < KeyEvent.KEYCODE_A || code > KeyEvent.KEYCODE_Z) {
                return result;
            }
            if (!RemoteConfig.globalLetterShortcuts()) {
                return result;
            }
            // Leave Ctrl/Alt/Meta combos (Ctrl+C, Alt+Tab-style editing) to the app.
            // Sym is not blocked: the OEM gate never reads the meta state, and on this
            // keyboard Sym+letter still produces the plain letter, so the IME check is
            // enough to keep it from interfering with typing.
            int blockedModifiers = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON
                    | KeyEvent.META_META_ON;
            if ((event.getMetaState() & blockedModifiers) != 0) {
                return result;
            }
            if (!SystemServer.isScreenOn() || SystemServer.isImeVisible()) {
                return result;
            }
            return true;
        });

        module.log(Log.INFO, ModuleMain.TAG,
                "global letter shortcut hook installed in system_server");
    }

    private ShortcutHooks() {
    }
}
