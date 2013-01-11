/*
 * NetUtils.java
 * 
 * This class contains helper functions necessary to communicate
 * with the milesense server.
 * 
 * @author "david@milesense.com"
 * 
 * Copyright 2011, Berkeley Telematics, Inc. All rights reserved.
 */

package org.odk.collect.android.utilities;

//import static com.milesense.android.util.MileSenseStaticFunctions.msLog;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
//import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.opendatakit.httpclientandroidlib.entity.mime.content.ByteArrayBody;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;

//import com.milesense.android.constants.Configuration;
//import com.milesense.android.constants.PreferenceConstants;
//import com.milesense.android.handlers.ShutdownEventDetector;

public class NetUtils {

    // This is sure on git now.
    private static final String TAG = "MileSense-NETUTILS";
    //-----------Remove later----------
    public static String SurveyEndpoint="http://50.16.218.67/api/v2/survey/?format=json";
    public static String DataEndpoint="http://50.16.218.67/api/v2/auth/?format=json";	
	// For collapse keys. This domain may contain preference keys not defined here.
	public static final String DOMAIN_KEYS = "key_prefs";
	// For very important keys that do not change like user name password.
	public static final String DOMAIN_STATIC = "static_prefs";
	// For keys that change from time to time
	public static final String DOMAIN_DYNAMIC = "dynamic_prefs";
	public static final String PREF_UNIQUE_ID = "unique_id";
	public static final String PREF_USERNAME = "username";
	public static final String PREF_SECRET_KEY = "secret_key";
	public static final String PREF_CACHED_TRIPS = "cached_trips";
	public static final String PREF_C2DM_ID = "c2dm_id";
	public static final String PREF_UPLOAD_SCRATCH = "upload_scratch";
	public static final String PREF_BYTES_TRANSFERRED = "bytes_transferred";
	public static final int HOURS = 3600000;

    
    public static class UploadFileDescriptor {
        public String file_name;
        public String param_name;
        public long upload_byte_count;
        public ReentrantLock file_lock;

        @SuppressWarnings("unused")
        private UploadFileDescriptor() {
        }

        public UploadFileDescriptor(String req_file_name, String req_param_name, ReentrantLock req_file_lock) {
            file_name = req_file_name;
            file_lock = req_file_lock;
            param_name = req_param_name;
        }
    }

    public static boolean has_wifi_connection(Context the_context) {

        ConnectivityManager cm = (ConnectivityManager) the_context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();

        if (ni == null) {
            return false;
        }
        
        if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
            return ni.isConnectedOrConnecting();
        }
        return false;
    }

    public static boolean has_cell_connection(Context the_context) {

        ConnectivityManager cm = (ConnectivityManager) the_context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();

        if (ni == null) {
            return false;
        }
        
        TelephonyManager tm = (TelephonyManager) the_context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.isNetworkRoaming())
            return false;

        if (ni.getType() == ConnectivityManager.TYPE_MOBILE) {
            return ni.isConnectedOrConnecting();
        }
        return false;
    }

    public static boolean has_connection(Context the_context) {
        if (has_wifi_connection(the_context)) {
            return true;
        }
        
        if (has_cell_connection(the_context)) {
            return true;
        }
        
        return false;
    }

    public static HttpResponse postBytes(byte[] the_bytes, int orig_len) throws ClientProtocolException, IOException {
        int stream_len = the_bytes.length;

        HttpClient httpClient = new DefaultHttpClient();
        //HttpPost request = new HttpPost(Configuration.V2_DATA_API_ENDPOINT);
        //Put this in Configuration later - for now using class variable
        HttpPost request = new HttpPost(SurveyEndpoint);
        request.setHeader(HTTP.USER_AGENT, "MileSense");

        MultipartEntity multipartEntity = new MultipartEntity();

        ByteArrayBody bab = new ByteArrayBody(the_bytes, "application/x-gzip");
        //msLog(TAG, "Uploading " + orig_len + " bytes (" + stream_len + " compressed)");
        multipartEntity.addPart("data", (ContentBody) bab);

        request.setEntity(multipartEntity);
        return httpClient.execute(request);
    }

    public static String httpResponseToString(HttpResponse response) throws IllegalStateException, IOException {
        HttpEntity et = response.getEntity();
        InputStream is = et.getContent();

        BufferedReader r = new BufferedReader(new InputStreamReader(is));
        StringBuilder total = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            total.append(line + "\n");
        }
        return total.toString();
    }
    
    public static String getSavedDeviceId(Context aContext){
        
        TelephonyManager tm = (TelephonyManager) aContext.getSystemService(Context.TELEPHONY_SERVICE);
        String deviceId = NetUtils.getStringPreference(aContext, NetUtils.DOMAIN_STATIC, NetUtils.PREF_UNIQUE_ID, "init");
        
        if( deviceId == null || deviceId.contains("init")){
            deviceId = tm.getDeviceId();
            if( deviceId == null || deviceId.length() < 5 ){
                return null;
            }
            NetUtils.setStringPreference(aContext, NetUtils.DOMAIN_STATIC, NetUtils.PREF_UNIQUE_ID, deviceId);
        }
        return deviceId;
    }
    
    public static void sendRecovedDataFile(){
        
        try {
            String recovery_file_name = "/mnt/sdcard/milesense/recovered.txt";
            File recoveryFile = new File(recovery_file_name);
            long len = recoveryFile.length();
            if( len <= 0 ) return;

            InputStream is = new FileInputStream(recoveryFile);
            byte[] recovery_upload_buffer = new byte[(int) len];

            int bytesRead = is.read(recovery_upload_buffer);
            is.close();

            String upload_str = new String(recovery_upload_buffer);
            upload_str = upload_str.trim();

            ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
            GZIPOutputStream gzos = new GZIPOutputStream(baos) {
                {
                    def.setLevel(Deflater.BEST_COMPRESSION);
                }
            };
            gzos.write(upload_str.getBytes("UTF-8"));
            gzos.close();

            byte[] the_gzipped_bytes = baos.toByteArray();
            HttpResponse response = postBytes(the_gzipped_bytes, 0);

      //      msLog(httpResponseToString(response));
            recoveryFile.delete();

        } catch (Exception e) {
        //    msLog("Exception while uploading recovery file");
        }
    }

    public static final int RESPONSE_LOG_OUT = 2;
    public static final int RESPONSE_PURGE_DATA = 0;
    public static final int RESPONSE_RETRY_LATER = 3;
    public static final int RESPONSE_SUCCESS = 1;
    public static final int RESPONSE_NO_ATTEMPT = -1;

    public static synchronized int do_upload_v2_simple(String data_segment, Context aContext) {

        if( data_segment == null ) return RESPONSE_NO_ATTEMPT;
        if( data_segment.length() <= 0 ) return RESPONSE_NO_ATTEMPT;
        
        sendRecovedDataFile();
        
        String deviceId = getSavedDeviceId(aContext);
        if( deviceId == null ) return RESPONSE_RETRY_LATER;
        
        try {

            String username = NetUtils.getStringPreference(aContext, NetUtils.DOMAIN_STATIC,
                    NetUtils.PREF_USERNAME, "anonymous");
            String secret_key = NetUtils.getStringPreference(aContext, NetUtils.DOMAIN_STATIC,
                    NetUtils.PREF_SECRET_KEY, "anonymous");
            secret_key = NetUtils.getMD5(secret_key + username);

            String param_list = "username=" + username + "&secret_key=" + secret_key + "&identifier="
                    + deviceId;
            
            String cached_trips = NetUtils.getStringPreference(aContext, NetUtils.DOMAIN_DYNAMIC,
                    NetUtils.PREF_CACHED_TRIPS, "{}");
            
            try {
                JSONObject ctrips = new JSONObject(cached_trips);
                // Change I want to make.
                if(ctrips.has("time")) {
                    long time = ctrips.getLong("time");
                    if( (System.currentTimeMillis() - time) > 24*NetUtils.HOURS) {
                        param_list += "&cached_trips=1";                            
                    }
                } else {
                    param_list += "&cached_trips=1";
                }
            } catch( JSONException e) {
                param_list += "&cached_trips=1"; 
            }
            
            String scratch_upload_str = NetUtils.getStringPreference(aContext,
            		NetUtils.DOMAIN_DYNAMIC, NetUtils.PREF_UPLOAD_SCRATCH, "init");
            if (false == scratch_upload_str.equals("init")) {
                param_list += "&upload_scratch=" + scratch_upload_str;
            }

            String push_notification_token = NetUtils.getStringPreference(aContext,
            		NetUtils.DOMAIN_DYNAMIC, NetUtils.PREF_C2DM_ID, "none");
            if (false == push_notification_token.equals("none")) {
                param_list += "&push_notification_token=" + push_notification_token;
            }
            
            // Do this append last, since it is really big.
            param_list += data_segment;

            // We save the file to SD for debugging.
//            if (PureFunctions.is_debug_list_user_s(username)) {
//                try {
//                    String tmp_file_name = "/mnt/sdcard/milesense/zipped_upload.gz";
//                    GZIPOutputStream gzos2 = new GZIPOutputStream(new FileOutputStream(new File(tmp_file_name), false));
//                    gzos2.write(param_list.getBytes("UTF-8"));
//                    gzos2.close();
//                } catch (Exception e) {
//                    msLog(TAG, "Could not save tmp file to SD card");
//                }
//            }

            long gzip_stream_len = 0;
            HttpResponse response;
            
            try {

                ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
                GZIPOutputStream gzos = new GZIPOutputStream(baos) {
                    {
                        def.setLevel(Deflater.BEST_COMPRESSION);
                    }
                };
                gzos.write(param_list.getBytes("UTF-8"));
                gzos.close();

                byte[] the_gzipped_bytes = baos.toByteArray();
                response = postBytes(the_gzipped_bytes, param_list.length());

                if (null == response) {
                    throw new Exception("response was null");
                }

            } catch (Exception e) {
            //    msLog(TAG, "Problem posting - either file or network");
                throw e;
            }

            String responseString = httpResponseToString(response);
            //msLog(TAG, responseString);

            // Parse the JSON response
            JSONObject jsonObj;

            try {
                jsonObj = new JSONObject(responseString);
            } catch (Exception e) {
              //  ShutdownEventDetector.report(aContext, "server sent a non-json response");
                responseString = responseString.replace("\n", "NEWLINE");
                responseString = responseString.replace("\r", "REWLINE");
                //ShutdownEventDetector.report(aContext, responseString);
                NetUtils.setStringPreference(aContext, NetUtils.DOMAIN_DYNAMIC,
                        "tv_main_server_status_val", "unknown server error");
                throw e;
            }

            String responseMsg = (String) jsonObj.get("message");
            int responseCode = (Integer) jsonObj.get("code");

            if (responseCode == RESPONSE_SUCCESS) {

//                msLog(TAG, "server code 1: successfully uploaded " + gzip_stream_len + " zipped bytes");
                NetUtils.setStringPreference(aContext, NetUtils.DOMAIN_DYNAMIC,
                        "tv_main_server_status_val", "connection success");

                String tot_so_far_str = NetUtils.getStringPreference(aContext,
                		NetUtils.DOMAIN_DYNAMIC, NetUtils.PREF_BYTES_TRANSFERRED, "0");
                long tot_so_far = Long.parseLong(tot_so_far_str);
                tot_so_far += gzip_stream_len;
                NetUtils.setStringPreference(aContext, NetUtils.DOMAIN_DYNAMIC,
                        NetUtils.PREF_BYTES_TRANSFERRED, "" + tot_so_far);

                Iterator<?> kit = jsonObj.keys();
                while (kit.hasNext()) {
                    String aKey = (String) kit.next();
                    Object o = jsonObj.get(aKey);
                    MileSenseStaticFunctions.handle_message_from_server(aKey, o.toString(), aContext);
                }

                return RESPONSE_SUCCESS;

            } else if (responseCode == RESPONSE_LOG_OUT) {

            //    msLog(TAG, responseMsg);
            //    msLog(TAG, "server code 2: log out");
             //   MileSenseStaticFunctions.complete_log_out(aContext);
                return RESPONSE_LOG_OUT;

            } else if (responseCode == RESPONSE_PURGE_DATA) {

             //   msLog(TAG, responseMsg);
                NetUtils.setStringPreference(aContext, NetUtils.DOMAIN_DYNAMIC,
                        "tv_main_server_status_val", "server rejected data");
              //  ShutdownEventDetector.report(aContext, "server code 0: server rejected data");

                return RESPONSE_PURGE_DATA;

            } else if (responseCode == RESPONSE_RETRY_LATER) {

                // Keep this chunk of data - do not throw it out!
           //     msLog(TAG, responseMsg);
                NetUtils.setStringPreference(aContext, NetUtils.DOMAIN_DYNAMIC,
                        "tv_main_server_status_val", "temporary server error");
            //    ShutdownEventDetector.report(aContext, "server code 3: temporary error");

                return RESPONSE_RETRY_LATER;

            } else {

                // What is this response code???? Lets purge all the data!
         //       msLog(TAG, responseMsg);

                NetUtils.setStringPreference(aContext, NetUtils.DOMAIN_DYNAMIC,
                        "tv_main_server_status_val", "unknown server response");
           //     ShutdownEventDetector.report(aContext, "server code ?: unknown");

                return RESPONSE_RETRY_LATER;
            }

        } catch (Exception e) {
         //   ShutdownEventDetector.report(aContext, e);
            NetUtils.setStringPreference(aContext, NetUtils.DOMAIN_DYNAMIC,
                    "tv_main_server_status_val", "network error");
            return RESPONSE_RETRY_LATER;
        }
    }

    public static String httpPostData(String endpoint, Map<String, String> params, Context mContext)
            throws ClientProtocolException, IOException, SocketException, UnknownHostException {

        String responseBody = null;
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(endpoint);

        String gVersion = "";
        try {
            gVersion = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            gVersion = "unknown";
        }
        httppost.setHeader(HTTP.USER_AGENT, "MileSense/" + gVersion);
        // httppost.setHeader(HTTP.CONTENT_ENCODING, "gzip");

        // Add your data
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(params.size());

        for (Map.Entry<String, String> entry : params.entrySet()) {
            nameValuePairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }

        UrlEncodedFormEntity uefe = new UrlEncodedFormEntity(nameValuePairs);
        httppost.setEntity(uefe);

        HttpResponse response = httpclient.execute(httppost);
        responseBody = EntityUtils.toString(response.getEntity());

        return responseBody;
    }
    
	public static void setStringPreference(Context aContext, String domain, String prefKey, String prefValue) {

		SharedPreferences settings = aContext.getSharedPreferences(domain, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(prefKey, prefValue);
		editor.commit();
//		Not quite sure what this is doing --> sensor-to-location intents	
//		Intent pref_update_intent = new Intent(MileSenseIntents.PREF_UPDATE_INTENT);
//		pref_update_intent.putExtra("key", prefKey);
//		aContext.sendBroadcast(pref_update_intent);
	}
	
	public static String getStringPreference(Context aContext, String domain, String prefKey, String defValue) {
		SharedPreferences settings = aContext.getSharedPreferences(domain, Context.MODE_PRIVATE);
		return settings.getString(prefKey, defValue);		
	}
	
	public static String getStringPreference_legacy(Context aContext, String prefKey, String defValue) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(aContext);
		return settings.getString(prefKey, defValue);		
	}
	
    public static String getMD5(String s) throws NoSuchAlgorithmException {

        MessageDigest m = MessageDigest.getInstance("MD5");
        m.update( s.getBytes(), 0, s.length() );

        BigInteger md5 = new BigInteger(1, m.digest());

        return String.format("%1$032x", md5);
    }
//	public static void handle_message_from_server(String message, String payload, Context mContext){
//		
//		msLog(TAG, "Message was "+message);
//		
//		try{
//			
//			Intent i = new Intent();
//			ServerCommand sv = ServerCommand.toServerCommand(message);
//			switch( sv ){
//			    
//			    case CACHED_TRIPS:
//			        JSONObject ctrips = new JSONObject();
//			        ctrips.put("time", System.currentTimeMillis());
//			        ctrips.put("trips", payload);
//			        AndroidSystemUtils.setStringPreference(mContext, PreferenceConstants.DOMAIN_DYNAMIC, PreferenceConstants.PREF_CACHED_TRIPS, ctrips.toString());
//			        break;
//				case INTENT:
//		        	i.setAction(payload);
//		        	mContext.sendBroadcast(i);
//		        	break;
//		        	
//				case LOG_OUT:
//					MileSenseStaticFunctions.complete_log_out(mContext);
//					break;
//					
//				case HOT_SPOTS:
//					
//					AndroidSystemUtils.setStringPreference(mContext, PreferenceConstants.DOMAIN_DYNAMIC, "hot_spots", payload);
//					break;
//					
//				case SET_PREF:
//					String prefList[] = payload.split(",");
//					for(int index=0; index<prefList.length; index++){
//						String prefPair[] = prefList[index].split("=");
//						if( prefPair.length == 2){
//							AndroidSystemUtils.setStringPreference(mContext, PreferenceConstants.DOMAIN_DYNAMIC, prefPair[0], prefPair[1]);
//						}
//					}
//					break;
//					
//				case START_SERVICES:
//					// fallthrough
//				case SERVICES_START:
//					
//					ComponentName comp = new ComponentName(mContext.getPackageName(), LocationHandler.class.getName());
//					Intent locationServiceIntent = new Intent();
//					locationServiceIntent.setComponent(comp);
//					locationServiceIntent.setAction("starting from push notification");
//					ComponentName service = mContext.startService(locationServiceIntent);
//					if (null == service) { 
//						msLog(TAG, "Could not start service " + comp.toString());
//					}
//					
//					ComponentName comp2 = new ComponentName(mContext.getPackageName(), SensorService.class.getName());
//					Intent sensorServiceIntent = new Intent();
//					sensorServiceIntent.setComponent(comp2);
//					sensorServiceIntent.setAction("starting from push notification");
//					ComponentName service2 = mContext.startService(sensorServiceIntent);
//					if( null == service2){
//						msLog(TAG, "Could not start service " + comp2.toString());
//					} 
//					break;
//					
//				case KILL_SELF:
//					
//		        	i.setAction( MileSenseIntents.KILL_YOURSELF_INTENT );
//		        	mContext.sendBroadcast(i);
//		        	break;
//		        	
//				case NOTIFICATION:
//					
//					String notificationList[] = payload.split(",");
//					if( notificationList.length == 2){
//					
//						int collapse_key = notificationList[0].hashCode();
//						final int NOTIFICATION_ID = 9526;
//						AndroidSystemUtils.sendStatusBarNotification("MileSense message", notificationList[1], mContext, MainActivity.class, NOTIFICATION_ID, collapse_key);
//					}
//					break;
//					
//				case GROUPS:
//					AndroidSystemUtils.setStringPreference(mContext, PreferenceConstants.DOMAIN_DYNAMIC, PreferenceConstants.PREF_GROUPS, payload );
//					update_reported_version(mContext);
//					break;
//					
//				case DEV_VERSION:
//					AndroidSystemUtils.setStringPreference(mContext, PreferenceConstants.DOMAIN_DYNAMIC, PreferenceConstants.PREF_DEV_VERSION, payload );
//					update_reported_version(mContext);
//					break;
//					
//				case PROD_VERSION:
//					msLog("Setting prov version pref");
//					AndroidSystemUtils.setStringPreference(mContext, PreferenceConstants.DOMAIN_DYNAMIC, PreferenceConstants.PREF_PROD_VERSION, payload );
//					update_reported_version(mContext);
//					break;
//					
//				default:
//					msLog(TAG, "Server sent unknown command "+message);
//					break;
//			
//			};
//
//		}catch(Exception e){
//			msLog(e.toString());
//			e.printStackTrace();
//		}
//	}
//	public enum ServerCommand
//	{
//	    LOG_OUT, INTENT, TOAST_TEXT, NOTIFICATION, 
//	    SET_PREF, START_SERVICES, SERVICES_START, KILL_SELF,
//	    GROUPS, PROD_VERSION, DEV_VERSION, HOT_SPOTS,CACHED_TRIPS,
//	    NO_VALUE;
//
//	    public static ServerCommand toServerCommand(String str){
//	        for( ServerCommand sv: ServerCommand.values() ){
//	        	if( str.equals(sv.stringValue() )) return sv;
//	        }
//	        return NO_VALUE;
//	    }
//	    public String stringValue(){
//	        if( this.equals(LOG_OUT))  return "log_out";
//	        if( this.equals(INTENT )) return "intent";
//	        if( this.equals(SET_PREF )) return "set_pref";
//	        if( this.equals(START_SERVICES) ) return "start_services";
//	        if( this.equals(SERVICES_START) ) return "services_start";
//	        if( this.equals(KILL_SELF )) return "kill_self";
//	        if( this.equals(GROUPS )) return "groups";
//	        if( this.equals(DEV_VERSION )) return "dev_version";
//	        if( this.equals(PROD_VERSION)) return "prod_version";
//	        if( this.equals(HOT_SPOTS)) return "hot_spots";
//	        if( this.equals(CACHED_TRIPS)) return "cached_trips";
//	        return "NO_VALUE";
//	    }
//	};
}
