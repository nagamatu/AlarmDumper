/*
 * Copyright (C) 2011 Tatsuo Nagamatsu <nagamatu@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gmail.nagamatu.tool.alarm;

import org.apache.http.util.EncodingUtils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class main extends ListActivity implements Button.OnClickListener {
    private static final String TAG = "AlarmDumper";
    private static final int MENU_REFRESH = 0;
    private static final int DIALOG_NOROOT = 0;
    private static final int DIALOG_PROGRESS = 1;
    private static final String PATH;
    private String[] mPath;
    private static final Runtime sRuntime = Runtime.getRuntime();
    private Set<AlarmInfo> mAlarms = new HashSet<AlarmInfo>();
    private AlarmInfoArrayAdapter mAdapter;
    private int mButtonId = R.id.rtc;

    // Initialize PATH environment
    static {
        final String path = System.getenv("PATH");
        PATH = path == null? "/system/xbin:/system/bin:/sbin": path;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        try {
            mPath = (PATH + ":" + getFilesDir().getAbsolutePath()).split(":");
        } catch (Exception e) {
            Log.e(TAG, "Error: Setup execution path: " + e.toString());
            mPath = PATH.split(":");
        }

        Button button;
        button = (Button)findViewById(R.id.rtc);
        button.setOnClickListener(this);
        button = (Button)findViewById(R.id.rtc_wakeup);
        button.setOnClickListener(this);
        button = (Button)findViewById(R.id.elapsed);
        button.setOnClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        showDialog(DIALOG_PROGRESS);
        new Thread(new Runnable() {
            public void run() {
                execDumpSys("alarm");
                AlarmInfo[] a = mAlarms.toArray(new AlarmInfo[] {});
                Arrays.sort(a, AlarmInfo.getComparator());
                mAdapter = new AlarmInfoArrayAdapter(main.this, R.layout.alarm_info_list_view, a);
                main.this.runOnUiThread(new Runnable() {
                    public void run() {
                        setListAdapter(mAdapter);
                        onClick(findViewById(mButtonId));
                        removeDialog(DIALOG_PROGRESS);
                    }
                });
            }
        }).start();
    }

    @SuppressWarnings("unused")
    private void dumpAlarms() {
        for (Iterator<AlarmInfo> i = mAlarms.iterator(); i.hasNext(); ) {
            final AlarmInfo ai = i.next();
            Log.d(TAG, "dumpAlarms: " + ai.toString());
        }
    }

    private static boolean waitForExecution(final Process p, final String cmd) {
        boolean rval = false;
        try {
            if (p.waitFor() == 0) {
                rval = true;
            } else {
                final InputStream es = p.getErrorStream();
                dumpLogMessage(es);
                es.close();
                Log.e(TAG, "waitForExecution: " + cmd + " returns " + p.exitValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "waitForExecution: " + e.toString());
        }

        return rval;
    }

    private static void dumpLogMessage(final InputStream s) {
        try {
            while (s.available() > 0) {
                final byte[] buf = new byte[512];
                int rsz = s.read(buf);
                if (rsz > 0)
                    Log.d(TAG, EncodingUtils.getAsciiString(buf, 0, rsz));
            }
        } catch (Exception e) {
            Log.e(TAG, "dumpLogMessage: " + e.toString());
        }
    }

    private void execDumpSys(final String name) {
        for (String path: mPath) {
            final String[] cmds = new String[] { path + "/su" };
            if (! new File(cmds[0]).exists())
                continue;
            try {
                Process p = sRuntime.exec(cmds);
                OutputStream os = p.getOutputStream();
                os.write(("/system/bin/dumpsys " + name).getBytes());
                os.flush();
                os.close();
                if (waitForExecution(p, cmds[0])) {
                    final DataInputStream is = new DataInputStream(p.getInputStream());
                    mAlarms.clear();
                    parseAlarmServiceMessage(is);
                    parseAlarmStats(is);
                    is.close();
                    p.destroy();
                    return;
                }
                p.destroy();
            } catch (IOException e) {
                Log.e(TAG, "execDumpSys: " + e.toString());
                continue;
            }
        }
        main.this.runOnUiThread(new Runnable() {
            public void run() {
                showDialog(DIALOG_NOROOT);
            }
        });
    }

    private static int getSpaceCount(final String s) {
        int n = 0;
        int len = s.length();
        while (n < len && Character.isSpace(s.charAt(n))) {
            n++;
        }

        return len == n? -1: n;
    }

    private void parseAlarmStats(final DataInputStream stream) {
        //dumpLogMessage(stream);
    }

    private String getAlarmType(final String s) {
        final String[] ss = s.split(" ");
        if (ss == null || ss.length == 0) {
            return "Unknown";
        }

        return ss[0];
    }

    private String getPackageName(final String s) {
        final String[] ss = s.split("[{ }]");        
        return ss.length < 5? "Unknown": ss[5];
    }

    private void parseAlarmServiceMessage(final DataInputStream stream) {
        try {
            final Map<String,String> params = new HashMap<String,String>();

            while (stream.available() > 0) {
                final String s = stream.readLine();
                final int scount = getSpaceCount(s);
                switch (scount) {
                    case -1:
                        break;
                    case 2:
                        final String[] ss = s.substring(scount).split(":");

                        if (ss.length < 2) {
                            if (ss[0].startsWith("Alarm Stats")) {
                                return;
                            }
                            continue;
                        }

                        if (s.endsWith(":")) {
                            break;
                        }

                        if (!params.isEmpty()) {
                            mAlarms.add(new AlarmInfo(params));
                        }

                        params.clear();
                        params.put("AlarmType", getAlarmType(ss[0]));
                        params.put("PackageName", getPackageName(ss[1]));
                        break;
                    case 4:
                        parseParameters(s.substring(scount), params);
                        break;
                }
                if (scount < 0) {
                    continue;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseAlarmServiceMessage: " + e.toString());
        }
    }

    private void parseParameter(final String s, final Map<String,String> params) {
        final String[] kv = s.split("=");
        if (kv == null || kv.length < 2) {
            Log.e(TAG, "parseParameters: ignore: " + s);
            return;
        }
        params.put(kv[0], kv[1]);
        return;
    }

    private void parseParameters(final String s, final Map<String,String> params) {
        if (s == null || params == null) {
            Log.e(TAG, "parseParameters: invalid arguments: " + s);
            return;
        }

        if (s.startsWith("operation")) {
            parseParameter(s, params);
            return;
        }

        final String[] ss = s.split(" ");
        if (ss == null) {
            return;
        }

        for (String e: ss) {
            parseParameter(e, params);
        }
    }

    private static final int[] sButtonIds = { R.id.rtc, R.id.rtc_wakeup, R.id.elapsed };

    private void unselectButtons() {
        for (int id: sButtonIds) {
            Button button = (Button)findViewById(id);
            button.setEnabled(true);
            button.setBackgroundResource(R.drawable.popup_top_dark);
            button.setTextColor(Color.WHITE);
        }
    }

    private void setTabSelected(Button button) {
        button.setEnabled(false);
        button.setBackgroundResource(R.drawable.popup_top_bright);
        button.setTextColor(Color.BLACK);
    }

    public void onClick(View v) {
        if (!(v instanceof Button)) {
            Log.e(TAG, "onClick: unsupported view: " + v.getClass().getSimpleName());
            return;
        }

        mButtonId = v.getId();
        unselectButtons();
        switch (mButtonId) {
            case R.id.rtc:
                mAdapter.setAlarmType("RTC");
                break;
            case R.id.rtc_wakeup:
                mAdapter.setAlarmType("RTC_WAKEUP");
                break;
            case R.id.elapsed:
                mAdapter.setAlarmType("ELAPSED");
                break;
        }
        setTabSelected((Button)v);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_REFRESH, 0, R.string.title_menu_refresh).setIcon(R.drawable.ic_menu_refresh);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(final int featureId, final MenuItem item) {
        switch (featureId) {
            case MENU_REFRESH:
                refresh();
                return true;
        }

        return super.onMenuItemSelected(featureId, item);
    }
    
    @Override
    public Dialog onCreateDialog(final int id) {
        switch (id) {
            case DIALOG_NOROOT:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.title_noroot);
                builder.setMessage(R.string.description_noroot);
                builder.setPositiveButton(R.string.ok, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        final Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse("http://www.google.com/m/search?q=rooted+" + Build.MODEL + "+android"), "text/html");
                        startActivity(intent);
                        finish();
                    }
                });
                return builder.create();
            case DIALOG_PROGRESS:
                ProgressDialog dialog = ProgressDialog.show(this, "", getResources().getString(R.string.updating), false, false);
                return dialog;
        }

        return null;
    }
}