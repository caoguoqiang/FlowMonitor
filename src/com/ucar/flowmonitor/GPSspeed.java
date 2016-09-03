package com.ucar.flowmonitor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

public class GPSspeed {
	private LocationManager mLocationManager;
	private float mSpeed=0;

	public GPSspeed(Context context) {
		Logger.d("GPSspeed���췽��");
		// �õ�LocationManager����
		mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		setCriteria();
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0, 0, new MyLocationListener());
	}

	@SuppressLint("NewApi")
	private Criteria setCriteria() {
		Logger.d("setCriteria");
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE); 			// ����Ϊ��󾫶�
		criteria.setAltitudeRequired(true); 					// Ҫ�󺣰���Ϣ
		criteria.setBearingRequired(true); 						// Ҫ��λ��Ϣ
		criteria.setBearingAccuracy(Criteria.ACCURACY_HIGH); 	// Ҫ��λ��Ϣ �ľ�ȷ��
		criteria.setCostAllowed(false); 						// �Ƿ�������
		criteria.setPowerRequirement(Criteria.POWER_LOW); 		// �Ե�����Ҫ��
		criteria.setSpeedAccuracy(Criteria.ACCURACY_HIGH);	 	// ���ٶȵľ�ȷ��
		criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH); // ��ˮƽ�ľ�ȷ��
		criteria.setSpeedRequired(true); 						// Ҫ���ٶ���Ϣ
		criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH); 	// �Դ�ֱ����
		mLocationManager.getBestProvider(criteria, true);		// �ҵ���õ����õ�Provider��
		return criteria;
	}

	private class MyLocationListener implements LocationListener {

		@Override
		public void onProviderDisabled(String arg0) {
		}

		@Override
		public void onProviderEnabled(String arg0) {
		}

		@Override
		public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		}

		@Override
		public void onLocationChanged(Location location) {
			// �豸λ�÷����ı�LocationΪ�����λ��
			if(location!=null){
				mSpeed=location.getSpeed();	
			}
			Logger.d("mSpeed="+mSpeed);
		}
	}
	
	public float getSpeed(){
		return mSpeed;
	}
}
