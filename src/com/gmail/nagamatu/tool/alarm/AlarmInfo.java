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

import android.util.Log;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AlarmInfo {
    private static final String TAG = "AlarmDumper";

    public String mPackageName;
    public String mAlarmType;
    public int mType;
    public String mWhen;
    public long mRepeatInterval;
    public int mCount;
    public String mOperation;

    public static long convertTime(final String time) {
        int value = 0;
        Pattern p = Pattern.compile("(\\d+)(d|h|s|ms*)");
        Matcher m = p.matcher(time);
        while (m.find()) {
            if (m.groupCount() != 2) {
                Log.e(TAG, "convertTime: failed to get time unit");
                continue;
            }
            final int v = Integer.parseInt(m.group(1));
            final String unit = m.group(2);
            if (unit.length() == 2) {   // msec
                value += v; 
                continue;
            }
            switch (unit.charAt(0)) {
                case 'd':
                    value += v * 24 * 60 * 60 * 1000;
                    break;
                case 'h':
                    value += v * 60 * 60 * 1000;
                    break;
                case 'm':
                    value += v * 60 * 1000;
                    break;
                case 's':
                    value += v * 1000;
                    break;
            }
        }

        return value;
    }

    public AlarmInfo(final Map<String,String> params) {
        super();
        for (Iterator<String> i = params.keySet().iterator(); i.hasNext(); ) {
            final String key = i.next();
            try {
                final Field f = getClass().getField("m" + Character.toUpperCase(key.charAt(0)) + key.substring(1));
                final String typeName = f.getType().getCanonicalName();
                switch (typeName.charAt(0)) {
                    case 'i':
                        f.setInt(this, Integer.valueOf(params.get(key)));
                        break;
                    case 'l':
                        final String value = params.get(key);
                        if (Character.isDigit(value.charAt(0))) {
                            f.setLong(this, Long.valueOf(value));
                        } else {
                            f.setLong(this, convertTime(value));
                        }
                        break;
                    case 'j':
                        f.set(this, params.get(key));
                        break;
                    default:
                        Log.e(TAG, "Unsupported type of field: " + key + ": " + typeName);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName() + "(");
        final Field[] ff = getClass().getFields();
        for (Field f: ff) {
            if (builder.length() > getClass().getSimpleName().length() + 1) {
                builder.append(", ");
            }
            builder.append(f.getName());
            builder.append("=");
            try {
                switch (f.getType().getCanonicalName().charAt(0)) {
                    case 'i':
                        builder.append(f.getInt(this));
                        break;
                    case 'l':
                        builder.append(f.getLong(this));
                        break;
                    case 'j':
                        builder.append(f.get(this));
                        break;
                    default:
                        builder.append("unknown");
                        break;
                }
            } catch (Exception e) {
                builder.append(e.toString());
            }
        }
        builder.append(")");

        return builder.toString();
    }
    
    private static class AlarmInfoComparator implements Comparator<AlarmInfo> {
        public int compare(AlarmInfo i1, AlarmInfo i2) {
            if (i1 == null) {
                return i2 == null? 0: -1;
            }
            if (i2 == null) {
                return 1;
            }
            int rval = i1.mPackageName.compareTo(i2.mPackageName);
            return rval == 0? (int)(convertTime(i1.mWhen) - convertTime(i2.mWhen)): rval;
        }
    }

    public static Comparator<AlarmInfo> getComparator() {
        return new AlarmInfoComparator();
    }
}
