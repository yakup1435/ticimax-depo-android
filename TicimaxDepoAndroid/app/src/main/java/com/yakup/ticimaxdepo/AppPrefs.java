package com.yakup.ticimaxdepo;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {
    private static final String P = "ticimax_depo";
    public static void save(Context c, String domain, String code){ c.getSharedPreferences(P,0).edit().putString("domain",domain).putString("code",code).apply(); }
    public static String domain(Context c){ return c.getSharedPreferences(P,0).getString("domain",""); }
    public static String code(Context c){ return c.getSharedPreferences(P,0).getString("code",""); }
}
