package com.ucar.flowmonitor;

import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

public class FlowService extends Service {
	/**
	 * ��ƵAPPÿ���ʹ�õ��������
	 */
	private static final long APP_DAY_MAX_FLOW = 50*1024*1024L;//1024*1024*1024
	/**
	 * WIFI�ȵ�ÿ��ʹ�������ﵽ��ֵ�ر��ȵ�
	 */
	private static final long WIFIAP_DAY_MAX_FLOW=15*1024*1024L;    //500*1024*1024 
	/**
	 * һ�����������ﵽ��ֵ��ÿ��wifi�ȵ�ﵽ���޺�ر��ȵ�
	 */
	private static final long WIFIAP_MONTH_MAX_FLOW=100*1024*1024L; //4*1024*1024*1024
	/**
	 * ��ƵAPP����
	 */
	private static final String PACKAGENAME_OF_VIDEO="com.qiyi.video.pad";
	
	private static final int PORT=1819;
	private static final String IP="localhost";
	
	private static final String NETWORK_URL="http://www.baidu.com";
	private static final int CYCLE_JUDGEMENT = 0;
//	private static final int PREENT_APP_NETWORK = 1;
//	private static final int CTRL_FLOW_SPEED_3G =2;
	private static final int CTRL_FLOW_SPEED_WIFIAP =3;
	private static final int CLEAN_ALL_RULES=4;
	private String privTime;
	/**
	 * wifi�ȵ�ʹ�õ�������
	 */
	private long dayWifiAPToaial; 
	/**
	 * wifi�ȵ��ϴ���������
	 */
	private long privWifiAPFlow;
	/**
	 * wifi�ȵ㵱ǰ��������
	 */
	private long currentWifiAPFlow;
	
	/**
	 * ����һ����ʹ�õ�����������
	 */
	private long monthTotialFlow;
	/**
	 * �ϴ�����������
	 */
	private long privTotialFlow;
	/**
	 * ��������������
	 */
	private long currentTotialFlow;
	
	/**
	 * ��ƵAPPʹ�õ�����������
	 */
	private long appTotialFlow;
	/**
	 * ��ƵAPP�ϴ�����������
	 */
	private long privAppFlow;
	/**
	 * ��ƵAPP��������������
	 */
	private long currentAppFlow;
	
	private String currentTime;
	private SimpleDateFormat mFormat;
	private ConnectivityManager mConManager;
	public static WifiAPHandler mWifiAPHandler;
	
	private GPSspeed mGPSspeed;
	
	private boolean isSendSocket=true;   //�ж��Ƿ����ƵAPP������
	private boolean isNeedSendClean=true;   //�ж��Ƿ���Ҫ������й���ֻ���Ƿ��͹�����й�
	private boolean isSend3G=true;       //�ж��Ƿ�3G����
	
	private boolean isApplyRules=true;   //�Ƿ����ù����ж�
//	private boolean isApplyRules=true;   //�Ƿ����ù����ж�
	private int ipSize=0; 
	private Timer mTimer;
	private Date mDate;
	
	public enum WIFI_AP_STATE {
		WIFI_AP_STATE_DISABLING, 
		WIFI_AP_STATE_DISABLED, 
		WIFI_AP_STATE_ENABLING, 
		WIFI_AP_STATE_ENABLED, 
		WIFI_AP_STATE_FAILED
		}
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		Logger.d("onCreate");
		mGPSspeed=new GPSspeed(FlowService.this);
		HandlerThread flowThread=new HandlerThread("wifiAPflow");
		flowThread.start();
		mWifiAPHandler=new WifiAPHandler(flowThread.getLooper());
		initData();
		mWifiAPHandler.sendEmptyMessage(CYCLE_JUDGEMENT);
		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Logger.d("onStartCommand");
		return super.onStartCommand(intent, flags, startId);
	}
	
	private void initData() {
		Logger.d("initData");
		privTime = FlowMPref.getTime(this);
		dayWifiAPToaial = FlowMPref.getWifiAPFlow(this);
		monthTotialFlow=FlowMPref.getMonthTotialFlow(this);
		appTotialFlow=FlowMPref.getAppFlow(this);
		
		Logger.d("initData  privTime="+privTime+"  dayWifiAPToaial="+dayWifiAPToaial+
				"  dayWifiAPToaial="+dayWifiAPToaial+"  appTotialFlow="+appTotialFlow);
		privTotialFlow =0;
		privAppFlow=0;
		privWifiAPFlow=0;
		
		currentWifiAPFlow = 0;
		currentAppFlow=0;
		currentTotialFlow=0;
		
		mFormat = new SimpleDateFormat("yyyyMMdd");
		mConManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	}
	
	/**
	 * �ж��ǲ���ͬһ����       27�ŵ��¸���26��
	 * @throws Exception 
	 */
	private boolean isSameMonth(){
		boolean isSameMonth=false;
		currentTime=mFormat.format(mDate);
		int currentMonth=mDate.getMonth()+1;
		int currentDay=mDate.getDay();
		if(privTime==null){
			return false;
		}
		int privMonth=Integer.parseInt(privTime.substring(4, 6));
		int privDay=Integer.parseInt(privTime.substring(6));
		Logger.d("isSameMonth currentMonth="+currentMonth+" currentDay="+currentDay+
				" privMonth="+privMonth+" privDay="+privDay);
		if(privDay>=27){
			if(currentMonth==privMonth){
				if(currentDay>=27){
					isSameMonth=true;
				}else{
					isSameMonth=false;
				}
			}else if(currentMonth==privMonth+1){
				if(currentDay>=27){
					isSameMonth=false;
				}else{
					isSameMonth=true;
				}
			}
		}else{
			if(currentMonth==privMonth){
				if(currentDay>=27){
					isSameMonth=false;
				}else{
					isSameMonth=true;
				}
			}else if(currentMonth==privMonth+1){
				isSameMonth=false;
			}
		}
		return isSameMonth;
	}
	
	/**
	 * �ж��ǲ���ͬһ��
	 * @throws Exception 
	 */
	private boolean isSameDay(){
		boolean isSameDay=false;
		currentTime=mFormat.format(mDate);
		if(currentTime.equals(privTime)){
			isSameDay=true;
		}else{
			isSameDay=false;
		}
		return isSameDay;
	}
	
	/**
	 * �ж�����״̬�������Ƿ����
	 * @return
	 */
	private boolean judgeNetwork(){
		boolean flag = false;
		NetworkInfo info=mConManager.getActiveNetworkInfo();
		if (info!= null) {                          //�ж��Ƿ�����������
			if(info.isAvailable()){					//�����Ƿ����
				if(info.getType() == ConnectivityManager.TYPE_WIFI){      //�ж���wifi�����ֻ���������
					if(isWifiApEnabled()){   //���wifi�ȵ����������ʹ��wifi����
						flag=true;
					}else{
						flag=false;
						Logger.d("CLEAN_ALL_RULES");
						mWifiAPHandler.sendEmptyMessage(CLEAN_ALL_RULES);        //�����wifi������������й��˹���
					}
				}else{
					flag=true;	
				}
			}else{
				flag=false;	
			}
		}
		Logger.d("judgeNetwork flag="+flag);
		return flag;
	}
	/**
	 * ��ȡ����ʱ��
	 * @return
	 * @throws Exception
	 */
	private Date getNetworkTime(){
		if(!judgeNetwork()){
			return null;
		}
		URLConnection uc=null;
		Date date=null;
		try {
			URL urlTime = new URL(NETWORK_URL);
			uc = urlTime.openConnection();
			uc.connect();
			long ld = uc.getDate(); 
			date = new Date(ld);
		} catch (Exception e) {
			Logger.d( "getNetworkTime e="+e.toString());
			e.printStackTrace();
		} 
		return date;
	}
	
	/**
	 * ��ȡwifi�ȵ㵱ǰ״̬
	 */
	private WIFI_AP_STATE getWifiAPState() {
		int tmp;
		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		try {
			Method method = wifi.getClass().getMethod("getWifiApState");
			tmp = ((Integer) method.invoke(wifi));
			Logger.d( "getWifiAPState tmp="+tmp);
			// Fix for Android 4
			if (tmp >= 10) {
				tmp = tmp - 10;
			}
			return WIFI_AP_STATE.class.getEnumConstants()[tmp];
		} catch (Exception e) {
			Logger.d( "getWifiAPState e="+e.toString());
			e.printStackTrace();
			return WIFI_AP_STATE.WIFI_AP_STATE_FAILED;
		}
	}
	
	/**
	 * ����wifi�ȵ�     open or close
	 * @param enable
	 */
	private void setWifiAP(boolean enable){
		Logger.d( "setWifiAP");
		try {
			WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			if(enable){
				wifi.setWifiEnabled(false);	
			}
			 WifiConfiguration apConfig = new WifiConfiguration(); 
			 apConfig.SSID = OpenStartReceiver.wifyAPName;  
		     apConfig.preSharedKey= OpenStartReceiver.wifyAPPassward;
		     apConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
		     apConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
		     apConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
		     apConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
		     apConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
		     apConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
		     apConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
		     apConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
		     
			Method method1 = wifi.getClass().getMethod("setWifiApEnabled",WifiConfiguration.class,Boolean.TYPE);
			method1.invoke(wifi, apConfig, enable);
		} catch (Exception e) {
			Logger.d( "setWifiAP e="+e.toString());
			e.printStackTrace();
		}
	}
	/**
	 * ��ȡwifi����������
	 */
	private long getWifiFlow(){
		long wifiFlow=(TrafficStats.getTotalRxBytes()+TrafficStats.getTotalTxBytes())-
				TrafficStats.getMobileRxBytes()+ TrafficStats.getMobileTxBytes();
		return wifiFlow;
	}
	/**
	 * �����������ܺ��ж��Ƿ�ر�wifi�ȵ㣬������ƵAPPһ������������ж��Ƿ����ƵAPP����
	 * @throws Exception
	 */
	private void judgeWifiAPAble(){
		Logger.d( "judgeWifiAPAble");
		mDate=getNetworkTime();
		if(mDate==null){               //��3G���磬������ʱ���ȡ��ȷ
			Logger.d( "getNetworkTime()==null");
			return;
		}
		//�жϳ��Ƿ����ƶ�������ƶ������������ƣ����ͣ������ʱ�䳬��4min����������
		Logger.d( "mGPSspeed="+mGPSspeed.getSpeed());
		
		if(mGPSspeed.getSpeed()<1.00){
			isNeedSendClean=true;
			if(mTimer==null){
				mTimer=new Timer();
				TimerTask	mTimeTask=new TimerTask() {
						@Override
						public void run() {
							if(!isApplyRules){
								isApplyRules=true;
							}
						}
					};
				mTimer.schedule(mTimeTask, 4*60*1000);
			}
		}else{
			if(mTimer!=null){
				mTimer.cancel();
				mTimer.purge();
				mTimer=null;
			}
			if(!isWifiApEnabled()){
				setWifiAP(true);
			}
			mWifiAPHandler.sendEmptyMessage(CLEAN_ALL_RULES);      //������й��򲢽�isApplyRules��Ϊfalse
		}
		Logger.d( "isApplyRules="+isApplyRules);
		
		if(isSend3G){                       //�ж��Ƿ�Ҫ��3G��������     
//			socketSend(Api.getCleanAllRules());
			socketSend(Api.getScript(true,null));
			isSend3G=false;
		}
		
		boolean isEnable=isWifiApEnabled();
		Logger.d("isWifiApEnabled="+isEnable);
		if(privTotialFlow==0){
			privTotialFlow=TrafficStats.getMobileRxBytes()+ TrafficStats.getMobileTxBytes();
		}
		currentTotialFlow =TrafficStats.getMobileRxBytes()+ TrafficStats.getMobileTxBytes();
		
		if(privAppFlow==0){
			privAppFlow=TrafficStats.getUidRxBytes(getVideoUid())+TrafficStats.getUidTxBytes(getVideoUid());
		}
		currentAppFlow=TrafficStats.getUidRxBytes(getVideoUid())+TrafficStats.getUidTxBytes(getVideoUid());
		
		if(isEnable){
//			socketSend(Api.getCleanAllRules());
			mWifiAPHandler.sendEmptyMessage(CTRL_FLOW_SPEED_WIFIAP);     //���wifi�ȵ����������
			if(privWifiAPFlow==0){
				privWifiAPFlow=getWifiFlow();
			}
			currentWifiAPFlow = getWifiFlow();
		}
		
		Logger.d( "judgeWifiAPAble  privTime="+privTime+"  currentTotialFlow="+currentTotialFlow+
				"  currentAppFlow="+currentAppFlow+"  currentWifiAPFlow="+currentWifiAPFlow);
		Logger.d( "judgeWifiAPAble  privTime="+privTime+"  privTotialFlow="+privTotialFlow+
				"  privAppFlow="+privAppFlow+"  privWifiAPFlow="+privWifiAPFlow);
		
		//һ��������������4G��ر�wifir�ȵ�
		if (!isSameMonth()) {       //����ͬһ����    ��ô�Ͳ�������ͬһ��
			Logger.d("!isSameMonth()");
			privTotialFlow = currentTotialFlow;
			monthTotialFlow = 0;
			FlowMPref.setMonthTotialFlow(FlowService.this, monthTotialFlow);
			//wifi�ȵ�����ж�
			if(isEnable){
				privWifiAPFlow = currentWifiAPFlow;
			}
			dayWifiAPToaial = 0;
			FlowMPref.setWifiAPFlow(FlowService.this, dayWifiAPToaial);
			//��ƵAPP����ж�
			privAppFlow = currentAppFlow;
			appTotialFlow = 0;
			FlowMPref.setAppFlow(FlowService.this, appTotialFlow);
		} else {									//��ͬһ����
			Logger.d("��ͬһ����");
			if (!isSameDay()) {       //����ͬһ��
				Logger.d("��ͬһ���� ���ǲ���ͬһ��");
				
				//wifi�ȵ�����ж�
				if(isEnable){
					privWifiAPFlow = currentWifiAPFlow;
				}
				dayWifiAPToaial = 0;
				FlowMPref.setWifiAPFlow(FlowService.this, dayWifiAPToaial);
				//��ƵAPP����ж�
				privAppFlow = currentAppFlow;
				appTotialFlow = 0;
				FlowMPref.setAppFlow(FlowService.this, appTotialFlow);
			} else {	
				Logger.d("��ͬһ����ͬһ��");//��ͬһ��
				monthTotialFlow += (currentTotialFlow - privTotialFlow);
				privTotialFlow = currentTotialFlow;
				FlowMPref.setMonthTotialFlow(FlowService.this, monthTotialFlow);
				if(isEnable){
					dayWifiAPToaial += (currentWifiAPFlow - privWifiAPFlow);
					privWifiAPFlow = currentWifiAPFlow;
					FlowMPref.setWifiAPFlow(FlowService.this, dayWifiAPToaial);
				}
				if (isApplyRules&&(monthTotialFlow >=WIFIAP_MONTH_MAX_FLOW)) {
					Logger.d("�������ﵽ���ֵ���ر�wifi�ȵ�    monthTotialFlow="+monthTotialFlow+"  WIFIAP_MONTH_MAX_FLOW="+WIFIAP_MONTH_MAX_FLOW);
					if (dayWifiAPToaial >= WIFIAP_DAY_MAX_FLOW) {
						Logger.d("wifi�ȵ��������ﵽ���ֵ���ر�wifi�ȵ�dayWifiAPToaial="+dayWifiAPToaial+"  WIFIAP_DAY_MAX_FLOW="+WIFIAP_DAY_MAX_FLOW);
						if(isWifiApEnabled()){
							setWifiAP(false);
						}
					}
				}
				
				appTotialFlow += (currentAppFlow - privAppFlow);
				privAppFlow = currentAppFlow;
				FlowMPref.setAppFlow(FlowService.this, appTotialFlow);
				if (isApplyRules&&(appTotialFlow >= APP_DAY_MAX_FLOW)) {
					if(getVideoUid()!=-1){
						Logger.d("��ƵAPP�����ﵽ���ֵ����ֹ��ƵAPP����appTotialFlow="+appTotialFlow+"  APP_DAY_MAX_FLOW="+APP_DAY_MAX_FLOW);
						if(isSendSocket){
							Logger.d("��ʼ���ͽű�ָ��");
							socketSend(Api.scriptHeader(this, getVideoUid()));
							isSendSocket=false;
						}
					}
				}
			}
		}
		privTime = currentTime;
		FlowMPref.setTime(FlowService.this, privTime);
		Logger.d("judgeWifiAPAble  privTime="+privTime+"  dayWifiAPToaial="+dayWifiAPToaial+
				"  monthTotialFlow="+monthTotialFlow+"  appTotialFlow="+appTotialFlow);
	}
	
	/**
	 * ��ȡ��ƵAPP��Uid
	 */
	private int getVideoUid(){
		int uid=-1;
		PackageManager pkgmanager = this.getPackageManager();
		List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(0);
		for(int i=0;i<installed.size();i++){
			if(installed.get(i).packageName.equals(PACKAGENAME_OF_VIDEO)){
				Logger.d("packageName="+installed.get(i).packageName);
				uid=installed.get(i).uid;
				Logger.d("uid="+uid);
				break;
			}
		}
		return uid;
	}
	
	/**
	 * ʹ��socket����UID
	 * @param data
	 * @throws Exception
	 */
	private void socketSend(String data){
		Logger.d("socketSend data=\n"+data);
		try {
			Socket client=new Socket(IP, PORT);
			DataOutputStream dos=new DataOutputStream(client.getOutputStream());
			
//			dos.writeUTF(data);
			dos.writeBytes(data);
			
			Thread.sleep(2000);
			if(dos!=null){
				dos.close();
			}
			if(client!=null){
				client=null;
			}
		} catch (Exception e) {
			Logger.d("socketSend e="+e.toString());
			e.printStackTrace();
		}  
	}
	/**�ж��ȵ�״̬*/
	public boolean isWifiApEnabled() {
		Logger.d("getWifiAPState()="+getWifiAPState());
		return getWifiAPState() == WIFI_AP_STATE.WIFI_AP_STATE_ENABLED;
	}
	
	class  WifiAPHandler extends Handler{
		public WifiAPHandler(Looper looper) {
			super(looper);
		}
		@Override
		public void handleMessage(Message msg) {
				switch(msg.what){
				case CYCLE_JUDGEMENT:
					Logger.d("CYCLE_JUDGEMENT");
					judgeWifiAPAble();
					mWifiAPHandler.sendEmptyMessageDelayed(CYCLE_JUDGEMENT,5000);
					break;
//				case PREENT_APP_NETWORK:               //�������ж������ͬһ������ƵAPP�����ѳ����޶�ֵ����ֱ�ӽ���
//					Logger.d( "PREENT_APP_NETWORK");
//					if(appTotialFlow>=APP_DAY_MAX_FLOW){
//						if(getVideoUid()!=-1){
//							if(isSendSocket){
//								Logger.d( "��ʼ���ͽű�ָ��");
//								socketSend(Api.scriptHeader(FlowService.this, getVideoUid()));
//								isSendSocket=false;
//							}
//						}
//					}
//					mWifiAPHandler.sendEmptyMessage(CYCLE_JUDGEMENT);
//					break;
//				case CTRL_FLOW_SPEED_3G:                         //������3G��������
//					socketSend(Api.getScript(true));
//					break;
				case CTRL_FLOW_SPEED_WIFIAP:         //wifi�ȵ��ʱ����wifi�ȵ�����
					ArrayList<String> ipList=Api.getConnectedIP();
					if(ipList.size()!=ipSize){
						if(ipList.size()>0){
							for(int i=0;i<ipList.size();i++){
								socketSend(Api.getScript(false,ipList.get(i)));
							}
						}
					}
					ipSize=ipList.size();
					break;
				case CLEAN_ALL_RULES:
					if(isNeedSendClean){
						socketSend(Api.getCleanAllRules());
						isNeedSendClean=false;
						isSendSocket=true;
						isSend3G=true;
						isApplyRules=false;         //�����ù����ж�
					}
					break;
				}
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	}
}
