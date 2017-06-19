package com.aquosense.proteos.types;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
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
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater =
                    (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.ble_device_list_item, parent, false);
        }

        final FoundDevice fd = getItem(position);
        if (fd == null) {
            return convertView;
        }

        TextView txvName = convertView.findViewById(R.id.txv_device_name);
        if (fd.getName().length() > 20) {
            txvName.setText(fd.getName().substring(0, 20));
        } else {
            txvName.setText(fd.getName());
        }

        TextView txvAddr = convertView.findViewById(R.id.txv_device_addr);
        txvAddr.setText(fd.getAddress());

        return convertView;
    }
}
