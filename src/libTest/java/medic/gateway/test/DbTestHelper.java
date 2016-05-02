package medic.gateway.test;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;

import java.util.*;
import java.util.regex.*;
import java.lang.reflect.*;

import medic.gateway.*;

import static java.util.UUID.randomUUID;
import static org.junit.Assert.*;
import static medic.gateway.test.TestUtils.*;

@SuppressWarnings({"PMD.JUnit4TestShouldUseAfterAnnotation",
		"PMD.ModifiedCyclomaticComplexity",
		"PMD.SignatureDeclareThrowsException",
		"PMD.StdCyclomaticComplexity",
		"PMD.UseVarargs"})
public class DbTestHelper {
	public static final String[] NO_ARGS = {};
	public static final String ALL_ROWS = null;

	public final Db db;
	public final SQLiteDatabase raw;

	public DbTestHelper(Context ctx) throws Exception {
		Constructor<?> constructor = Db.class.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		db = (Db) constructor.newInstance(ctx);
		raw = db.getWritableDatabase();
	}

	public void tearDown() {
		raw.delete("log", ALL_ROWS, NO_ARGS);
		raw.delete("wt_message", ALL_ROWS, NO_ARGS);
		raw.delete("wo_message", ALL_ROWS, NO_ARGS);
		db.close();
	}

	public long count(String tableName) {
		return raw.compileStatement("SELECT COUNT(*) FROM " + tableName).simpleQueryForLong();
	}

	public Cursor selectById(String tableName, String[] cols, String id) {
		Cursor c = raw.query(tableName, cols, "_id=?", args(id), null, null, null);
		assertEquals(1, c.getCount());
		c.moveToFirst();
		return c;
	}

	public void insert(String tableName, String[] cols, Object[] vals) {
		ContentValues v = new ContentValues();
		long initialCount = count(tableName);
		for(int i=cols.length-1; i>=0; --i) {
			if(vals[i] instanceof String) v.put(cols[i], (String) vals[i]);
			else if(vals[i] instanceof Byte) v.put(cols[i], (Byte) vals[i]);
			else if(vals[i] instanceof Short) v.put(cols[i], (Short) vals[i]);
			else if(vals[i] instanceof Integer) v.put(cols[i], (Integer) vals[i]);
			else if(vals[i] instanceof Long) v.put(cols[i], (Long) vals[i]);
			else if(vals[i] instanceof Float) v.put(cols[i], (Float) vals[i]);
			else if(vals[i] instanceof Double) v.put(cols[i], (Double) vals[i]);
			else if(vals[i] instanceof Boolean) v.put(cols[i], (Boolean) vals[i]);
			else if(vals[i] instanceof byte[]) v.put(cols[i], (byte[]) vals[i]);
			else v.put(cols[i], vals[i].toString());
		}
		long rowId = raw.insertOrThrow(tableName, null, v);
		assertEquals(initialCount+1, count(tableName));
		assertNotEquals(-1, rowId);
	}

	public void assertTable(String tableName, Object... expectedValues) {
		Cursor c = getContents(tableName);
		try {
			int colCount = c.getColumnCount();
			int expectedRowCount = expectedValues.length / colCount;
			assertEquals("Wrong number of rows in db.", expectedRowCount, c.getCount());
			for(int i=0; i<expectedRowCount; ++i) {
				c.moveToNext();

				String expectedRow = Arrays.toString(Arrays.copyOfRange(expectedValues, i * colCount, i * colCount + colCount));
				for(int j=0; j<colCount; ++j) {
					Object expected = expectedValues[i * colCount + j];
					String actual = c.getString(j);

					String failMessage = String.format("Expected row: %s.  Unexpected value at (%s, %s):",
							expectedRow, i, j);
					if(expected instanceof Pattern) {
						assertMatches(failMessage, expected, actual);
					} else if(expected instanceof Boolean) {
						String expectedString = ((Boolean) expected) ? "1" : "0";
						assertEquals(failMessage, expectedString, actual);
					} else {
						assertEquals(failMessage, expected.toString(), actual);
					}
				}
			}
		} finally {
			c.close();
		}
	}

	public void assertEmpty(String tableName) {
		assertEquals(0, count(tableName));
	}

	private Cursor getContents(String tableName) {
		return raw.rawQuery("SELECT * FROM " + tableName, NO_ARGS);
	}

//> STATIC HELPERS
	public static String[] args(String... args) {
		return args;
	}

	public static String[] cols(String... columnNames) {
		return columnNames;
	}

	public static Object[] vals(Object... vals) {
		return vals;
	}

	public static String randomUuid() {
		return randomUUID().toString();
	}
}