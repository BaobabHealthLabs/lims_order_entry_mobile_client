package org.baobabhealth.myusb.myusb;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.baobabhealth.myusb.myusb.util.SystemUiHider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class USBActivity extends Activity {

    private final String TAG = USBActivity.class.getSimpleName();

    private static UsbSerialPort sPort = null;

    private UsbDevice mDevice;
    private Byte[] bytes;
    private static int TIMEOUT = 0;
    private boolean forceClaim = true;
    private UsbManager mUsbManager;
    private UsbInterface mIntf;

    private UsbDeviceConnection mConnection;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private String mURL = "";

    private String mFilePath = "conn.txt";

    public WebView myWebView;

    private String networkSSID = "";
    private String networkPass = "";

    private String mWifiFilePath = "wifi-conn.txt";

    private WifiManager wifiManager;

    private ImageView mIcoWifi;

    private TextView mSSIDLabel;

    private List<ScanResult> mWifiList;

    private ConnectivityManager mConnManager;

    private NetworkInfo mWifi;

    private boolean mPopupAfterLoad;

    private String mPrevMsg = "";

    private Handler mHandler = new Handler();

    private boolean mDialogOpen = false;

    private String appWebViewTempUrl = "";

    private FrameLayout mWebContainer;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    mDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (mDevice != null) {
                            //call method to set up device communication
                            mIntf = mDevice.getInterface(0);
                            UsbEndpoint endpoint = mIntf.getEndpoint(0);
                            mConnection = mUsbManager.openDevice(mDevice);
                            mConnection.claimInterface(mIntf, forceClaim);

                        }
                    } else {
                        Log.d("INFO", "permission denied for device " + mDevice);
                    }
                }
            }

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                // UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (mDevice != null) {
                    // call your method that cleans up and closes communication with the device
                    mConnection.releaseInterface(mIntf);
                }
            }

        }
    };

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private PendingIntent mPermissionIntent;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    USBActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            USBActivity.this.updateReceivedData(data);
                        }
                    });
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // remove title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        setContentView(R.layout.activity_usb);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        Intent wifi = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);

        startService(wifi);

        stopService(wifi);

        wifi = null;

        mWebContainer = (FrameLayout) findViewById(R.id.web_container);

        myWebView = new WebView(getApplicationContext());

        mWebContainer.addView(myWebView);

        // myWebView = (WebView) findViewById(R.id.webview);

        myWebView.getSettings().setJavaScriptEnabled(true); // enable javascript

        myWebView.addJavascriptInterface(new WebAppInterface(this, this), "Android");

        myWebView.clearCache(true);

        myWebView.getSettings().setSaveFormData(false);

        if (Build.VERSION.SDK_INT <= 18) {
            myWebView.getSettings().setSavePassword(false);
        } else {
            // do nothing. because as google mentioned in the documentation -
            // "Saving passwords in WebView will not be supported in future versions"
        }

        // this.deleteDatabase("webview.db");
        // this.deleteDatabase("webviewCache.db");

        myWebView.clearHistory();

        myWebView.setWebViewClient(new WebViewClient() {
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {

                try {
                    if ((description.toString().trim().equalsIgnoreCase("the url could not be found.") ||
                            description.toString().trim().equalsIgnoreCase("the connection to the server timed out.")) &&
                            mURL.trim().length() > 0) {

                        hideErrorPage(myWebView, mURL.trim());

                    } else {

                        hideErrorPage(myWebView, "");

                        showDialog(description);

                    }
                } catch (Exception e){
                    Log.d("ERROR", e.toString());
                }

            }

            public void onPageFinished(WebView view, String url) {

                Log.i("INFO", "$$$ " + url.startsWith("data"));

                if(!url.startsWith("data")) {

                    appWebViewTempUrl = url;

                }

            }
        });

        // myWebView

        myWebView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });
        myWebView.setLongClickable(false);

        myWebView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                // The code of the hiding goest here, just call hideSoftKeyboard(View v);
                return false;
            }
        });

        mSSIDLabel = (TextView) findViewById(R.id.textSSIDLabel);

        mIcoWifi = (ImageView) findViewById(R.id.icoWifi);

        ((ImageView) mIcoWifi).setImageResource(R.drawable.wifiblack);

        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);

        wifiManager.setWifiEnabled(true);

        mConnManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        mHandler.post(wifiRunnable);

        File wfile = getBaseContext().getFileStreamPath(mWifiFilePath);

        if (wfile.exists()) {

            String[] tokens = readFile(this, mWifiFilePath).split("\\|", -1);

            if (tokens.length > 1) {

                networkSSID = tokens[0].trim();
                networkPass = tokens[1].trim();

                mPopupAfterLoad = false;

                connectToWifi(networkSSID.trim(), networkPass.trim());

            } else {

                mPopupAfterLoad = true;

            }

            tokens = null;

        } else {

            mPopupAfterLoad = true;

        }

        wfile = null;

        mIcoWifi.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                showOptions();

            }

        });

    }

    private void hideErrorPage(WebView view, String url) {

        if(url.trim().length() == 0){

            String customErrorPageHtml = "<html><body><center><h1 style='margin-top: 40px; color: #23538a;'>Page Not " +
                    "Available or Set!</h1></center></html>";
            view.loadData(customErrorPageHtml, "text/html", null);

        } else {

            String customErrorPageHtml = "<html><body><center><h2 style='margin-top: 40px; color: #23538a;'>Loading. " +
                    "Please wait...</h2></center><script>setTimeout(function(){window.location = '" + url + "';}, 500);</script></html>";
            view.loadData(customErrorPageHtml, "text/html", null);

        }

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

    }

    public void showDialog() {

        if (mDialogOpen) {

            return;

        } else {

            mDialogOpen = true;

        }

        // get prompts.xml view
        LayoutInflater li = LayoutInflater.from(USBActivity.this);
        View promptsView = li.inflate(R.layout.prompts, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                USBActivity.this);

        // set prompts.xml to alertdialog builder
        alertDialogBuilder.setView(promptsView);

        final EditText userInput = (EditText) promptsView
                .findViewById(R.id.editTextDialogUserInput);

        userInput.setText(mURL);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // get user input and set it to
                        // result
                        // edit text
                        mURL = userInput.getText().toString();

                        // File file = getBaseContext().getFileStreamPath(mFilePath);

                        writeFile(USBActivity.this, mFilePath, mURL);

                        myWebView.loadUrl(mURL);

                        mDialogOpen = false;

                        dialog.dismiss();

                        dialog = null;

                    }
                })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                mDialogOpen = false;

                                dialog.dismiss();

                                dialog = null;

                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    public void showDialog(String msg) {

        if (mDialogOpen) {

            return;

        } else {

            mDialogOpen = true;

        }

        // get prompts.xml view
        LayoutInflater li = LayoutInflater.from(USBActivity.this);
        View promptsView = li.inflate(R.layout.prompts, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                USBActivity.this);

        // set prompts.xml to alertdialog builder
        alertDialogBuilder.setView(promptsView);

        final EditText userInput = (EditText) promptsView
                .findViewById(R.id.editTextDialogUserInput);

        alertDialogBuilder.setTitle(msg);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // get user input and set it to
                        // result
                        // edit text
                        mURL = userInput.getText().toString();

                        // File file = getBaseContext().getFileStreamPath(mFilePath);

                        writeFile(USBActivity.this, mFilePath, mURL);

                        myWebView.loadUrl(mURL);

                        mDialogOpen = false;

                        dialog.dismiss();

                        dialog = null;
                    }
                })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                mDialogOpen = false;

                                dialog.cancel();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.my_options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                showDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
        // respond to menu item selection

    }

    @Override
    protected void onPause() {
        super.onPause();

        mHandler.removeCallbacks(null);

        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }

        try {

            if (!myWebView.getUrl().startsWith("data")) {

                appWebViewTempUrl = myWebView.getUrl();

            }

        } catch(Exception e){

            Log.d("ERROR", e.toString());

        }
        // myWebView.loadUrl("file:///android_asset/infAppPaused.html");

        sPort = null;

        mDevice = null;

        bytes = null;

        TIMEOUT = 0;

        forceClaim = true;

        mUsbManager = null;

        mIntf = null;

        mConnection = null;

        mSerialIoManager = null;

        mURL = null;

        mFilePath = null;

        networkSSID = null;

        networkPass = null;

        mWifiFilePath = null;

        wifiManager = null;

        mIcoWifi = null;

        mSSIDLabel = null;

        mWifiList = null;

        mConnManager = null;

        mWifi = null;

        mPrevMsg = null;

        mHandler = null;

        mDialogOpen = false;

        appWebViewTempUrl = null;

        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mHandler.post(wifiRunnable);

        Log.d(TAG, "Resumed, port=" + sPort);
        if (sPort == null) {
            // mTitleTextView.setText("No serial device.");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            mConnection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (mConnection == null) {
                // mTitleTextView.setText("Opening device failed");
                return;
            }

            try {
                sPort.open(mConnection);
                sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                // mTitleTextView.setText("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
            // mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());
        }

        if (!appWebViewTempUrl.equals("") && appWebViewTempUrl != null) {

            myWebView.loadUrl(appWebViewTempUrl);

        }

        onDeviceStateChange();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        final String message = "Read " + data.length + " bytes: \n"
                + HexDump.dumpHexString(data) + "\n\n";
        // mDumpTextView.append(message);
        // mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }

    /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context // @param driver
     */
    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, USBActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

    static void reOpen(Context context) {
        final Intent intent = new Intent(context, USBActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            //Fetch data as above thru Intent(data)
            //and set the value to your edittext
        }
    }

    public void printBarcode(String name, String npid, String datetime, String ward, String test, String barcode) {

        if (sPort == null) {

            // DeviceListActivity.show(this);

            checkPrinterSettings();

            return;

        }

        if (sPort != null) {

            String command =
                    "N\n" +
                            "q456\n" +
                            "Q151,025\n" +
                            "ZT\n" +
                            "A20,3,0,3,1,1,N,\"" + name + "\"\n" +
                            "A20,35,0,3,1,1,N,\"" + npid + "\"\n" +
                            "A221,35,0,3,1,1,N,\"" + datetime + "\"\n" +
                            "A20,90,0,3,1,1,N,\"" + ward + "\"\n" +
                            "A20,105,0,3,1,1,R,\"" + test + "\"\n" +
                            "B220,65,0,1,2,4,42,B,\"" + barcode + "\"\n" +
                            "P2\n";

            byte[] data;

            data = command.getBytes();

            try {

                sPort.write(data, 100);

            } catch (IOException e) {

                Log.d(this.getClass().getSimpleName(), "Timeout error!");

            }

            command = null;

            data = null;

        }

    }

    public void printBarcode(String name, String npid, String datetime, String ward, String test, String barcode, int count) {

        if (sPort == null) {

            // DeviceListActivity.show(this);

            checkPrinterSettings();

            return;

        }

        if (sPort != null) {

            String command =
                    "N\n" +
                            "q456\n" +
                            "Q151,025\n" +
                            "ZT\n" +
                            "A20,3,0,3,1,1,N,\"" + name + "\"\n" +
                            "A20,35,0,3,1,1,N,\"" + npid + "\"\n" +
                            "A221,35,0,3,1,1,N,\"" + datetime + "\"\n" +
                            "A20,90,0,3,1,1,N,\"" + ward + "\"\n" +
                            "A20,105,0,3,1,1,R,\"" + test + "\"\n" +
                            "B220,65,0,1,2,4,42,B,\"" + barcode + "\"\n" +
                            "P" + count + "\n";

            byte[] data;

            data = command.getBytes();

            try {

                sPort.write(data, 100);

            } catch (IOException e) {

                Log.d(this.getClass().getSimpleName(), "Timeout error!");

            }

            command = null;

            data = null;

        }

    }

    // Calling:
/*
    Context context = getApplicationContext();
    String filename = "log.txt";
    String str = read_file(context, filename);
*/
    public String readFile(Context context, String filename) {
        try {
            FileInputStream fis = context.openFileInput(filename);
            InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (FileNotFoundException e) {
            return "";
        } catch (UnsupportedEncodingException e) {
            return "";
        } catch (IOException e) {
            return "";
        }
    }

    public void writeFile(Context context, String filename, String strMsgToSave) {
        FileOutputStream fos;
        try {
            fos = context.openFileOutput(filename, Context.MODE_PRIVATE);
            try {
                fos.write(strMsgToSave.getBytes());
                fos.close();

            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    public void runJSAction(String action) {

        myWebView.loadUrl("javascript:" + action);

    }

    public void printResultBarcode(String testsTBD, String identifier, String name) {

        if (sPort == null) {

            // DeviceListActivity.show(this);

            checkPrinterSettings();

            return;

        }


        if (sPort != null) {

            String[] results = testsTBD.split(";", -1);

            String command = "";

            for (int i = 0; i < results.length; i++) {

                String[] tokens = results[i].split("\\|", -1);

                if (tokens.length < 2) {

                    break;

                }

                String messageDatetime = tokens[0];
                String test = tokens[1];
                String result = tokens[2];

                List<String> resultParts = new ArrayList<String>();

                if (result.trim().length() > 23) {

                    resultParts.add(result.substring(0, 23));

                    Double d = Math.ceil((result.trim().length() - 23) / 31);

                    int lines = d.intValue() + (((result.trim().length() - 23) % 31) > 0 ? 1 : 0);

                    for (int j = 0; j < lines; j++) {

                        String line = "";

                        if (result.trim().length() - (23 + (31 * j)) < 31) {

                            line = result.substring(23 + (31 * j), (23 + (31 * j) + result.trim().length() - (23 + (31 * j))));

                        } else {

                            line = result.substring(23 + (31 * j), (23 + (31 * j) + 31));

                        }

                        resultParts.add(line);

                    }

                } else {

                    resultParts.add(result);

                }

                command +=
                        "\n\nN\n" +
                                "q456\n" +
                                "Q151,025\n" +
                                "ZT\n" +
                                "A20,3,0,3,1,1,N,\"" + identifier + "\"\n" +
                                "A221,3,0,3,1,1,N,\"" + messageDatetime + "\"\n" +
                                "A20,33,0,3,1,1,N,\"" + name + "\"\n" +
                                "A20,60,0,3,1,1,R,\"Test:\"\n" +
                                "A110,60,0,3,1,1,N,\"" + test + "\"\n" +
                                "A20,91,0,3,1,1,R,\"Result:\"\n" +
                                "A125,91,0,3,1,1,N,\"" + resultParts.get(0).toString() + (resultParts.size() > 1 &&
                                resultParts.get(0).toString().substring(resultParts.get(0).toString().length() - 1).trim().length() != 0 ? "-" : "") + "\"\n" +
                                "A20,119,0,3,1,1,N,\"" + (resultParts.size() > 1 ? resultParts.get(1).toString() : "") + (resultParts.size() > 2 &&
                                resultParts.get(1).toString().substring(resultParts.get(1).toString().length() - 1).trim().length() != 0 ? "-" : "") + "\"\n" +
                                "P1\n\n";

                if (resultParts.size() > 2) {

                    command +=
                            "\n\nN\n" +
                                    "q456\n" +
                                    "Q151,025\n" +
                                    "ZT\n";

                    int l = 0;

                    for (int k = 2; k < resultParts.size(); k++) {

                        command +=
                                "A20," + (3 + (l * 30)) + ",0,3,1,1,N,\"" + resultParts.get(k).toString() + (resultParts.size() > (k + 1) &&
                                        resultParts.get(k).toString().substring(resultParts.get(k).toString().length() - 1).trim().length() != 0 ? "-" : "") + "\"\n";

                        l++;

                        if (l > 3) {

                            l = 0;

                            command += "P1\n\n" +
                                    "\nN\n" +
                                    "q456\n" +
                                    "Q151,025\n" +
                                    "ZT\n";

                        }

                    }

                    command += "P1\n\n";

                }

                messageDatetime = null;
                test = null;
                result = null;

            }

            byte[] data;

            data = command.getBytes();

            try {

                sPort.write(data, data.length * 1000);

            } catch (IOException e) {

                Log.d(this.getClass().getSimpleName(), e.toString());

            }

            command = null;

            data = null;

        }

    }

    void connectToWifi(String ssid, String key) {

        boolean networkSet = false;

        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration i : list) {
            if (i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
                wifiManager.disconnect();

                wifiManager.enableNetwork(i.networkId, false);

                wifiManager.reconnect();

                networkSet = true;

                break;
            }
        }

        if (!networkSet) {

            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = "\"" + ssid + "\"";

            conf.preSharedKey = "\"" + key + "\"";

            wifiManager.addNetwork(conf);

        }

        mSSIDLabel.setText(ssid.replaceAll("\"", ""));

        File file = getBaseContext().getFileStreamPath(mFilePath);

        if (file.exists()) {

            mURL = readFile(this, mFilePath);

            if (!appWebViewTempUrl.equals("")) {

                mURL = appWebViewTempUrl;

            }

            Log.i("INFO", "$$$ " + appWebViewTempUrl);

            myWebView.loadUrl(mURL);

        }

    }

    void reconnect() {

        Intent wifi = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);

        startService(wifi);

        stopService(wifi);

        connectToWifi(networkSSID.trim(), networkPass.trim());

    }

    public void showOptions() {

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                USBActivity.this);

        alertDialogBuilder.setTitle("Select Action");

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("Reset Wifi", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        if (mWifiList != null) {

                            showWifiDialog();

                            dialog.dismiss();

                            dialog = null;

                        } else {

                            showMsg("Still initialising networks. Please wait...");

                            dialog.dismiss();

                            dialog = null;

                        }

                    }
                })
                .setNeutralButton("Reset Server Link", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        showDialog();

                        dialog.dismiss();

                        dialog = null;

                    }
                })
                .setNegativeButton("Reconnect",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                reconnect();

                                dialog.dismiss();

                                dialog = null;

                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    public void showWifiDialog() {

        // get prompts.xml view
        LayoutInflater li = LayoutInflater.from(USBActivity.this);
        View promptsView = li.inflate(R.layout.ssid_view, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                USBActivity.this);

        // set prompts.xml to alertdialog builder
        alertDialogBuilder.setView(promptsView);

        final Spinner ssidInput = (Spinner) promptsView
                .findViewById(R.id.editSSID);

        final EditText ssidKeyInput = (EditText) promptsView
                .findViewById(R.id.editSSIDKey);

        List<String> list = new ArrayList<String>();

        if (mWifiList != null) {
            for (int i = 0; i < mWifiList.size(); i++) {

                list.add(mWifiList.get(i).SSID.replaceAll("\"", ""));

            }
        }

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, list);

        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        ssidInput.setAdapter(dataAdapter);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        networkSSID = ssidInput.getSelectedItem().toString().trim();

                        networkPass = ssidKeyInput.getText().toString().trim();

                        WifiConfiguration conf = new WifiConfiguration();
                        conf.SSID = "\"" + networkSSID.trim() + "\"";

                        conf.preSharedKey = "\"" + networkPass + "\"";

                        wifiManager.addNetwork(conf);

                        writeFile(USBActivity.this, mWifiFilePath, networkSSID.trim() + "|" + networkPass);

                        connectToWifi(networkSSID.trim(), networkPass);

                        dialog.dismiss();

                        dialog = null;

                    }
                })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                dialog.dismiss();

                                dialog = null;

                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    public void showMsg(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(USBActivity.this);
        builder.setCancelable(true);
        builder.setTitle(msg);
        builder.setInverseBackgroundForced(true);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                dialog = null;
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    Runnable wifiRunnable = new Runnable() {
        @Override
        public void run() {
            List<ScanResult> results = wifiManager.getScanResults();

            mWifi = mConnManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            if (mWifi.getDetailedState().toString().toLowerCase().trim() != "connected" && mPrevMsg.trim() != mWifi.getDetailedState().toString().trim()) {

                mPrevMsg = mWifi.getDetailedState().toString().trim();

                Toast.makeText(USBActivity.this, mWifi.getDetailedState().toString(), Toast.LENGTH_SHORT).show();

            } else if (mWifi.getDetailedState().toString().toLowerCase().trim() == "connected") {

                mPrevMsg = mWifi.getDetailedState().toString().trim();

            }

            // Log.i("INFO", mWifi.getDetailedState().toString());

            if (mWifi.isConnected() && networkSSID.trim().length() > 0 && networkPass.trim().length() > 0) {

                ((ImageView) mIcoWifi).setImageResource(R.drawable.wifigreen);

                if (mURL.trim().length() == 0) {

                    showDialog();

                }

            } else {

                reconnect();

                ((ImageView) mIcoWifi).setImageResource(R.drawable.wifired);

            }

            if (results == null) {

                mHandler.postDelayed(this, 3000);
                return;
            }

            mWifiList = results;

            if (mPopupAfterLoad) {

                mPopupAfterLoad = false;

                showWifiDialog();

            }


            mHandler.postDelayed(this, 3000);

        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();

        sPort = null;

        mDevice = null;

        bytes = null;

        TIMEOUT = 0;

        forceClaim = true;

        mUsbManager = null;

        mIntf = null;

        mConnection = null;

        mSerialIoManager = null;

        mURL = null;

        mFilePath = null;

        networkSSID = null;

        networkPass = null;

        mWifiFilePath = null;

        wifiManager = null;

        mIcoWifi = null;

        mSSIDLabel = null;

        mWifiList = null;

        mConnManager = null;

        mWifi = null;

        mPrevMsg = null;

        mHandler = null;

        mDialogOpen = false;

        appWebViewTempUrl = null;

        mWebContainer.removeAllViews();

        myWebView.destroy();

    }

    public void changeOrientation(String orientation) {

        Log.i("INFO", "Got " + orientation);

        if (orientation.equalsIgnoreCase("landscape")) {

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        } else if (orientation.equalsIgnoreCase("portrait")) {

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        }

    }

    public void checkPrinterSettings(){

        final List<UsbSerialDriver> drivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

        if(drivers.size() == 1) {

            UsbSerialPort port = drivers.get(0).getPorts().get(0);

            sPort = port;

            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            mConnection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (mConnection == null) {
                // mTitleTextView.setText("Opening device failed");
                return;
            }

            try {
                sPort.open(mConnection);
                sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                // mTitleTextView.setText("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }

        } else {

            DeviceListActivity.show(this);

        }

    }

}
