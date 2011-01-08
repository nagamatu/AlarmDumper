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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class AlarmInfoArrayAdapter extends ArrayAdapter<AlarmInfo> {
    private static final String TAG = "AlarmDumper";

    private int mResourceId;
    private LayoutInflater mInflater;
    private PackageManager mPM;
    private String mAlarmType = "RTC";

    public AlarmInfoArrayAdapter(Context context, int resourceId) {
        super(context, resourceId);
        init(context, resourceId);
    }

    public AlarmInfoArrayAdapter(Context context, int resourceId, AlarmInfo[] object) {
        super(context, resourceId, object);
        init(context, resourceId);
    }

    private void init(Context context, int resourceId) {
        mResourceId = resourceId;
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPM = context.getPackageManager();
    }

    private AlarmInfo getItemByType(final int position, final String type) {
        final int total = super.getCount();
        int count = 0;
        for (int i = 0; i < total; i++) {
            final AlarmInfo ai = getItem(i);
     Log.d(TAG, "getItemByType: " + ai.mAlarmType + ": " + type);
            if (ai.mAlarmType.equalsIgnoreCase(type) || (ai.mAlarmType.startsWith("ELAPSED") && type.startsWith("ELAPSED"))) {
                if (count == position) {
                    return ai;
                }
                count++;
            }
        }

        Log.d(TAG, "getItemByType: NotFound: " + position + "/" + total);
        return null;
    }

    @Override
    public int getCount() {
        final int total = super.getCount();
        int count = 0;
        for (int i = 0; i < total; i++) {
            final AlarmInfo ai = getItem(i);
            if (ai.mAlarmType.equalsIgnoreCase(mAlarmType) || (ai.mAlarmType.startsWith("ELAPSED") && mAlarmType.startsWith("ELAPSED"))) {
                count++;
            }
        }
        
        return count;
    }

    @Override
    public View getView(final int position, final View convertView, ViewGroup parent) {
        final AlarmInfo ai = getItemByType(position, mAlarmType);
        ApplicationInfo appInfo = null;
        try {
            appInfo = mPM.getApplicationInfo(ai.mPackageName, 0);
        } catch (Exception e) {
            return null;
        }

        View view;

        if (convertView == null) {
            view = mInflater.inflate(mResourceId, parent, false);
        } else {
            view = convertView;
        }

        TextView text = (TextView)view.findViewById(R.id.name);
        text.setText(appInfo.loadLabel(mPM));
        text = (TextView)view.findViewById(R.id.interval);
        text.setText(String.valueOf(ai.mRepeatInterval));
        text = (TextView)view.findViewById(R.id.when);
        text.setText(ai.mWhen);

        ImageView image = (ImageView)view.findViewById(R.id.icon);
        try {
            Drawable icon = mPM.getApplicationIcon(ai.mPackageName);
            image.setImageDrawable(icon);
        } catch (Exception e) {
        }
        return view;
    }
    
    public void setAlarmType(final String type) {
        Log.d(TAG, "setAlarmType: " + type);
        mAlarmType = type;
        notifyDataSetInvalidated();
    }
}