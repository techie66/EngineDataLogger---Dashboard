package net.xtlive.EDL.Dashboard;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.util.Set;

public class PrefsActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);

        Preference clearall = findPreference("clearall");

        ListPreference BTList = (ListPreference) findPreference("bt_device");
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth device not found!", Toast.LENGTH_SHORT).show();
        }
        else {
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            CharSequence[] entries = new CharSequence[1];
            CharSequence[] entryValues = new CharSequence[1];
            entries[0] = "No Devices";
            entryValues[0] = "";
            if(pairedDevices.size() > 0){
                entries = new CharSequence[pairedDevices.size()];
                entryValues = new CharSequence[pairedDevices.size()];
                int i=0;
                for(BluetoothDevice device : pairedDevices){
                    entries[i] = device.getName();
                    entryValues[i] = device.getAddress();
                    i++;
                }
            }
            BTList.setEntries(entries);
            BTList.setEntryValues(entryValues);
        }


        clearall.setOnPreferenceClickListener(preference -> {
            PreferenceManager.getDefaultSharedPreferences(PrefsActivity.this).edit().clear().apply();
            PrefsActivity.this.finish();
            return true;
        });
    }
}