package net.jaqpot.netcounter.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.TrafficStats;
import android.net.Uri;
import android.provider.CallLog;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.util.Log;

/**
 * @author Louis
 *
 */
public class PlayModeModelAPI {
	
	private static DatabaseAPI sDbAPI = null;
	
	private static Location DEFAULT_LOC = new Location("");
	
	private static int sSignalStrength = 0;
	
	private static int sSentBytes = 0;
	
//	p static int getSignalStrength (Context ctx) {
//		//FIXME
//		return 99;
//	}
	
	private static int getBytes(Context ctx) {
		return (int)(TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes());
	}
	
	private static int getCallDuration(Context ctx) {
		Uri allCalls = Uri.parse("content://call_log/calls");
		int duration = 0;
		Cursor c = ctx.getContentResolver().query(allCalls, null, null, null, null);

		if (c.moveToFirst()) {
			do {
				int d = Integer.valueOf(c.getString(c.getColumnIndex(CallLog.Calls.DURATION)));

				duration += d;
			} while (c.moveToNext());
		}
		
		c.close();

		return duration;
	}

	private static int getSmsCount(Context ctx) {
		Uri sms_content = Uri.parse("content://sms");
		Cursor c = ctx.getContentResolver().query(sms_content, null,null, null, null);
		c.moveToFirst();
		
		int count = c.getCount();
		
		c.close();
		
		return count;
	}
	
	private static float getDistance(Context ctx) {
		TelephonyManager telephonyManager = (TelephonyManager)ctx.getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(new PhoneStateListener(), PhoneStateListener.LISTEN_SIGNAL_STRENGTH);
		CellLocation cellLocation = telephonyManager.getCellLocation();
		float temp = -1;
		if (cellLocation instanceof CdmaCellLocation ) {
			float lat = ((CdmaCellLocation) cellLocation).getBaseStationLatitude();
			float lon = ((CdmaCellLocation) cellLocation).getBaseStationLongitude();
			
			Location currentLoc = new Location("");
			currentLoc.setLatitude(lat);
			currentLoc.setLongitude(lon);
			
			temp = DEFAULT_LOC.distanceTo(currentLoc);
		}
		
		return temp;
	}
	
//	public static void resetModel(Context ctx, Location defaultLocation) {
//		NewDataModel.initialize((int)(TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()),
//				PlayModeModelAPI.getCallDuration(ctx),
//				PlayModeModelAPI.getSmsCount(ctx),
//				99,
//				PlayModeModelAPI.getDistance(ctx));	
//	}
	
	private static void insertModel(Context ctx, NewDataModel model) {
		ContentValues values = new ContentValues();
		values.put(DatabaseAPI.MyCounter.BYTES, model.getBytes());
		values.put(DatabaseAPI.MyCounter.CALL_DURATION, model.getConvDuration());
		values.put(DatabaseAPI.MyCounter.CELL_DISTANCE, model.getCellDistance());
		values.put(DatabaseAPI.MyCounter.SMS_SENT, model.getSMS());
		values.put(DatabaseAPI.MyCounter.SIGNAL_STRENGTH, model.getSignalStrength());
		
		Log.d("DEBUG", "Inserting model in DB where BYTES = " + model.getBytes());
		
		SQLiteDatabase db = getDbAPI(ctx).getWritableDatabase();
		db.insert(DatabaseAPI.MyCounter.TABLE_NAME, null, values);
		db.close();
	}
	
	private static NewDataModel selectLastModel(Context ctx) {
		SQLiteDatabase db = getDbAPI(ctx).getReadableDatabase();
		Cursor c = db.rawQuery("SELECT * FROM " + DatabaseAPI.MyCounter.TABLE_NAME +
				" ORDER BY " + DatabaseAPI.MyCounter.VALUE_TS + " DESC", null);
		NewDataModel model = null;
		
		if (c.moveToFirst()) {
			model = new NewDataModel(
					c.getInt(c.getColumnIndex(DatabaseAPI.MyCounter.BYTES)),
					c.getInt(c.getColumnIndex(DatabaseAPI.MyCounter.CALL_DURATION)),
					c.getInt(c.getColumnIndex(DatabaseAPI.MyCounter.SMS_SENT)),
					c.getInt(c.getColumnIndex(DatabaseAPI.MyCounter.SIGNAL_STRENGTH)),
					(float) c.getDouble(c.getColumnIndex(DatabaseAPI.MyCounter.CELL_DISTANCE)));
			Log.d("Debug", "Selected value among " + c.getCount() + " in DB where BYTES = " + model.getBytes()
					+ " TS = " + c.getString(c.getColumnIndex(DatabaseAPI.MyCounter.VALUE_TS)));
		}
		
		c.close();
		
		return model;
	}
	
	public static void clearDB(Context ctx) {
		getDbAPI(ctx).onUpgrade(sDbAPI.getWritableDatabase(), 1, 2);
		sSignalStrength = 0;
		sSentBytes = 0;
	}
	
	public static NewDataModel getDataToSend(Context ctx) { 
		//Most up to date data
				NewDataModel newData = new NewDataModel(
						PlayModeModelAPI.getBytes(ctx) - sSentBytes,
						PlayModeModelAPI.getCallDuration(ctx),
						PlayModeModelAPI.getSmsCount(ctx),
						sSignalStrength,
						PlayModeModelAPI.getDistance(ctx));
				
				//Most recent entry in database
				NewDataModel prevData = PlayModeModelAPI.selectLastModel(ctx);
				
				NewDataModel modelToSend = (prevData != null) ? newData.subtract(prevData) : newData;
//				
//				mCurrentModel = newData;
				
				PlayModeModelAPI.insertModel(ctx, newData);
				
				return modelToSend;
	}
	
	public static void setSignalStrength(int strength) {
		sSignalStrength = strength;
	}
	
	public static void setSentBytes(int sent) {
		sSentBytes += sent;
	}
	
	private static DatabaseAPI getDbAPI(Context ctx) {
		if (sDbAPI == null) {
			sDbAPI = new DatabaseAPI(ctx);
			DEFAULT_LOC.setLatitude(0.0);
			DEFAULT_LOC.setLongitude(0.0);
		}
		
		return sDbAPI;
	}
	
	public static SQLiteDatabase getReadableAPI(Context ctx) {
		return getDbAPI(ctx).getReadableDatabase();
	}
	
//	public static String[] modelToStringArray(NewDataModel model) {
//		return null;
//	}
}
