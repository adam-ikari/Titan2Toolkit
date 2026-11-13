package com.titan2.toolkit;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * Keeps workspace items that only exist because of the 6-column override.
 *
 * <p>The stock loader permanently deletes rows whose cell lies outside the grid it is loading.
 * Two mechanisms protect them: rows outside the grid are not deleted while the guard is on,
 * and a copy of the favorites table is kept outside the launcher database so rows deleted
 * during a module-inactive episode can be put back on reactivation.
 */
final class LayoutGuard {

    private static final String BACKUP_FILE = "titan2_layout_backup.db";
    private static final long SNAPSHOT_DELAY_MS = 1500;
    private static final int CONTAINER_DESKTOP = -100;
    private static final int CONTAINER_HOTSEAT = -101;

    private static final int ITEM_TYPE_APPWIDGET = 4;
    // LauncherAppWidgetInfo restore flags. A restored widget row must not keep the appWidgetId
    // from the snapshot: that host binding died with the row, and inflating the stale id shows
    // "Problem loading widget". Launcher3 only allocates and binds a fresh id for rows carrying
    // these flags (WidgetInflater.inflateAppWidget); otherwise it falls back to a pending view.
    private static final int WIDGET_FLAG_ID_NOT_VALID = 1;
    private static final int WIDGET_FLAG_PROVIDER_NOT_READY = 2;
    private static final int WIDGET_FLAG_UI_NOT_READY = 4;

    private static final ScheduledExecutorService WORKER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "titan2-layout-guard");
                t.setDaemon(true);
                return t;
            });

    private static final AtomicBoolean sSnapshotQueued = new AtomicBoolean();
    private static final AtomicBoolean sRestoreDone = new AtomicBoolean();
    private static final Object DB_LOCK = new Object();

    private static volatile XposedModule sModule;
    private static volatile Context sContext;
    private static volatile boolean sProcessWarningLogged;

    private static Field fNumColumns;
    private static Field fNumRows;
    private static Field fNumHotseat;
    private static Field fDbFile;

    static void install(XposedModule module, ClassLoader cl, Class<?> idpClass) {
        sModule = module;
        cacheIdpFields(idpClass);
        hookMarkDeleted(cl);
        hookDbWrites(cl);
    }

    /**
     * Called from the grid anchor before the column counts are forced to 6. Restoring here
     * happens before LoaderTask reads the workspace, so restored rows load in the same pass.
     */
    static void beforeGridOverride(Object idp, Object contextArg, String pkg) {
        Context context = contextArg instanceof Context
                ? ((Context) contextArg).getApplicationContext() : sContext;
        if (context == null || idp == null) {
            return;
        }
        sContext = context;
        if (!isMainProcess(pkg)) {
            return;
        }
        if (!RemoteConfig.workspace6Columns(context)) {
            return;
        }
        if (!sRestoreDone.compareAndSet(false, true)) {
            return;
        }
        try {
            restore(idp, context);
        } catch (Throwable t) {
            log(Log.ERROR, "layout restore failed", t);
        }
    }

    private static void cacheIdpFields(Class<?> idpClass) {
        fNumColumns = findField(idpClass, "numColumns");
        fNumRows = findField(idpClass, "numRows");
        fNumHotseat = findField(idpClass, "numDatabaseHotseatIcons");
        fDbFile = findField(idpClass, "dbFile");
        if (fNumColumns == null || fNumRows == null || fNumHotseat == null || fDbFile == null) {
            log(Log.WARN, "launcher grid fields missing; layout protection limited");
        }
    }

    private static void hookMarkDeleted(ClassLoader cl) {
        Class<?> cursorClass = findClass(cl, "com.android.launcher3.model.LoaderCursor");
        if (cursorClass == null) {
            log(Log.WARN, "LoaderCursor missing; out-of-bounds items stay deletable");
            return;
        }
        Method markDeleted = findMethod(cursorClass, "markDeleted", String.class, String.class);
        Field idpField = findField(cursorClass, "mIDP");
        Field contextField = findField(cursorClass, "mContext");
        if (markDeleted == null || idpField == null) {
            log(Log.WARN, "LoaderCursor.markDeleted missing; out-of-bounds items stay deletable");
            return;
        }
        idpField.setAccessible(true);
        if (contextField != null) {
            contextField.setAccessible(true);
        }
        final Field ctxField = contextField;

        sModule.hook(markDeleted).intercept(chain -> {
            try {
                java.util.List<Object> args = chain.getArgs();
                Object reason = args.size() > 1 ? args.get(1) : null;
                if ("overlapping_item".equals(reason)
                        && isOutOfGrid(chain.getThisObject(), idpField, ctxField)) {
                    return null;
                }
            } catch (Throwable t) {
                log(Log.WARN, "placement check failed; using stock deletion", t);
            }
            return chain.proceed();
        });
        log(Log.INFO, "layout guard hooked LoaderCursor.markDeleted");
    }

    private static void hookDbWrites(ClassLoader cl) {
        Class<?> dbClass = findClass(cl, "com.android.launcher3.model.ModelDbController");
        if (dbClass == null) {
            log(Log.WARN, "ModelDbController missing; layout snapshots disabled");
            return;
        }
        Field contextField = findField(dbClass, "mContext");
        Field idpField = findField(dbClass, "mIdp");
        if (contextField != null) {
            contextField.setAccessible(true);
        }
        if (idpField != null) {
            idpField.setAccessible(true);
        }

        Method insert = findMethod(dbClass, "insert", ContentValues.class);
        Method update = findMethod(dbClass, "update", ContentValues.class, String.class, String[].class);
        Method delete = findMethod(dbClass, "delete", String.class, String[].class);
        hookWrite(insert, contextField, idpField);
        hookWrite(update, contextField, idpField);
        hookWrite(delete, contextField, idpField);
        log(Log.INFO, "layout guard hooked launcher database writes");
    }

    private static void hookWrite(Method method, Field contextField, Field idpField) {
        if (method == null) {
            return;
        }
        sModule.hook(method).intercept(chain -> {
            Object result = chain.proceed();
            int changed = result instanceof Number ? ((Number) result).intValue() : 1;
            if (changed > 0) {
                try {
                    scheduleSnapshot(chain.getThisObject(), contextField, idpField);
                } catch (Throwable t) {
                    log(Log.WARN, "unable to schedule layout snapshot", t);
                }
            }
            return result;
        });
    }

    private static void scheduleSnapshot(Object controller, Field contextField, Field idpField)
            throws Exception {
        Context context = sContext;
        if (context == null && contextField != null) {
            Object value = contextField.get(controller);
            if (value instanceof Context) {
                context = ((Context) value).getApplicationContext();
                sContext = context;
            }
        }
        if (context == null || !RemoteConfig.workspace6Columns(context)) {
            return;
        }
        String dbFile = null;
        int columns = -1;
        int rows = -1;
        int hotseat = -1;
        if (idpField != null) {
            Object idp = idpField.get(controller);
            if (idp != null) {
                if (fDbFile != null) {
                    Object value = fDbFile.get(idp);
                    if (value instanceof String) {
                        dbFile = (String) value;
                    }
                }
                columns = fieldValue(fNumColumns, idp);
                rows = fieldValue(fNumRows, idp);
                hotseat = fieldValue(fNumHotseat, idp);
            }
        }
        final Context ctx = context;
        final String file = dbFile;
        final int currentColumns = columns;
        final int currentRows = rows;
        final int currentHotseat = hotseat;
        if (!sSnapshotQueued.compareAndSet(false, true)) {
            return;
        }
        WORKER.schedule(() -> {
            sSnapshotQueued.set(false);
            try {
                snapshot(ctx, file, currentColumns, currentRows, currentHotseat);
            } catch (Throwable t) {
                log(Log.WARN, "layout snapshot failed", t);
            }
        }, SNAPSHOT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static void snapshot(Context context, String dbFile,
                                 int currentColumns, int currentRows, int currentHotseat) {
        File liveFile = resolveDbFile(context, dbFile);
        if (liveFile == null || !liveFile.isFile()) {
            return;
        }
        File backupFile = new File(context.getNoBackupFilesDir(), BACKUP_FILE);
        synchronized (DB_LOCK) {
            SQLiteDatabase backup = null;
            try {
                backup = SQLiteDatabase.openOrCreateDatabase(backupFile, null);
                String path = liveFile.getAbsolutePath().replace("'", "''");
                backup.execSQL("ATTACH DATABASE '" + path + "' AS src");
                backup.beginTransaction();
                try {
                    backup.execSQL("DROP TABLE IF EXISTS favorites_new");
                    backup.execSQL("CREATE TABLE favorites_new AS SELECT * FROM src.favorites");
                    if (currentColumns > 0 && currentColumns < ModuleMain.COLUMNS
                            && currentRows > 0 && currentHotseat > 0
                            && tableExists(backup, "favorites")) {
                        // Running the stock grid: rows in the danger zone that are no longer live
                        // were removed while the module was inactive, keep them restorable.
                        try {
                            backup.execSQL("INSERT INTO favorites_new "
                                    + "SELECT * FROM favorites WHERE _id NOT IN "
                                    + "(SELECT _id FROM favorites_new) AND ("
                                    + "(container = " + CONTAINER_DESKTOP
                                    + " AND (cellX < 0 OR cellY < 0 "
                                    + "OR cellX + COALESCE(spanX, 1) > " + currentColumns
                                    + " OR cellY + COALESCE(spanY, 1) > " + currentRows + ")) "
                                    + "OR (container = " + CONTAINER_HOTSEAT
                                    + " AND screen >= " + currentHotseat + "))");
                        } catch (Throwable t) {
                            log(Log.WARN, "unable to merge old layout backup", t);
                        }
                    }
                    backup.execSQL("DROP TABLE IF EXISTS favorites");
                    backup.execSQL("ALTER TABLE favorites_new RENAME TO favorites");
                    backup.setTransactionSuccessful();
                } finally {
                    backup.endTransaction();
                }
                backup.execSQL("DETACH DATABASE src");
            } catch (Throwable t) {
                log(Log.WARN, "unable to copy launcher favorites", t);
            } finally {
                if (backup != null) {
                    backup.close();
                }
            }
        }
    }

    private static void restore(Object idp, Context context) throws Exception {
        if (fNumColumns == null || fNumRows == null || fNumHotseat == null || fDbFile == null) {
            return;
        }
        String dbFile = (String) fDbFile.get(idp);
        File liveFile = resolveDbFile(context, dbFile);
        File backupFile = new File(context.getNoBackupFilesDir(), BACKUP_FILE);
        if (liveFile == null || !liveFile.isFile() || !backupFile.isFile()) {
            return;
        }
        int nativeColumns = fNumColumns.getInt(idp);
        int nativeRows = fNumRows.getInt(idp);
        int nativeHotseat = fNumHotseat.getInt(idp);
        if (nativeColumns >= ModuleMain.COLUMNS) {
            return;
        }

        int restored;
        synchronized (DB_LOCK) {
            SQLiteDatabase backup = null;
            SQLiteDatabase live = null;
            try {
                backup = SQLiteDatabase.openDatabase(backupFile.getAbsolutePath(), null,
                        SQLiteDatabase.OPEN_READONLY);
                live = SQLiteDatabase.openDatabase(liveFile.getAbsolutePath(), null,
                        SQLiteDatabase.OPEN_READWRITE);
                if (!tableExists(backup, "favorites") || !tableExists(live, "favorites")) {
                    return;
                }
                restored = restoreMissingRows(live, backup, nativeColumns, nativeRows,
                        nativeHotseat);
            } finally {
                if (live != null) {
                    live.close();
                }
                if (backup != null) {
                    backup.close();
                }
            }
        }
        if (restored > 0) {
            log(Log.INFO, "restored " + restored + " launcher item(s) from layout backup");
            final Context ctx = context;
            final String file = dbFile;
            final int columns = nativeColumns;
            final int rows = nativeRows;
            final int hotseat = nativeHotseat;
            WORKER.execute(() -> snapshot(ctx, file, columns, rows, hotseat));
        }
    }

    static int restoreMissingRows(SQLiteDatabase live, SQLiteDatabase backup,
                                  int nativeColumns, int nativeRows, int nativeHotseat) {
        final int targetColumns = ModuleMain.COLUMNS;
        final int targetRows = nativeRows;
        final int targetHotseat = ModuleMain.COLUMNS;

        Set<Integer> liveIds = new HashSet<>();
        Map<Integer, boolean[][]> occupied = new HashMap<>();
        boolean[] hotseat = new boolean[targetHotseat];
        int maxScreen = -1;

        try (Cursor cursor = live.query("favorites", null, null, null, null, null, null)) {
            int idIndex = cursor.getColumnIndexOrThrow("_id");
            int containerIndex = cursor.getColumnIndexOrThrow("container");
            int screenIndex = cursor.getColumnIndexOrThrow("screen");
            int cellXIndex = cursor.getColumnIndexOrThrow("cellX");
            int cellYIndex = cursor.getColumnIndexOrThrow("cellY");
            int spanXIndex = cursor.getColumnIndexOrThrow("spanX");
            int spanYIndex = cursor.getColumnIndexOrThrow("spanY");
            while (cursor.moveToNext()) {
                int id = cursor.getInt(idIndex);
                int container = cursor.getInt(containerIndex);
                int screen = cursor.getInt(screenIndex);
                int cellX = cursor.getInt(cellXIndex);
                int cellY = cursor.getInt(cellYIndex);
                int spanX = Math.max(1, cursor.getInt(spanXIndex));
                int spanY = Math.max(1, cursor.getInt(spanYIndex));
                liveIds.add(id);
                if (container != CONTAINER_DESKTOP && container != CONTAINER_HOTSEAT) {
                    continue;
                }
                if (screen >= 0) {
                    maxScreen = Math.max(maxScreen, screen);
                }
                if (container == CONTAINER_HOTSEAT) {
                    if (screen >= 0 && screen < targetHotseat) {
                        hotseat[screen] = true;
                    }
                } else {
                    markOccupied(occupied, screen, cellX, cellY, spanX, spanY,
                            targetColumns, targetRows);
                }
            }
        }

        int recoveryScreen = Math.max(maxScreen + 1, 0);
        int restored = 0;
        try (Cursor cursor = backup.query("favorites", null, null, null, null, null, null)) {
            String[] columns = cursor.getColumnNames();
            int idIndex = cursor.getColumnIndexOrThrow("_id");
            int containerIndex = cursor.getColumnIndexOrThrow("container");
            int screenIndex = cursor.getColumnIndexOrThrow("screen");
            int cellXIndex = cursor.getColumnIndexOrThrow("cellX");
            int cellYIndex = cursor.getColumnIndexOrThrow("cellY");
            int spanXIndex = cursor.getColumnIndexOrThrow("spanX");
            int spanYIndex = cursor.getColumnIndexOrThrow("spanY");
            while (cursor.moveToNext()) {
                int id = cursor.getInt(idIndex);
                if (liveIds.contains(id)) {
                    continue;
                }
                int container = cursor.getInt(containerIndex);
                int screen = cursor.getInt(screenIndex);
                int cellX = cursor.getInt(cellXIndex);
                int cellY = cursor.getInt(cellYIndex);
                int spanX = Math.max(1, cursor.getInt(spanXIndex));
                int spanY = Math.max(1, cursor.getInt(spanYIndex));
                if (!outsideNativeGrid(container, screen, cellX, cellY, spanX, spanY,
                        nativeColumns, nativeRows, nativeHotseat)) {
                    continue;
                }

                ContentValues values = readRow(cursor, columns);
                if (!repairRestoredWidget(values, id)) {
                    continue;
                }
                if (container == CONTAINER_HOTSEAT) {
                    int slot = screen >= 0 && screen < targetHotseat && !hotseat[screen]
                            ? screen : firstFreeSlot(hotseat);
                    if (slot >= 0) {
                        values.put("container", CONTAINER_HOTSEAT);
                        values.put("screen", slot);
                        hotseat[slot] = true;
                    } else {
                        recoveryScreen = placeOnWorkspace(values, occupied, targetColumns,
                                targetRows, recoveryScreen, spanX, spanY);
                    }
                } else if (regionFree(occupied, screen, cellX, cellY, spanX, spanY,
                        targetColumns, targetRows)) {
                    values.put("container", CONTAINER_DESKTOP);
                    values.put("screen", screen);
                    values.put("cellX", cellX);
                    values.put("cellY", cellY);
                    markOccupied(occupied, screen, cellX, cellY, spanX, spanY,
                            targetColumns, targetRows);
                } else {
                    recoveryScreen = placeOnWorkspace(values, occupied, targetColumns,
                            targetRows, recoveryScreen, spanX, spanY);
                }

                try {
                    live.insertOrThrow("favorites", null, values);
                    liveIds.add(id);
                    restored++;
                } catch (Throwable t) {
                    log(Log.WARN, "unable to restore launcher item " + id, t);
                }
            }
        }
        return restored;
    }

    /**
     * Makes a restored widget row safe to insert. Returns false when the row has to be skipped.
     */
    static boolean repairRestoredWidget(ContentValues values, int id) {
        Integer itemType = values.getAsInteger("itemType");
        if (itemType == null || itemType != ITEM_TYPE_APPWIDGET) {
            return true;
        }
        String provider = values.getAsString("appWidgetProvider");
        if (provider == null || ComponentName.unflattenFromString(provider) == null) {
            // WorkspaceItemProcessor unflattenFromString(...)!! on this column, so a widget row
            // without a usable provider would take the loader down.
            log(Log.WARN, "skipping widget row " + id + ": invalid appWidgetProvider=" + provider);
            return false;
        }
        values.put("appWidgetId", -1);
        values.put("restored", WIDGET_FLAG_ID_NOT_VALID | WIDGET_FLAG_PROVIDER_NOT_READY
                | WIDGET_FLAG_UI_NOT_READY);
        Integer spanX = values.getAsInteger("spanX");
        Integer spanY = values.getAsInteger("spanY");
        if (spanX == null || spanX <= 0) {
            values.put("spanX", 1);
        }
        if (spanY == null || spanY <= 0) {
            values.put("spanY", 1);
        }
        return true;
    }

    private static int placeOnWorkspace(ContentValues values, Map<Integer, boolean[][]> occupied,
                                        int columns, int rows, int startScreen,
                                        int spanX, int spanY) {
        int spanW = Math.min(Math.max(1, spanX), columns);
        int spanH = Math.min(Math.max(1, spanY), rows);
        if (spanW != spanX) {
            values.put("spanX", spanW);
        }
        if (spanH != spanY) {
            values.put("spanY", spanH);
        }
        int screen = Math.max(startScreen, 0);
        while (true) {
            int[] spot = findFreeSpot(occupied, screen, columns, rows, spanW, spanH);
            if (spot != null) {
                values.put("container", CONTAINER_DESKTOP);
                values.put("screen", screen);
                values.put("cellX", spot[0]);
                values.put("cellY", spot[1]);
                markOccupied(occupied, screen, spot[0], spot[1], spanW, spanH,
                        columns, rows);
                return screen;
            }
            screen++;
        }
    }

    private static int[] findFreeSpot(Map<Integer, boolean[][]> occupied, int screen,
                                      int columns, int rows, int spanX, int spanY) {
        for (int y = 0; y + spanY <= rows; y++) {
            for (int x = 0; x + spanX <= columns; x++) {
                if (regionFree(occupied, screen, x, y, spanX, spanY, columns, rows)) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static boolean regionFree(Map<Integer, boolean[][]> occupied, int screen,
                                      int cellX, int cellY, int spanX, int spanY,
                                      int columns, int rows) {
        if (cellX < 0 || cellY < 0 || cellX + spanX > columns || cellY + spanY > rows) {
            return false;
        }
        boolean[][] grid = occupied.get(screen);
        if (grid == null) {
            return true;
        }
        for (int y = cellY; y < cellY + spanY; y++) {
            for (int x = cellX; x < cellX + spanX; x++) {
                if (grid[x][y]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void markOccupied(Map<Integer, boolean[][]> occupied, int screen,
                                     int cellX, int cellY, int spanX, int spanY,
                                     int columns, int rows) {
        boolean[][] grid = occupied.computeIfAbsent(screen,
                key -> new boolean[columns][rows]);
        for (int y = Math.max(0, cellY);
             y < Math.min(rows, cellY + spanY); y++) {
            for (int x = Math.max(0, cellX);
                 x < Math.min(columns, cellX + spanX); x++) {
                grid[x][y] = true;
            }
        }
    }

    private static int fieldValue(Field field, Object target) {
        if (field == null) {
            return -1;
        }
        try {
            return field.getInt(target);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int firstFreeSlot(boolean[] taken) {
        for (int i = 0; i < taken.length; i++) {
            if (!taken[i]) {
                return i;
            }
        }
        return -1;
    }

    private static boolean outsideNativeGrid(int container, int screen, int cellX, int cellY,
                                             int spanX, int spanY, int nativeColumns,
                                             int nativeRows, int nativeHotseat) {
        if (container == CONTAINER_HOTSEAT) {
            return screen < 0 || screen >= nativeHotseat;
        }
        if (container != CONTAINER_DESKTOP) {
            return false;
        }
        return cellX < 0 || cellY < 0
                || cellX + spanX > nativeColumns || cellY + spanY > nativeRows;
    }

    private static ContentValues readRow(Cursor cursor, String[] columns) {
        ContentValues values = new ContentValues(columns.length);
        for (int i = 0; i < columns.length; i++) {
            switch (cursor.getType(i)) {
                case Cursor.FIELD_TYPE_INTEGER:
                    values.put(columns[i], cursor.getLong(i));
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    values.put(columns[i], cursor.getDouble(i));
                    break;
                case Cursor.FIELD_TYPE_STRING:
                    values.put(columns[i], cursor.getString(i));
                    break;
                case Cursor.FIELD_TYPE_BLOB:
                    values.put(columns[i], cursor.getBlob(i));
                    break;
                default:
                    break;
            }
        }
        return values;
    }

    private static boolean isOutOfGrid(Object cursorObject, Field idpField, Field contextField)
            throws Exception {
        if (!(cursorObject instanceof Cursor)) {
            return false;
        }
        Cursor cursor = (Cursor) cursorObject;
        Context context = sContext;
        if (context == null && contextField != null) {
            Object value = contextField.get(cursorObject);
            if (value instanceof Context) {
                context = (Context) value;
            }
        }
        if (context == null || !RemoteConfig.workspace6Columns(context)) {
            return false;
        }
        Object idp = idpField.get(cursorObject);
        if (idp == null || fNumColumns == null || fNumRows == null || fNumHotseat == null) {
            return false;
        }

        int container = cursor.getInt(cursor.getColumnIndexOrThrow("container"));
        int screen = cursor.getInt(cursor.getColumnIndexOrThrow("screen"));
        int columns = fNumColumns.getInt(idp);
        int rows = fNumRows.getInt(idp);
        int hotseatIcons = fNumHotseat.getInt(idp);

        if (container == CONTAINER_HOTSEAT) {
            if (screen >= hotseatIcons) {
                log(Log.INFO, "kept out-of-grid hotseat item at slot " + screen);
                return true;
            }
            return false;
        }
        if (container != CONTAINER_DESKTOP) {
            return false;
        }
        int cellX = cursor.getInt(cursor.getColumnIndexOrThrow("cellX"));
        int cellY = cursor.getInt(cursor.getColumnIndexOrThrow("cellY"));
        int spanX = Math.max(1, cursor.getInt(cursor.getColumnIndexOrThrow("spanX")));
        int spanY = Math.max(1, cursor.getInt(cursor.getColumnIndexOrThrow("spanY")));
        boolean outside = cellX < 0 || cellY < 0
                || cellX + spanX > columns || cellY + spanY > rows;
        if (outside) {
            log(Log.INFO, "kept out-of-grid item at " + cellX + "," + cellY
                    + " span " + spanX + "x" + spanY + " grid " + columns + "x" + rows);
        }
        return outside;
    }

    private static boolean isMainProcess(String pkg) {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object name = activityThread.getMethod("currentProcessName").invoke(null);
            boolean main = name == null || pkg.equals(name);
            if (!main && !sProcessWarningLogged) {
                sProcessWarningLogged = true;
                log(Log.WARN, "layout restore skipped in process " + name);
            }
            return main;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean tableExists(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{table})) {
            return cursor.moveToFirst();
        }
    }

    private static File resolveDbFile(Context context, String dbFile) {
        if (dbFile == null || dbFile.isEmpty()) {
            return null;
        }
        File file = new File(dbFile);
        return file.isAbsolute() ? file : context.getDatabasePath(dbFile);
    }

    private static Class<?> findClass(ClassLoader cl, String name) {
        try {
            return Class.forName(name, false, cl);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                // keep walking
            }
        }
        return null;
    }

    private static void log(int priority, String message) {
        XposedModule module = sModule;
        if (module != null) {
            module.log(priority, ModuleMain.TAG, message);
        }
    }

    private static void log(int priority, String message, Throwable t) {
        XposedModule module = sModule;
        if (module != null) {
            module.log(priority, ModuleMain.TAG, message, t);
        }
    }

    private LayoutGuard() {
    }
}
