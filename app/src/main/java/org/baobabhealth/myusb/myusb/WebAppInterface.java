package org.baobabhealth.myusb.myusb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class WebAppInterface {
	Context mContext;
    USBActivity mParent;
	private int mID;
	private int mCategory;
	private static final int KEY_PERSON = 1;
	private static final int KEY_USER = 2;

	SharedPreferences mPrefs;

	private Handler mHandler;
	private int mInterval = 30000;

	/** Instantiate the interface and set the context */
	WebAppInterface(Context c, USBActivity parent) {
		mContext = c;
		mParent = parent;

		mPrefs = c.getSharedPreferences("PrefFile", 0);

	}

	/** Show a toast from the web page */
	@JavascriptInterface
	public void showToast(String toast) {
		Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
	}

	@JavascriptInterface
	public void showMsg(String msg) {
		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setCancelable(true);
		builder.setTitle(msg);
		builder.setInverseBackgroundForced(true);
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});

		AlertDialog alert = builder.create();
		alert.show();
	}

	@JavascriptInterface
	public String getToken() {
		String token = mPrefs.getString("token", "");

		return token;
	}

	@JavascriptInterface
	public String getPref(String pref) {
		String item = mPrefs.getString(pref, "");

		return item;
	}

	@JavascriptInterface
	public void setPref(String pref, String value) {
		Editor editor = mPrefs.edit();

		editor.putString(pref, value);

		editor.commit();
	}

	@JavascriptInterface
	public String ajaxRead(String aUrl) {
		AssetManager am = mContext.getAssets();
		try {
			InputStream is = am.open(aUrl);
			String res = null;

			if (is != null) {
				// prepare the file for reading
				InputStreamReader input = new InputStreamReader(is);
				BufferedReader buffreader = new BufferedReader(input);

				res = "";
				String line;
				while ((line = buffreader.readLine()) != null) {
					res += line + "\n";
				}
				is.close();

				return res.toString();
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "";
	}

    @JavascriptInterface
    public void printBarcode(String name, String npid, String datetime, String ward, String test, String barcode) {
        mParent.printBarcode(name, npid, datetime, ward, test, barcode);
    }

	@JavascriptInterface
	public void debugPrint(String in) {
		Log.i("JAVASCRIPT DEBUG", in);
	}

	@JavascriptInterface
	public boolean checkConnection(String host, int timeOut) {
		boolean result = false;
		try {
			result = InetAddress.getByName(host).isReachable(timeOut);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// result = mDB.getAgegroupCount(date_selected, age_group);
		return result;
	}

	@JavascriptInterface
	public void confirmRestart(String msg) {

		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setCancelable(true);
		builder.setTitle(msg);
		builder.setInverseBackgroundForced(true);
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {

				Intent mStartActivity = new Intent(mContext, USBActivity.class);
				int mPendingIntentId = 123456;

				PendingIntent mPendingIntent = PendingIntent.getActivity(
						mContext, mPendingIntentId, mStartActivity,
						PendingIntent.FLAG_CANCEL_CURRENT);
				AlarmManager mgr = (AlarmManager) mContext
						.getSystemService(Context.ALARM_SERVICE);
				mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100,
						mPendingIntent);

				System.exit(0);

				dialog.dismiss();
			}
		});

		AlertDialog alert = builder.create();
		alert.show();

	}

}
