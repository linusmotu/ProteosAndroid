package com.aquosense.proteos.types;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.aquosense.proteos.R;

import java.util.List;

import types.FoundDevice;

/**
 * Created by francis on 11/5/16.
 */
public class BleDeviceListAdapter extends ArrayAdapter<FoundDevice> {
    private Context _context = null;

    public BleDeviceListAdapter(Context context, List<FoundDevice> list) {
        super(context, R.layout.ble_device_list_item, list);
        _context = context;
        return;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater =
                    (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.ble_device_list_item, parent, false);
        }

        final FoundDevice fd = getItem(position);
        if (fd == null) {
            return convertView;
        }

        TextView txvName = (TextView) convertView.findViewById(R.id.txv_device_name);
        if (fd.getName().length() > 20) {
            txvName.setText(fd.getName().substring(0, 20));
        } else {
            txvName.setText(fd.getName());
        }

        TextView txvAddr = (TextView) convertView.findViewById(R.id.txv_device_addr);
        txvAddr.setText(fd.getAddress());

//        convertView.setOnClickListener(
//                new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        SharedPreferences userSettings =
//                                _context.getSharedPreferences("APP_SETTINGS", Context.MODE_PRIVATE);
//
//                        /* Save the chosen device to shared prefs */
//                        userSettings.edit()
//                                .putString("DEVICE_NAME", fd.getName())
//                                .putString("DEVICE_ADDR", fd.getAddress())
//                                .commit();
//
//                        return;
//                    }
//                }
//        );

        return convertView;
    }
}
