package com.duvitech.mybluetooth;

/**
 * Created by George on 3/11/2015.
 */
public interface DongleListener {

    void foundDongle(String address);
    void updateData(VehicleData data);

}
