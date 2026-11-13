package com.titan2.toolkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

/** Exercises the snapshot/restore row handling against two real SQLite files. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LayoutGuardTest {

    private static final int FLAG_ID_NOT_VALID = 1;
    private static final int FLAG_PROVIDER_NOT_READY = 2;
    private static final int FLAG_UI_NOT_READY = 4;

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void restoredWidgetDoesNotKeepStaleAppWidgetId() throws Exception {
        SQLiteDatabase live = open("launcher.db");
        SQLiteDatabase backup = open("titan2_layout_backup.db");
        createFavorites(live);
        createFavorites(backup);

        insertApp(live, 1, 0, 0);
        insertApp(backup, 1, 0, 0);
        // Column 5 only exists in the 6-column grid; the stock 4-column loader deleted it.
        insertWidget(backup, 2, 5, 0, 1234, "com.example.widget/.Provider");

        assertEquals(1, LayoutGuard.restoreMissingRows(live, backup, 4, 5, 4));

        try (Cursor c = live.query("favorites", null, "_id=2", null, null, null, null)) {
            assertTrue(c.moveToFirst());
            assertEquals(4, c.getInt(c.getColumnIndexOrThrow("itemType")));
            assertEquals(-1, c.getInt(c.getColumnIndexOrThrow("appWidgetId")));
            assertEquals(FLAG_ID_NOT_VALID | FLAG_PROVIDER_NOT_READY | FLAG_UI_NOT_READY,
                    c.getInt(c.getColumnIndexOrThrow("restored")));
            assertEquals("com.example.widget/.Provider",
                    c.getString(c.getColumnIndexOrThrow("appWidgetProvider")));
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("spanX")));
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("spanY")));
        }
    }

    @Test
    public void widgetWithoutProviderIsNotRestored() throws Exception {
        SQLiteDatabase live = open("launcher.db");
        SQLiteDatabase backup = open("titan2_layout_backup.db");
        createFavorites(live);
        createFavorites(backup);

        insertWidget(backup, 3, 5, 0, 1234, "");

        assertEquals(0, LayoutGuard.restoreMissingRows(live, backup, 4, 5, 4));

        try (Cursor c = live.query("favorites", null, "_id=3", null, null, null, null)) {
            assertFalse(c.moveToFirst());
        }
    }

    private SQLiteDatabase open(String name) throws Exception {
        return SQLiteDatabase.openOrCreateDatabase(new File(tmp.getRoot(), name), null);
    }

    private static void createFavorites(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE favorites ("
                + "_id INTEGER PRIMARY KEY,"
                + "itemType INTEGER,"
                + "container INTEGER,"
                + "screen INTEGER,"
                + "cellX INTEGER,"
                + "cellY INTEGER,"
                + "spanX INTEGER,"
                + "spanY INTEGER,"
                + "appWidgetId INTEGER DEFAULT -1,"
                + "appWidgetProvider TEXT,"
                + "restored INTEGER DEFAULT 0,"
                + "intent TEXT,"
                + "title TEXT,"
                + "profileId INTEGER DEFAULT 0)");
    }

    private static void insertApp(SQLiteDatabase db, int id, int cellX, int cellY) {
        db.execSQL("INSERT INTO favorites"
                        + " (_id,itemType,container,screen,cellX,cellY,spanX,spanY)"
                        + " VALUES (" + id + ",0,-100,0," + cellX + "," + cellY + ",1,1)");
    }

    private static void insertWidget(SQLiteDatabase db, int id, int cellX, int cellY,
                                     int appWidgetId, String provider) {
        db.execSQL("INSERT INTO favorites"
                        + " (_id,itemType,container,screen,cellX,cellY,spanX,spanY,"
                        + "appWidgetId,appWidgetProvider,restored)"
                        + " VALUES (" + id + ",4,-100,0," + cellX + "," + cellY + ",1,1,"
                        + appWidgetId + ",'" + provider + "',0)");
    }
}
