package org.baobabhealth.myusb.myusb;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

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

    public String mString = "";
    public String mAction = "";

    /**
     * Instantiate the interface and set the context
     */
    WebAppInterface(Context c, USBActivity parent) {
        mContext = c;
        mParent = parent;

        mPrefs = c.getSharedPreferences("PrefFile", 0);

    }

    /**
     * Show a toast from the web page
     */
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
    public void printBarcode(String name, String npid, String datetime, String ward, String test, String barcode, int count) {
        mParent.printBarcode(name, npid, datetime, ward, test, barcode, count);
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

    @JavascriptInterface
    public String getMac() {
        WifiManager manager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = manager.getConnectionInfo();
        String address = info.getMacAddress();

        return address;
    }

    @JavascriptInterface
    public void showInputDialog(String msg) {

        // get prompts.xml view
        LayoutInflater li = LayoutInflater.from(mContext);
        View promptsView = li.inflate(R.layout.prompts, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                mContext);

        // set prompts.xml to alertdialog builder
        alertDialogBuilder.setView(promptsView);

        final TextView textLabel = (TextView) promptsView.findViewById(R.id.textView1);
        textLabel.setText(msg);

        final EditText userInput = (EditText) promptsView
                .findViewById(R.id.editTextDialogUserInput);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        mString = userInput.getText().toString();


                    }
                })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                dialog.cancel();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    @JavascriptInterface
    public void confirmAction(String msg, String action) {

        mAction = action;

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setCancelable(true);
        builder.setTitle(msg);
        builder.setInverseBackgroundForced(true);
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                mParent.runJSAction(mAction);

                dialog.dismiss();

            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();

    }

    @JavascriptInterface
    public void printResultBarcode(String testsTBD, String identifier, String name) {
        mParent.printResultBarcode(testsTBD, identifier, name);
    }

    @JavascriptInterface
    public String batteryStatus() {

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mContext.registerReceiver(null, ifilter);

        // Are we charging / charged?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        // How are we charging?
        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level / (float)scale;

        String bStatus = "isCharging:" + isCharging + "|usbCharge:" + usbCharge + "|chargePlug:" +
                chargePlug + "|acCharge:" + acCharge + "|batteryPct:" + batteryPct;

        return bStatus;

    }

    @JavascriptInterface
    public String connectivityStatus() {

        String bStatus = "";

        return bStatus;
    }

    @JavascriptInterface
    public void changeOrientation(String orientation) {

        mParent.changeOrientation(orientation);

    }

}
