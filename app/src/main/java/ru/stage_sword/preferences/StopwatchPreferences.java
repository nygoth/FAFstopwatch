package ru.stage_sword.preferences;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

import ru.stage_sword.fafstopwatch.R;

/**
 * Created by nygoth on 15.10.2017.
 * Preferences for stopwatch
 */

public class StopwatchPreferences extends PreferenceActivity {
    public static class StopWatchPreferencesFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            // Fill in about field
            PackageInfo info;
            String about = getString(R.string.app_name);
            try {
                info = getActivity().getApplicationContext().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
                about = getString(R.string.preference_key_about_version, info.versionName + "." + info.versionCode);
            } catch (PackageManager.NameNotFoundException e) {}

            findPreference("about_text").setTitle(about);

            //FIXME enable after corresponding code writing
            findPreference(P.STRICT_CONTROL).setEnabled(false);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(android.R.id.content, new StopWatchPreferencesFragment()).commit();
    }

}
