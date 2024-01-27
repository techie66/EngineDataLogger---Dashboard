package net.xtlive.EDL.Dashboard;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.Set;


public class SettingsFragment extends PreferenceFragmentCompat {
    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private ListPreference KeylessList;
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getPreferenceManager().getContext();
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        KeylessList = (ListPreference)  getPreferenceManager().findPreference("keyless_device");
        ListPreference BTList = (ListPreference)  getPreferenceManager().findPreference("bt_device");
        //ListPreference KeylessList = (ListPreference)  getPreferenceManager().findPreference("keyless_device");
        Preference clearall = (Preference) getPreferenceManager().findPreference("clearall");
        assert clearall != null;
        clearall.setOnPreferenceClickListener(preference -> {
            PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply();
            //context.finish();
            return true;
        });
        /*BTList.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // your code here
            }*/
        if (mBluetoothAdapter == null) {
            Toast.makeText(getActivity(), "Bluetooth device not found!", Toast.LENGTH_SHORT).show();
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
            assert BTList != null;
            BTList.setEntries(entries);
            BTList.setEntryValues(entryValues);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                //BLEscan();
            }
            else {
                KeylessList.setVisible(false);
            }
        }
    }
}