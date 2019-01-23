/*
 * CameraPublishActivity.java
 * CameraPublishActivity
 * 
 * Github: https://github.com/daniulive/SmarterStreaming
 * 
 * Created by DaniuLive on 2015/09/20.
 * Copyright © 2014~2016 DaniuLive. All rights reserved.
 */

package com.xuebao.rtmpPush;

import com.daniulive.smartpublisher.RecorderManager;
import com.daniulive.smartpublisher.SmartPublisherJni.WATERMARK;
import com.daniulive.smartpublisher.SmartPublisherJniV2;
import com.deerlive.usbcamera.ffmpeg.FFmpegHandle;
import com.eventhandle.NTSmartEventCallbackV2;
import com.eventhandle.NTSmartEventID;
//import com.voiceengine.NTAudioRecord;	//for audio capture..
import com.voiceengine.NTAudioRecordV2;
import com.voiceengine.NTAudioRecordV2Callback;
import com.voiceengine.NTAudioUtils;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.hardware.Camera.AutoFocusCallback;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import android_serialport_api.SerialPort;
import android_serialport_api.ComPort;
import socks.*;
import update.SilentInstall;
import  com.android.gpio.*;
@SuppressWarnings("deprecation")
public class CameraPublishActivity extends FragmentActivity {
    public enum MessageType {
        msgCheckTime,//检查系统时间是否已正确。正确之后才能开始预览并推流，否则会因为设置时间导致预览被卡住而死机
        msgOnTimeOK,//时间已准备就绪
        msgDelayPush,//延迟开始推。
        msgCheckWawajiReady,//检查娃娃机是否就绪
        msgCheckPreview,//5秒钟的预览有效性检查
        msgNetworkData,//收到应用服务器过来的数据
        msgConfigData,//收到配置变更后的数据
        msgOnUpdate,//收到更新命令--准备开始更新程序
        msgComData,//收到串口过来的数据
        msgUDiskMount,//收到U盘插入的消息。会读取里面的txt来配置本机--实际并没有使用场景
        msgQueryWawajiState,//查询娃娃机是否已处于空闲状态。 此命令是预览停止后，准备重启娃娃机时开始查询
        msgWaitIP,//等待IP
        msgIpGOT, //IP已得到。开始检查时间
        msgCheckWawaNowState, //检查娃娃机当前状态的循环
        msgUDiskUnMount, //U盘拔掉消息
        msgUpdateFreeSpace,
        msgApplyCamparam,//点击摄像头的对比度设置按钮
        msgRestoreCamparam,//点击恢复默认按钮
        msgOutputLog, //显示调试信息
        msgQueryNetworkState,//读超时，发0x92看看有没返回0x92 如无，则表明网络已断开。重连
        msgOutputDetialLog,//显示详细调试信息
        msgDelayRunInit,//20180529 听说会在启动的瞬间闪退。因此把逻辑延迟几秒以后再做。
        msgRestartH5,
        msgDelayClose,
        msgDelay2sPush,//应用配置或推流失败时，重推间隔
    };

    private static String TAG = "CameraPublishActivity";
    public static final boolean DEBUG = BuildConfig.LOG;

    public static CameraPublishActivity mainInstance = null;

    //NTAudioRecord audioRecord_ = null;	//for audio capture

    NTAudioRecordV2 audioRecord_ = null;

    NTAudioRecordV2Callback audioRecordCallback_ = null;

    private long publisherHandleBack = 0;

    private long publisherHandleFront = 0;

    private SmartPublisherJniV2 libPublisher = null;

    /* 推流分辨率选择
     * 0: 640*480
	 * 1: 320*240
	 * 2: 176*144
	 * 3: 1280*720
	 * */
    private Spinner resolutionSelector;

    /* video软编码profile设置
     * 1: baseline profile
     * 2: main profile
     * 3: high profile
	 * */
    private Spinner swVideoEncoderProfileSelector;

    private Spinner swVideoEncoderSpeedSelector;

    private Button btnHWencoder;

    private Button btnStartPush;

    private SurfaceView mSurfaceViewFront = null;
    private SurfaceHolder mSurfaceHolderFront = null;

    private SurfaceView mSurfaceViewBack = null;
    private SurfaceHolder mSurfaceHolderBack = null;

    private Camera mCameraFront = null;
    private AutoFocusCallback myAutoFocusCallbackFront = null;

    private Camera mCameraBack = null;
    private AutoFocusCallback myAutoFocusCallbackBack = null;

    private boolean mPreviewRunningFront = false;
    private boolean mPreviewRunningBack = false;

    private boolean isPushing = false;
    private boolean isRecording = false;

    private String txt = "当前状态";

    private static final int FRONT = 1;        //前置摄像头标记
    private static final int BACK = 2;        //后置摄像头标记

    private int curFrontCameraIndex = -1;
    private int curBackCameraIndex = -1;

    public static ComPort mComPort;
    public static SockAPP sendThread;//application server
    public SockConfig confiThread;//Configuration server
    MyTCServer lis_server = null;//The local listening port. Accept LAN configuration tool commands

    private WifiManager wifiManager;
    WifiAutoConnectManager wifiauto;
    private boolean USB_WIFI_CONFIG_ENABLE = false;//是否启用usb插入后读取并配置wifi的功能

    private Context myContext;

    enum PushState {UNKNOWN, OK, FAILED, CLOSE};

    PushState pst_front = PushState.UNKNOWN;
    PushState pst_back = PushState.UNKNOWN;

    boolean isTimeReady = false;//安卓系统时间初始化会引起摄像头预览卡住，从而推流失败。我们不方便给客户烧录旧系统去补救这个。所以只能推流端将摄像头在时间就绪以后初始化--add 2018.2.2
    boolean isWawajiReady = false;//检测娃娃机就绪以后才开始跟他要ip，设置IP，什么之类的。w娃娃机就绪以后，应用服务器开始心跳--add 2018.2.2

    //检查摄像头预览是否正常的变量。初始为true 每隔5秒检查是否是true。如果是设置成false。 如果检查是false 则表明预览已中断可以重启。当需要重启时，检查娃娃机是否有人在玩，没人则closesocket 重启。 有人则等待本局游戏结束，发送完成后，重启。
    //add 2018.2.2
    boolean isFrontCameraPreviewOK = true;
    boolean isBackCameraPreviewOK = true;

    boolean isShouldRebootSystem = false;//检测到推流预览停止时，这变量置为真.此时立刻查询娃娃机状态，如果娃娃机是空闲的，直接关掉socket。重启。

    int timeWaitCount = 20;//等待时间就绪的次数。我们只等2分钟。也就是20次。
    int wawajiCurrentState = -1;//娃娃机当前状态

    String sdCardPath;//20180308 存储sd卡路径
    String fronDirName = "/xuebaoRecFront";
    String backDirName = "/xuebaoRecBack";
    CheckSpaceThread checkSpaceThread = null;

    //int queryStateTimeoutTime = 0;//娃娃机状态查询超时的次数
    //新做的tab控件
    //列表控件相关
    private String[] titles = new String[]{"日志", "色彩调整"};
    private TabLayout mTabLayout;
    private ViewPager mViewPager;
    private FragmentAdapter adapter;
    //ViewPage选项卡页面列表
    private List<Fragment> mFragments;
    private List<String> mTitles;

    private int trySetMACCount = 0;//20180421 当收到心跳的mac为空时，尝试给串口发mac和本机ip。最多重试3次。已重试的次数存储在这

    //日志窗口
    View mPopupGUI = null;
    AlertDialog mPopupDlg;
    boolean bPauseOutput = false;
    //播放地址 http://kuailai.deerlive.com/h5player.html 填写ws://pili-live-rtmp.sou001.com:1250/kuailai/123456782.wsts
    private String http_url1 = "http://pili-publish.zhuwawa.dx1ydb.com:1250/aiquwawaji002/test1.httpts";
    private String http_url2 = "http://pili-publish.zhuwawa.dx1ydb.com:1250/aiquwawaji002/test2.httpts";

    //推流地址 http://pili-publish.zhuwawa.dx1ydb.com:1250/aiquwawaji002/danzhujih5001.httpts
    //播放 http://114.55.36.228/view-stream.html?wsUrl=ws://pili-live-rtmp.zhuwawa.dx1ydb.com:1250/aiquwawaji002/test1.wsts
    FFmpegHandle ffmpegH = null;
    int h5_video1_push_state = 0;//用来检测长时间断网的情况。
    int h5_video2_push_state = 0;
    boolean begin_check_h5 = false;//当此变量为真时 h5_push_state要更新
    Timer tm_check_h5 = null;
    byte[] yuv_cam1;
    byte[] yuv_cam2;//避免频繁的分配内存

    int android_mainboard_type = -1;//0-7130 1-3288
    Gpio3288 gpioPB5 = null;
    Gpio7130 gpio7130 = null;

    static {
        System.loadLibrary("SmartPublisher");
    }

    BroadcastReceiver mSdcardReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String path = intent.getData().getPath();
            if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {
                Toast.makeText(context, "U盘插入:" + intent.getData().getPath(), Toast.LENGTH_SHORT).show();
                if(CameraPublishActivity.DEBUG) Log.e(TAG, "U盘插入:" + intent.getData().getPath());
                Message message = Message.obtain();
                message.what = MessageType.msgUDiskMount.ordinal();
                message.obj = path;
                if (mHandler != null) mHandler.sendMessage(message);

            } else if (intent.getAction().equals(Intent.ACTION_MEDIA_REMOVED)) {

                Message message = Message.obtain();
                message.what = MessageType.msgUDiskUnMount.ordinal();
                message.obj = path;
                if (mHandler != null) mHandler.sendMessage(message);

                if(CameraPublishActivity.DEBUG) Log.e(TAG, "U盘拔出:" + path);
            }
        }
    };
    public boolean isExist(String path) {
        try {
            File file = new File(path);
            return file.exists();
        } catch (Exception e) {
            return false;
        }
    }

    public int execRootCmdSilent(String cmd) {
//	    	System.out.println(cmd);
        int result = -1;
        DataOutputStream dos = null;

        try {
            Process p = Runtime.getRuntime().exec("su");
            dos = new DataOutputStream(p.getOutputStream());

            Log.i(TAG, cmd);
            dos.writeBytes(cmd + "\n");
            dos.flush();
            dos.writeBytes("exit\n");
            dos.flush();
            result = p.waitFor();
//	            result = p.exitValue();
//	            System.out.println(result);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);    //屏幕常亮
        setContentView(R.layout.activity_main);
        myContext = this.getApplicationContext();
        mainInstance = this;
        isWawajiReady = true;//todo debug only

        if( USB_WIFI_CONFIG_ENABLE )
        {
            wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            wifiauto = new WifiAutoConnectManager(wifiManager);
        }

        pst_front = PushState.UNKNOWN;
        pst_back = PushState.UNKNOWN;

        IntentFilter filter = null;
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);   //接受外媒挂载过滤器
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);   //接受外媒挂载过滤器
        filter.addDataScheme("file");
        registerReceiver(mSdcardReceiver, filter, "android.permission.READ_EXTERNAL_STORAGE", null);

        VideoConfig.instance = new VideoConfig();
        VideoConfig.instance.LoadConfig(mainInstance, mHandler);

        isShouldRebootSystem = false;
        isTimeReady = false;
       // isTimeReady = true;
        isWawajiReady =  false;//note 调试模式下 先让娃娃机就绪 否则连接不了应用服务器
        timeWaitCount = 20;
        //queryStateTimeoutTime = 0;

        initUI();

        UpdateConfigToUI();

        mHandler.sendEmptyMessageDelayed(MessageType.msgDelayRunInit.ordinal(), 5000);

        //初始化GPIO。检查是哪种类型的GPIO
        ///sys/class/gpio/gpio111--这是7123
        if( isExist("/sys/class/gpio/gpio111") )
        {
            android_mainboard_type =0;
            gpio7130 = new Gpio7130();

        }else {
            android_mainboard_type =1;
            gpioPB5  = new Gpio3288(Gpio3288.RK3288_PIN7_PB5);
            String B5Direction = "chmod 777 /sys/class/gpio/gpio237/direction";
            String B5Value = "chmod 777 /sys/class/gpio/gpio237/value";
            try {
                execRootCmdSilent(B5Direction);
                execRootCmdSilent(B5Value);
            } catch (Exception e) {
                // TODO: handle exception
            }
        }

        Log.e(TAG,"主板类型" + android_mainboard_type);
    }

    private int GetRecFileList(String recDirPath)
    {
        if ( recDirPath == null )
        {
            if(CameraPublishActivity.DEBUG) Log.i(TAG, "recDirPath is null");
            return 0;
        }


        if ( recDirPath.isEmpty() )
        {
            if(CameraPublishActivity.DEBUG) Log.i(TAG, "recDirPath is empty");
            return 0;
        }


        File recDirFile = null;

        try
        {
            recDirFile = new File(recDirPath);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return 0;
        }

        if ( !recDirFile.exists() )
        {
            if(CameraPublishActivity.DEBUG)  Log.e("Tag", "rec dir is not exist, path:" + recDirPath);
            return 0;
        }

        if ( !recDirFile.isDirectory() )
        {
            if(CameraPublishActivity.DEBUG) Log.e(TAG, recDirPath + " is not dir");
            return 0;
        }


        File[] files = recDirFile.listFiles();
        if ( files == null )
        {
            return 0;
        }

        List<String>  fileList = new ArrayList<String>();

        try
        {
            for ( int i =0; i < files.length; ++i )
            {

                File recFile = files[i];
                if ( recFile == null )
                {
                    continue;
                }

                //Log.i(Tag, "recfile:" + recFile.getAbsolutePath());

                if ( !recFile.isFile() )
                {
                    continue;
                }

                if ( !recFile.exists() )
                {
                    continue;
                }

                String name = recFile.getName();
                if ( name == null )
                {
                    continue;
                }

                if ( name.isEmpty() )
                {
                    continue;
                }

                if ( name.endsWith(".mp4") )
                {
                    fileList.add(recFile.getAbsolutePath());
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return fileList.size();
    }

    //in MB
    long getSDFreesSpace(String sdP)
    {
        if( sdP.equals("") == true)
            return 0;

        StatFs sf = new StatFs(sdP);
        long blockSize = sf.getBlockSize();
        long blockCount = sf.getBlockCount();
        long availCount = sf.getAvailableBlocks();

        return (availCount*blockSize>>20);
    }

    void initRecordUI(String uPath, int total) {

        if( uPath.equals("") == true)
        {
            TextView tviapptitle = findViewById(R.id.devSpace);
            tviapptitle.setText("录像功能无法使用。原因:没有插入U盘或外置SD卡.或插入的U盘不满足存储临界条件");
        }
        else {
                StatFs sf = new StatFs(uPath);
                long blockSize = sf.getBlockSize();
                long blockCount = sf.getBlockCount();
                long availCount = sf.getAvailableBlocks();
            if(CameraPublishActivity.DEBUG) Log.d(TAG, "block大小:"+ blockSize+",block数目:"+ blockCount+",总大小:"+blockSize*blockCount/1024+"KB");
            if(CameraPublishActivity.DEBUG) Log.d(TAG, "可用的block数目：:"+ availCount+",剩余空间:"+ (availCount*blockSize>>20)+"MB");

                TextView tviapptitle = findViewById(R.id.devSpace);
                tviapptitle.setText( "已有录像个数:" + total + " 剩余可用空间: " +  (availCount*blockSize>>20)+" MB" + "盘符路径" + uPath);
            }
    }

    public static List<String> getAllExternalSdcardPath() {
        List<String> PathList = new ArrayList<String>();

        //PathList.add("/sdcard/daniulive");
        //return PathList;

        String firstPath = Environment.getExternalStorageDirectory().getPath();
        if(CameraPublishActivity.DEBUG) Log.d(TAG,"getAllExternalSdcardPath , firstPath = "+firstPath);

        try {
            // 运行mount命令，获取命令的输出，得到系统中挂载的所有目录
            Runtime runtime = Runtime.getRuntime();
            Process proc = runtime.exec("mount");
            InputStream is = proc.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            String line;
            BufferedReader br = new BufferedReader(isr);
            while ((line = br.readLine()) != null) {
                // 将常见的linux分区过滤掉
                if (line.contains("proc") || line.contains("tmpfs") || line.contains("media") || line.contains("asec") || line.contains("secure") || line.contains("system") || line.contains("cache")
                        || line.contains("sys") || line.contains("data") || line.contains("shell") || line.contains("root") || line.contains("acct") || line.contains("misc") || line.contains("obb")) {
                    continue;
                }

                // 下面这些分区是我们需要的
                if (line.contains("fat") || line.contains("fuse") || (line.contains("ntfs"))){
                    // 将mount命令获取的列表分割，items[0]为设备名，items[1]为挂载路径
                    String items[] = line.split(" ");
                    if (items != null && items.length > 1){
                        String path = items[1].toLowerCase(Locale.getDefault());
                        // 添加一些判断，确保是sd卡，如果是otg等挂载方式，可以具体分析并添加判断条件
                        if (path != null && !PathList.contains(path))
                        {
                            if(  path.contains("usb_storage") || path.contains("external_storage"))
                            {
                                PathList.add(items[1]);
                                if(CameraPublishActivity.DEBUG) Log.e(TAG,"USB1 PATH:" + path);
                            } else
                            {
                                if(CameraPublishActivity.DEBUG) Log.e(TAG,"ohter PATH:" + path);
                            }
                        }
                    }
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        return PathList;
    }

    @Override
    protected void onDestroy() {
        if(CameraPublishActivity.DEBUG) Log.i(TAG, "activity destory!");

        if( ffmpegH != null)
        {
            ffmpegH.close1();
            ffmpegH.close2();
            ffmpegH = null;
        }

        if (confiThread != null) {
            if(CameraPublishActivity.DEBUG) Log.e("app退出", "配置线程终止");
            confiThread.StopNow();
            confiThread = null;
        }

        if (sendThread != null) {
            if(CameraPublishActivity.DEBUG) Log.e("app退出", "应用线程终止");
            sendThread.StopNow();
            sendThread = null;
        }

        if (lis_server != null) {
            if(CameraPublishActivity.DEBUG) Log.e("app退出", "监听线程终止");
            lis_server.StopNow();
            lis_server = null;
        }

        if (isPushing || isRecording) {
            if (audioRecord_ != null) {
                if(CameraPublishActivity.DEBUG) Log.i(TAG, "surfaceDestroyed, call StopRecording..");

                //audioRecord_.StopRecording();
                //audioRecord_ = null;

                audioRecord_.Stop();

                if (audioRecordCallback_ != null) {
                    audioRecord_.RemoveCallback(audioRecordCallback_);
                    audioRecordCallback_ = null;
                }

                audioRecord_ = null;
            }

            stopPush();
            stopRecorder();

            isPushing = false;
            isRecording = false;

            if (publisherHandleFront != 0) {
                if (libPublisher != null) {
                    libPublisher.SmartPublisherClose(publisherHandleFront);
                    publisherHandleFront = 0;
                }
            }

            if (publisherHandleBack != 0) {
                if (libPublisher != null) {
                    libPublisher.SmartPublisherClose(publisherHandleBack);
                    publisherHandleBack = 0;
                }
            }

            if( checkSpaceThread != null)
            {
                checkSpaceThread.StopNow();
                checkSpaceThread = null;
            }
        }

        if( mComPort!= null)
        {
            mComPort.Destroy(); mComPort = null;
        }

        unregisterReceiver(mSdcardReceiver);

        Log.e("xuebaoRtmpPush","onDestroy");

        super.onDestroy();
        finish();
        System.exit(0);

    }

    void initUI() {
        TextView tviapptitle = findViewById(R.id.id_app_title);
        tviapptitle.setText(APKVersionCodeUtils.getVerName(this) + Integer.toString(VideoConfig.instance.appVersion));

        //DHCP checkbox的逻辑
        CheckBox cbDHCP = findViewById(R.id.checkBoxAutoGet);
        cbDHCP.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    findViewById(R.id.my_ip_addr).setEnabled(false);
                    findViewById(R.id.my_netgate_tip).setVisibility(View.INVISIBLE);
                    findViewById(R.id.my_gate_addr).setVisibility(View.INVISIBLE);
                    findViewById(R.id.my_netmask_tip).setVisibility(View.INVISIBLE);
                    findViewById(R.id.my_netmask_addr).setVisibility(View.INVISIBLE);
                    VideoConfig.instance.using_dhcp = true;
                    EditText eti_my_ip_addr = findViewById(R.id.my_ip_addr);
                    eti_my_ip_addr.setText(getLocalIpAddress());

                } else {
                    findViewById(R.id.my_ip_addr).setEnabled(true);
                    findViewById(R.id.my_netgate_tip).setVisibility(View.VISIBLE);
                    findViewById(R.id.my_gate_addr).setVisibility(View.VISIBLE);
                    findViewById(R.id.my_netmask_tip).setVisibility(View.VISIBLE);
                    findViewById(R.id.my_netmask_addr).setVisibility(View.VISIBLE);
                    VideoConfig.instance.using_dhcp = false;
                }
            }
        });

        //是否使用预设视频配置
        CheckBox cbPrefernce = findViewById(R.id.checkUsePrefence);
        cbPrefernce.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    findViewById(R.id.resolutionSelctor).setVisibility(View.VISIBLE);
                    findViewById(R.id.custum_w_tip).setVisibility(View.INVISIBLE);
                    findViewById(R.id.custum_wideo_w).setVisibility(View.INVISIBLE);
                    findViewById(R.id.custum_h_tip).setVisibility(View.INVISIBLE);
                    findViewById(R.id.custum_wideo_h).setVisibility(View.INVISIBLE);

                } else {
                    findViewById(R.id.resolutionSelctor).setVisibility(View.INVISIBLE);
                    findViewById(R.id.custum_w_tip).setVisibility(View.VISIBLE);
                    findViewById(R.id.custum_wideo_w).setVisibility(View.VISIBLE);
                    findViewById(R.id.custum_h_tip).setVisibility(View.VISIBLE);
                    findViewById(R.id.custum_wideo_h).setVisibility(View.VISIBLE);
                }
            }
        });

        //分辨率配置
        resolutionSelector = (Spinner) findViewById(R.id.resolutionSelctor);
        final String[] resolutionSel = new String[]{"960*720", "640*480", "640*360", "352*288", "320*240"};
        ArrayAdapter<String> adapterResolution = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, resolutionSel);
        adapterResolution.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSelector.setAdapter(adapterResolution);
        resolutionSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isPushing || isRecording) {
                    return;
                }

                SwitchResolution(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        //推流类型
        RadioGroup group = (RadioGroup)this.findViewById(R.id.pushH5);
        //绑定一个匿名监听器
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup arg0, int arg1) {
            //获取变更后的选中项的ID
                int radioButtonId = arg0.getCheckedRadioButtonId();
                if (radioButtonId == R.id.rb_rtmp){
                    VideoConfig.instance.pushH5 = false;
                }
                else if (radioButtonId == R.id.rb_mpeg){
                    VideoConfig.instance.pushH5 = true;
                }
                Log.e("PushH5...", "is" + VideoConfig.instance.pushH5);
            }
        });

        //软编码配置
        swVideoEncoderProfileSelector = (Spinner) findViewById(R.id.swVideoEncoderProfileSelector);
        final String[] profileSel = new String[]{"BaseLineProfile", "MainProfile", "HighProfile"};
        ArrayAdapter<String> adapterProfile = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, profileSel);
        adapterProfile.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        swVideoEncoderProfileSelector.setAdapter(adapterProfile);
        swVideoEncoderProfileSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isPushing || isRecording) {
                    return;
                }

                VideoConfig.instance.sw_video_encoder_profile = position + 1;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        //软编码关键帧数
        swVideoEncoderSpeedSelector = (Spinner) findViewById(R.id.sw_video_encoder_speed_selctor);
        final String[] video_encoder_speed_Sel = new String[]{"6", "5", "4", "3", "2", "1"};
        ArrayAdapter<String> adapterVideoEncoderSpeed = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, video_encoder_speed_Sel);
        adapterVideoEncoderSpeed.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        swVideoEncoderSpeedSelector.setAdapter(adapterVideoEncoderSpeed);
        swVideoEncoderSpeedSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                VideoConfig.instance.sw_video_encoder_speed = 6 - position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        btnHWencoder = (Button) findViewById(R.id.button_hwencoder);
        btnHWencoder.setOnClickListener(new ButtonHardwareEncoderListener());

        TextView timac = findViewById(R.id.id_mac);
        timac.setText("MAC: " + VideoConfig.instance.getMac());

        btnStartPush = (Button) findViewById(R.id.button_start_push);
        btnStartPush.setOnClickListener(new ButtonStartPushListener());

        //摄像头部分
        mSurfaceViewFront = (SurfaceView) this.findViewById(R.id.surface_front);
        mSurfaceHolderFront = mSurfaceViewFront.getHolder();
        mSurfaceHolderFront.addCallback(new NT_SP_SurfaceHolderCallback(FRONT));
        mSurfaceHolderFront.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        //自动聚焦变量回调
        myAutoFocusCallbackFront = new AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                if (success)//success表示对焦成功
                {
                    if(CameraPublishActivity.DEBUG)  Log.i(TAG, "Front onAutoFocus succeed...");
                } else {
                    if(CameraPublishActivity.DEBUG) Log.i(TAG, "Front onAutoFocus failed...");
                }
            }
        };

        mSurfaceViewBack = (SurfaceView) this.findViewById(R.id.surface_back);
        mSurfaceHolderBack = mSurfaceViewBack.getHolder();
        mSurfaceHolderBack.addCallback(new NT_SP_SurfaceHolderCallback(BACK));
        mSurfaceHolderBack.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        //自动聚焦变量回调
        myAutoFocusCallbackBack = new AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                if (success)//success表示对焦成功
                {
                    if(CameraPublishActivity.DEBUG)  Log.i(TAG, "Back onAutoFocus succeed...");
                } else {
                    if(CameraPublishActivity.DEBUG) Log.i(TAG, "Back onAutoFocus failed...");
                }
            }
        };

        //是否启用远程配置的逻辑
        CheckBox cbEnableCongfig = findViewById(R.id.enableConfServer);
        cbEnableCongfig.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    //启用界面 调用开启配置线程的接口
                    findViewById(R.id.config_server_ip).setEnabled(true);
                    findViewById(R.id.config_server_port).setEnabled(true);

                    VideoConfig.instance.enableConfigServer = true;
                } else {

                    //停用界面 关闭配置线程
                    findViewById(R.id.config_server_ip).setEnabled(false);
                    findViewById(R.id.config_server_port).setEnabled(false);

                    VideoConfig.instance.enableConfigServer = false;
                }
            }
        });

        //是否开启录像功能
        CheckBox cbRecord = findViewById(R.id.checkRecord);
        cbRecord.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    VideoConfig.instance.is_need_local_recorder = true;
                } else {
                    VideoConfig.instance.is_need_local_recorder = false;
                }
            }
        });

        //包含声音
        CheckBox cbIncludeAudio = findViewById(R.id.cbIncludeAudio);
        cbIncludeAudio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    VideoConfig.instance.containAudio = true;
                } else {
                    VideoConfig.instance.containAudio = false;
                }
            }
        });

        //日志的tab
        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        mTabLayout = (TabLayout) findViewById(R.id.tablayout);

        mTitles = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            mTitles.add(titles[i]);
        }
        mFragments = new ArrayList<>();
        mFragments.add(FragmentLogTxt.newInstance(0));
        mFragments.add(FragmentCamSet.newInstance(1, mHandler));

        adapter = new FragmentAdapter(getSupportFragmentManager(), mFragments, mTitles);
        mViewPager.setAdapter(adapter);//给ViewPager设置适配器
        mTabLayout.setupWithViewPager(mViewPager);//将TabLayout和ViewPager关联起来
    }

    public void OnClickViewLogDetail(View v)
    {
        //20180528 详细日志按钮 。弹出窗口
        AlertDialog.Builder builder = new AlertDialog.Builder(CameraPublishActivity.this);
        //通过LayoutInflater来加载一个xml的布局文件作为一个View对象
        mPopupGUI = LayoutInflater.from(CameraPublishActivity.this).inflate(R.layout.layout_log, null);
        //设置我们自己定义的布局文件作为弹出框的Content

        //关闭按钮
        TextView btnCloseDlg = (TextView) mPopupGUI.findViewById(R.id.id_txt_Close);
        btnCloseDlg.setOnClickListener( new View.OnClickListener()
        {
            @Override
            public void onClick(View vbtn)
            {
                bPauseOutput = true;
                if(mPopupDlg != null){mPopupDlg.dismiss(); mPopupDlg = null;}
            }
        });

        //为了捕捉调试瞬间，暂停输出。定位错误。防止滚屏
        final TextView btnPauseOutput = (TextView) mPopupGUI.findViewById(R.id.id_txt_pausetxt);
        btnPauseOutput.setOnClickListener( new View.OnClickListener()
        {
            @Override
            public void onClick(View vbtn)
            {
                bPauseOutput = !bPauseOutput;
                if( bPauseOutput == true )
                {
                    btnPauseOutput.setText("继续输出");
                } else
                    btnPauseOutput.setText("暂停输出");
            }
        });

        builder.setCancelable(false);
        builder.setView(mPopupGUI);
        mPopupDlg =  builder.show();
        bPauseOutput = false;
    }

    byte[] strIPtob(String sip) {
        String[] ipb = sip.split("\\.");
        byte[] b = new byte[4];
        b[0] = (byte) Integer.parseInt(ipb[0]);
        b[1] = (byte) Integer.parseInt(ipb[1]);
        b[2] = (byte) Integer.parseInt(ipb[2]);
        b[3] = (byte) Integer.parseInt(ipb[3]);
        return b;
    }

    int g_packget_id = 0;

    void send_com_data(int... params) {
        byte send_buf[] = new byte[8 + params.length];
        send_buf[0] = (byte) 0xfe;
        send_buf[1] = (byte) (g_packget_id);
        send_buf[2] = (byte) (g_packget_id >> 8);
        send_buf[3] = (byte) ~send_buf[0];
        send_buf[4] = (byte) ~send_buf[1];
        send_buf[5] = (byte) ~send_buf[2];
        send_buf[6] = (byte) (8 + params.length);
        for (int i = 0; i < params.length; i++) {
            send_buf[7 + i] = (byte) (params[i]);
        }

        int sum = 0;
        for (int i = 6; i < (8 + params.length - 1); i++) {
            sum += (send_buf[i] & 0xff);
        }

        send_buf[8 + params.length - 1] = (byte) (sum % 100);
        if(mComPort!=null) mComPort.SendData(send_buf, send_buf.length);
        g_packget_id++;
    }

    byte[] make_cmd(int... params) {
        byte send_buf[] = new byte[8 + params.length];
        send_buf[0] = (byte) 0xfe;
        send_buf[1] = (byte) (g_packget_id);
        send_buf[2] = (byte) (g_packget_id >> 8);
        send_buf[3] = (byte) ~send_buf[0];
        send_buf[4] = (byte) ~send_buf[1];
        send_buf[5] = (byte) ~send_buf[2];
        send_buf[6] = (byte) (8 + params.length);
        for (int i = 0; i < params.length; i++) {
            send_buf[7 + i] = (byte) (params[i]);
        }

        int sum = 0;
        for (int i = 6; i < (8 + params.length - 1); i++) {
            sum += (send_buf[i] & 0xff);
        }

        send_buf[8 + params.length - 1] = (byte) (sum % 100);

        g_packget_id++;
        return send_buf;
    }

    void SaveConfigHostInfoToCom() {
        /*if (VideoConfig.instance.enableConfigServer == false)//不启用远程配置的时候，给娃娃机传0
        {
            send_com_data(0x40, 0, 0, 0, 0, 0, 0);
            return;
        }

        String[] ipb = VideoConfig.instance.configHost.split("\\.");
        if (ipb.length < 4)
            return;

        byte[] b = new byte[6];
        b[0] = (byte) Integer.parseInt(ipb[0]);
        b[1] = (byte) Integer.parseInt(ipb[1]);
        b[2] = (byte) Integer.parseInt(ipb[2]);
        b[3] = (byte) Integer.parseInt(ipb[3]);

        b[4] = (byte) (VideoConfig.instance.GetConfigPort() / 256);
        b[5] = (byte) (VideoConfig.instance.GetConfigPort() % 256);

        send_com_data(0x40, b[0], b[1], b[2], b[3], b[4], b[5]);*/
    }

    void ComParamSet(boolean includeMAC, boolean includedLocalIP, boolean includeduserID) {
        //给娃娃机发送本机的MAC
        if (includeMAC) {
            byte msg_content[] = new byte[21];
            msg_content[0] = (byte) 0xfe;
            msg_content[1] = (byte) (0);
            msg_content[2] = (byte) (0);
            msg_content[3] = (byte) ~msg_content[0];
            msg_content[4] = (byte) ~msg_content[1];
            msg_content[5] = (byte) ~msg_content[2];
            msg_content[6] = (byte) (msg_content.length);
            msg_content[7] = (byte) 0x3f;
            String strMAC = VideoConfig.instance.getMac();
            System.arraycopy(strMAC.getBytes(), 0, msg_content, 8, strMAC.getBytes().length);
            int total_c = 0;
            for (int i = 6; i < msg_content.length - 1; i++) {
                total_c += (msg_content[i] & 0xff);
            }
            msg_content[msg_content.length - 1] = (byte) (total_c % 100);
            if(mComPort!=null) mComPort.SendData(msg_content, msg_content.length);
            String sss = SockAPP.bytesToHexString(msg_content);
            outputInfo("MaC发往串口" + sss, false);
        }

        //ip
        if (includedLocalIP) {
            byte msg_content[] = new byte[13];
            msg_content[0] = (byte) 0xfe;
            msg_content[1] = (byte) (0);
            msg_content[2] = (byte) (0);
            msg_content[3] = (byte) ~msg_content[0];
            msg_content[4] = (byte) ~msg_content[1];
            msg_content[5] = (byte) ~msg_content[2];
            msg_content[6] = (byte) (msg_content.length);
            msg_content[7] = (byte) 0x39;

            if (VideoConfig.instance.hostIP.equals(""))
                VideoConfig.instance.hostIP = getLocalIpAddress();

            byte bip[] = strIPtob(VideoConfig.instance.hostIP);
            System.arraycopy(bip, 0, msg_content, 8, bip.length);

            int total_c = 0;
            for (int i = 6; i < msg_content.length - 1; i++) {
                total_c += (msg_content[i] & 0xff);
            }
            msg_content[msg_content.length - 1] = (byte) (total_c % 100);
            if(mComPort!=null) mComPort.SendData(msg_content, msg_content.length);
        }

        //userid
        if (includeduserID) {
            byte msg_content[] = new byte[25];
            msg_content[0] = (byte) 0xfe;
            msg_content[1] = (byte) (0);
            msg_content[2] = (byte) (0);
            msg_content[3] = (byte) ~msg_content[0];
            msg_content[4] = (byte) ~msg_content[1];
            msg_content[5] = (byte) ~msg_content[2];
            msg_content[6] = (byte) (msg_content.length);
            msg_content[7] = (byte) 0x3a;

            int psw_len = VideoConfig.instance.userID.length() > 16 ? 16 : VideoConfig.instance.userID.length();
            System.arraycopy(VideoConfig.instance.userID.getBytes(), 0, msg_content, 8, psw_len);

            int total_c = 0;
            for (int i = 6; i < msg_content.length - 1; i++) {
                total_c += (msg_content[i] & 0xff);
            }
            msg_content[msg_content.length - 1] = (byte) (total_c % 100);
            if(mComPort!=null) mComPort.SendData(msg_content, msg_content.length);
        }
    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String ipAddress = inetAddress.getHostAddress().toString();
                        if (!ipAddress.contains("::"))
                            return inetAddress.getHostAddress().toString();
                    } else
                        continue;
                }
            }
        } catch (SocketException ex) {
            if(CameraPublishActivity.DEBUG)  Log.e("getloaclIp exception", ex.toString());
        }
        return "";
    }

    //更新配置到UI
    void UpdateConfigToUI() {
        //本机名称
        EditText eti_myname = findViewById(R.id.id_my_name1);
        eti_myname.setText(VideoConfig.instance.machine_name);

        //是否自动获取IP
        if (VideoConfig.instance.using_dhcp == false) {
            CheckBox cbDHCP = findViewById(R.id.checkBoxAutoGet);
            cbDHCP.setChecked(false);

            //设置本机为静态IP 并且设置IP地址
            findViewById(R.id.my_ip_addr).setEnabled(true);
            EditText eti = (EditText) findViewById(R.id.my_ip_addr);
            eti.setText(VideoConfig.instance.hostIP);

            findViewById(R.id.my_netgate_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.my_gate_addr).setVisibility(View.VISIBLE);
            eti = (EditText) findViewById(R.id.my_gate_addr);
            eti.setText(VideoConfig.instance.gateIP);

            findViewById(R.id.my_netmask_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.my_netmask_addr).setVisibility(View.VISIBLE);
            eti = (EditText) findViewById(R.id.my_netmask_addr);
            eti.setText(VideoConfig.instance.maskIP);

        } else {
            CheckBox cbDHCP = findViewById(R.id.checkBoxAutoGet);
            cbDHCP.setChecked(true);

            //设置为动态获取IP
            findViewById(R.id.my_ip_addr).setEnabled(false);
            EditText eti_my_ip_addr = findViewById(R.id.my_ip_addr);
            VideoConfig.instance.hostIP = getLocalIpAddress();
            eti_my_ip_addr.setText(VideoConfig.instance.hostIP);

            findViewById(R.id.my_netgate_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.my_gate_addr).setVisibility(View.INVISIBLE);
            findViewById(R.id.my_netmask_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.my_netmask_addr).setVisibility(View.INVISIBLE);
        }

        //是否使用预设分辨率
        CheckBox cbPrefernce = findViewById(R.id.checkUsePrefence);
        if (VideoConfig.instance.GetResolutionIndex() != -1) {
            cbPrefernce.setChecked(true);
            findViewById(R.id.resolutionSelctor).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_w_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_wideo_w).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_h_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_wideo_h).setVisibility(View.INVISIBLE);
            resolutionSelector.setSelection(VideoConfig.instance.GetResolutionIndex());
        } else {
            cbPrefernce.setChecked(false);
            findViewById(R.id.resolutionSelctor).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_w_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_wideo_w).setVisibility(View.VISIBLE);
            EditText eti = (EditText) findViewById(R.id.custum_wideo_w);
            eti.setText(Integer.toString(VideoConfig.instance.GetVideoWidth()));

            findViewById(R.id.custum_h_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_wideo_h).setVisibility(View.VISIBLE);
            eti = (EditText) findViewById(R.id.custum_wideo_h);
            eti.setText(Integer.toString(VideoConfig.instance.GetVideoHeight()));
        }

        //推流类型
       // RadioGroup group = (RadioGroup)this.findViewById(R.id.pushH5);
        RadioButton raRTMP =(RadioButton)findViewById(R.id.rb_rtmp);
        RadioButton raMPEG =(RadioButton)findViewById(R.id.rb_mpeg);
        if( VideoConfig.instance.pushH5 == true)
        {
            raRTMP.setChecked(false);
            raMPEG.setChecked(true);
        }else
            {
                raRTMP.setChecked(true);
                raMPEG.setChecked(false);
            }

        //软硬编码按钮
        if (VideoConfig.instance.is_hardware_encoder) {
            btnHWencoder.setText("当前硬编码");
            //显示软编码选项
            findViewById(R.id.swVideoEncoderProfileSelector).setVisibility(View.INVISIBLE);
            findViewById(R.id.speed_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.sw_video_encoder_speed_selctor).setVisibility(View.INVISIBLE);
        } else {
            btnHWencoder.setText("当前软编码");
            //显示软编码选项
            findViewById(R.id.swVideoEncoderProfileSelector).setVisibility(View.VISIBLE);
            swVideoEncoderProfileSelector.setSelection(VideoConfig.instance.sw_video_encoder_profile - 1);

            findViewById(R.id.speed_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.sw_video_encoder_speed_selctor).setVisibility(View.VISIBLE);
            swVideoEncoderSpeedSelector.setSelection(6 - VideoConfig.instance.sw_video_encoder_speed);
        }

        //帧率
        EditText eti = findViewById(R.id.push_rate);
        eti.setText(Integer.toString(VideoConfig.instance.GetFPS()));

        //码率
        EditText etib = findViewById(R.id.push_bitrate);
        etib.setText(Integer.toString(VideoConfig.instance.encoderKpbs));

        //推流地址1
        EditText eti_url1 = findViewById(R.id.cam1_url_edit);
        eti_url1.setText(VideoConfig.instance.url1);

        //推流地址2
        EditText eti_url2 = findViewById(R.id.cam2_url_edit);
        eti_url2.setText(VideoConfig.instance.url2);

        //应用服务器IP
        EditText eti_serverip = findViewById(R.id.server_ip);
        eti_serverip.setText(VideoConfig.instance.destHost);

        //应用服务器端口
        EditText eti_server_port = findViewById(R.id.server_port);
        eti_server_port.setText(Integer.toString(VideoConfig.instance.GetAppPort()));

        //配置服务器ip
        EditText eti_configserverip = findViewById(R.id.config_server_ip);
        eti_configserverip.setText(VideoConfig.instance.configHost);

        //配置服务器端口
        EditText eti_configserver_port = findViewById(R.id.config_server_port);
        eti_configserver_port.setText(Integer.toString(VideoConfig.instance.GetConfigPort()));

        if (VideoConfig.instance.enableConfigServer == false) {
            eti_configserverip.setEnabled(false);
            eti_configserver_port.setEnabled(false);

            CheckBox cbEnableConfig = findViewById(R.id.enableConfServer);
            cbEnableConfig.setChecked(false);

        } else {
            CheckBox cbEnableConfig = findViewById(R.id.enableConfServer);
            cbEnableConfig.setChecked(true);

            eti_configserverip.setEnabled(true);
            eti_configserver_port.setEnabled(true);
        }

        //录像选项
        CheckBox cbRecord = findViewById(R.id.checkRecord);
        if (VideoConfig.instance.is_need_local_recorder == true)
        {
            cbRecord.setChecked(true);
        }
        else {
            cbRecord.setChecked(false);
        }

        CheckBox cbIncludeAudio = findViewById(R.id.cbIncludeAudio);
        if( VideoConfig.instance.containAudio == true)
        {
            cbIncludeAudio.setChecked(true);
        }
        else
            {
                cbIncludeAudio.setChecked(false);
            }

        EditText eti_userID = findViewById(R.id.id_userid);
        eti_userID.setText(VideoConfig.instance.userID);

        updateCamUI();//更新tablayout里面的值
    }

    //更新亮度饱和度对比到界面
    void updateCamUI()
    {
        FragmentCamSet fcam = (FragmentCamSet) mFragments.get(1);
        fcam.ConfigToUI();
    }

    //将亮度饱和度对比度应用到摄像头
    public void ApplyCam3Params()
    {
        if( mCameraFront != null)
        {
            Camera.Parameters parameters;
            try {
                parameters = mCameraFront.getParameters();
                parameters.set("staturation", VideoConfig.instance.staturation);
                parameters.set("contrast", VideoConfig.instance.contrast);
                parameters.set("brightness", VideoConfig.instance.brightness);

                parameters.flatten();
                mCameraFront.setParameters(parameters);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if( mCameraBack != null)
        {
            Camera.Parameters parameters;
            try {
                parameters = mCameraBack.getParameters();
                parameters.set("staturation", VideoConfig.instance.staturation);
                parameters.set("contrast", VideoConfig.instance.contrast);
                parameters.set("brightness", VideoConfig.instance.brightness);

                parameters.flatten();
                mCameraBack.setParameters(parameters);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    boolean SaveConfigFromUI() {
        //保存--本机名称
        EditText eti_myname = findViewById(R.id.id_my_name1);
        VideoConfig.instance.machine_name = eti_myname.getText().toString().trim();

        //dhcp
        CheckBox cbDHCP = findViewById(R.id.checkBoxAutoGet);
        if (cbDHCP.isChecked()) {
            VideoConfig.instance.using_dhcp = true;
            EditText eti_my_ip_addr = findViewById(R.id.my_ip_addr);
            VideoConfig.instance.hostIP = getLocalIpAddress();
            eti_my_ip_addr.setText(VideoConfig.instance.hostIP);
        } else {
            VideoConfig.instance.using_dhcp = false;

            EditText eti_my_ip_addr = findViewById(R.id.my_ip_addr);
            VideoConfig.instance.hostIP = eti_my_ip_addr.getText().toString().trim();

            ComParamSet(false, true, false);

            EditText eti_my_gate = findViewById(R.id.my_gate_addr);
            VideoConfig.instance.gateIP = eti_my_gate.getText().toString().trim();

            EditText eti_my_mask = findViewById(R.id.my_netmask_addr);
            VideoConfig.instance.maskIP = eti_my_mask.getText().toString().trim();
        }

        //推流分辨率 查看是否使用预设
        //是否使用预设分辨率
        CheckBox cbPrefernce = findViewById(R.id.checkUsePrefence);
        if (cbPrefernce.isChecked()) {
            VideoConfig.instance.SetResolutionIndex(resolutionSelector.getSelectedItemPosition());
        } else//不使用
        {
            VideoConfig.instance.SetResolutionIndex(-1);

            EditText eti_my_video_w = findViewById(R.id.custum_wideo_w);
            String ss = eti_my_video_w.getText().toString().trim();
            VideoConfig.instance.SetVideoWidth(Integer.parseInt(eti_my_video_w.getText().toString().trim()));

            EditText eti_my_video_h = findViewById(R.id.custum_wideo_h);
            VideoConfig.instance.SetVideoHeight(Integer.parseInt(eti_my_video_h.getText().toString().trim()));
        }

        RadioButton raRTMP =(RadioButton)findViewById(R.id.rb_rtmp);
        //RadioButton raMPEG =(RadioButton)findViewById(R.id.rb_mpeg);
        if( raRTMP.isChecked())
            VideoConfig.instance.pushH5 = false;
        else
            VideoConfig.instance.pushH5 = true;

        Log.e("PushH5", "###############is" + VideoConfig.instance.pushH5);

        //is_hardware_encoder已经实时更改了
        if (VideoConfig.instance.is_hardware_encoder) {

        } else//软编码
        {
            //软编码配置
            VideoConfig.instance.sw_video_encoder_profile = swVideoEncoderProfileSelector.getSelectedItemPosition() + 1;

            //软编码关键帧数
            VideoConfig.instance.sw_video_encoder_speed = 6 - swVideoEncoderSpeedSelector.getSelectedItemPosition();
        }

        //帧率
        EditText eti = findViewById(R.id.push_rate);
        String strFPS = eti.getText().toString().trim();
        VideoConfig.instance.SetFPS(Integer.parseInt(strFPS));

        EditText etib = findViewById(R.id.push_bitrate);
        String strBitRate = etib.getText().toString().trim();
        VideoConfig.instance.encoderKpbs = Integer.parseInt(strBitRate);

        //保存--推流地址
        EditText cam_front_url = (EditText) findViewById(R.id.cam1_url_edit);
        EditText cam_back_url = (EditText) findViewById(R.id.cam2_url_edit);

        VideoConfig.instance.url1 = cam_front_url.getText().toString().trim();
        VideoConfig.instance.url2 = cam_back_url.getText().toString().trim();

        EditText eti_serverip = findViewById(R.id.server_ip);
        VideoConfig.instance.destHost = eti_serverip.getText().toString().trim();

        EditText eti_server_port = findViewById(R.id.server_port);
        String strPort = eti_server_port.getText().toString().trim();
        VideoConfig.instance.SetAppPort(Integer.parseInt(strPort));

        EditText eti_config_serverip = findViewById(R.id.config_server_ip);
        VideoConfig.instance.configHost = eti_config_serverip.getText().toString().trim();

        EditText eti_config_server_port = findViewById(R.id.config_server_port);
        String streti_config_serveripPort = eti_config_server_port.getText().toString().trim();
        VideoConfig.instance.SetConfigPort(Integer.parseInt(streti_config_serveripPort));

        EditText eti_userID = findViewById(R.id.id_userid);
        VideoConfig.instance.userID = eti_userID.getText().toString().trim();
        ComParamSet(false, false, true);

        boolean is_applyok = true;
        if (VideoConfig.instance.GetResolutionIndex() == -1)//查看自定义的配置是否合法 不合法不推。
        {
            Camera front_camera = GetCameraObj(FRONT);
            if (front_camera != null && mSurfaceHolderFront != null) {
                front_camera.stopPreview();
                is_applyok = initCamera(FRONT, mSurfaceHolderFront);
            }

            Camera back_camera = GetCameraObj(BACK);
            if (back_camera != null && mSurfaceHolderBack != null) {
                back_camera.stopPreview();
                if (is_applyok)
                    is_applyok = initCamera(BACK, mSurfaceHolderBack);
            }

            //当两个摄像头都不存在 并且不是预设分辨率时 视为失败。不要问为什么。和小林商量的时候确定的
            if (front_camera == null && back_camera == null && VideoConfig.instance.GetResolutionIndex() == -1)
                is_applyok = false;

            if (is_applyok == false) {
                RestoreConfigAndUpdateVideoUI();
                Toast.makeText(getApplicationContext(), "Invalid video configuration, reverted to its original state!", Toast.LENGTH_SHORT).show();
                if(CameraPublishActivity.DEBUG)  Log.e(TAG, "Invalid video configuration, reverted to its original state");
                //	return false;
            }
        }

        //是否启用录像
        CheckBox chRecorder = findViewById(R.id.checkRecord);
        if (chRecorder.isChecked()) {
            VideoConfig.instance.is_need_local_recorder = true;
        } else {
            VideoConfig.instance.is_need_local_recorder = false;
        }

        CheckBox cbIncludeAudio = findViewById(R.id.cbIncludeAudio);
        if( cbIncludeAudio.isChecked() )
        {
            VideoConfig.instance.containAudio = true;
        }
        else
        {
            VideoConfig.instance.containAudio = false;
        }

        return true;
    }

    private void outputInfo(String strTxt, boolean append ) {
        FragmentLogTxt fc = (FragmentLogTxt) mFragments.get(0);
        fc.outputInfo( strTxt , append);

        outputDetailLog(strTxt);
    }

    public void outputDetailLog(String strTxt)
    {
        if(mPopupDlg != null && bPauseOutput == false)
        {
            EditText et = (EditText) mPopupDlg.findViewById(R.id.txt_recv);
            String str_conten = et.getText().toString();
            if(et.getLineCount() >150)
               et.setText( strTxt );
            else
            {
                str_conten += "\r\n";
                str_conten += strTxt;
                et.setText(str_conten);
            }

            et.setMovementMethod(ScrollingMovementMethod.getInstance());
            et.setSelection(et.getText().length(), et.getText().length());
        }
    }

    //用来检测拔网线.南软的板拔网线就知道了。老罗的板不行，所以要加这个步骤
    private boolean network_query_issend = false;
    private boolean network_query_isrecv = false;

    //直接处理串口过来的数据。如有必要再sendmesage到主线程处理。否则直接透传。提高处理速度。因为sendmessage调度太慢了。。。20180428
    public void ThreadHandleCom(byte[] com_data, int len)
    {
        //处理从串口过来的消息。只处理相应的部分 其余全部转发
        //串口过来的 主程序要处理
        // 0x33 游戏结束
        // 0x34 娃娃机状态
        // 0x35 心跳
        // 0x42 设置ip的通知。其中0x42不透传--20180529已废弃 0x3c 0x42 。因为部分的rom不支持3c

        //打印调试输出
        String str_com_data ="Serial data" + ComPort.bytes2HexString(com_data, len);
        Message msgLog = Message.obtain();
        msgLog.what = CameraPublishActivity.MessageType.msgOutputLog.ordinal();
        msgLog.obj = str_com_data;
        if(mHandler!= null) mHandler.sendMessage(msgLog);

        int cmd_value = com_data[7]&0xff;
        if( cmd_value != 0x42 && cmd_value !=0x35 ) {//先透传
            if( CameraPublishActivity.sendThread != null)
                CameraPublishActivity.sendThread.sendMsg( com_data );
        }

        //主线程选择处理感兴趣的部分
        if( cmd_value == 0x33
                ||cmd_value == 0x34
                ||cmd_value == 0x35
                ||cmd_value == 0x42
                ||cmd_value == 0x17 )
        {
            if( cmd_value == 0x35 )//特殊处理 看看mac为空时给板设置mac。然后发自己的心跳
            {
                //检查mac是否是全空 是的话 mac ip 发给串口--add 20180420.防止主板重启而安卓板不重启的情况.
                if (trySetMACCount<3 &&
                        len >= 21
                        && com_data[8] == 0
                        && com_data[9] == 0
                        && com_data[10] == 0
                        && com_data[11] == 0
                        && com_data[12] == 0
                        && com_data[13] == 0
                        && com_data[14] == 0
                        && com_data[15] == 0
                        && com_data[16] == 0
                        && com_data[17] == 0
                        && com_data[18] == 0
                        && com_data[19] == 0)
                {
                    trySetMACCount ++;
                    ComParamSet(true, true, false);
                }

                if(sendThread != null) {
                    if( sendThread.heartBeat() == true)
                    {
                        Message me1 = Message.obtain();//心跳消息
                        me1.what = MessageType.msgOutputLog.ordinal();
                        me1.obj = "Heartbeat.";
                        if (mHandler != null) mHandler.sendMessage(me1);
                    }
                    else {
                            Message me1 = Message.obtain();//心跳消息
                            me1.what = MessageType.msgOutputLog.ordinal();
                            me1.obj = "Received a serial heartbeat, but the transmission was unsuccessful.";
                            if (mHandler != null) mHandler.sendMessage(me1);
                        }
                }
            }

            Message message = Message.obtain();
            message.what = CameraPublishActivity.MessageType.msgComData.ordinal();
            message.arg1 = len;
            message.obj = com_data;
            if (mHandler != null) mHandler.sendMessage(message);
        }
    }

    //同上直接处理socket收到的数据。如果不是主线程必须处理的，则直接透传给串口。提高响应速度
    public void ThreadHandleSockData(byte[] sock_data, int len)
    {
        //打印调试输出
        String str_com_data ="Network data" + ComPort.bytes2HexString(sock_data, len);
        Message msgLog = Message.obtain();
        msgLog.what = CameraPublishActivity.MessageType.msgOutputLog.ordinal();
        msgLog.obj = str_com_data;
        if (mHandler != null) mHandler.sendMessage(msgLog);

        //处理0x31 0x88 0x92 0x90 0x93 0x99 其他直接透传
        int cmd_value = sock_data[7]&0xff;
        if( cmd_value != 0x31
            && cmd_value != 0x88
            && cmd_value !=0x90
            && cmd_value != 0x92
                && cmd_value != 0x93
            && cmd_value !=0x99
                && cmd_value !=0x35 //20181024
                ) {//先透传
            if( CameraPublishActivity.mComPort != null)
                CameraPublishActivity.mComPort.SendData( sock_data , len);
        }

        //处理状态查询返回
        if( cmd_value == 0x92 )
        {
            if( network_query_issend )
            {
                network_query_isrecv = true;
            }
        }

        //开局指令
        if ( cmd_value == 0x31)
        {
            if(isShouldRebootSystem == true)//系统因为摄像头断流的原因要重启。析出开局指令不转发
            {
                Message msgLog1 = Message.obtain();
                msgLog1.what = CameraPublishActivity.MessageType.msgOutputLog.ordinal();
                msgLog1.obj = "It is detected that the device needs to be restarted. Do not forward the opening order";
                if (mHandler != null) mHandler.sendMessage(msgLog1);
            }
            else
                if(mComPort != null) mComPort.SendData(sock_data, len);
        }

        //让主线程处理
        if( cmd_value == 0x88
                || cmd_value ==0x90
                || cmd_value ==0x93
                || cmd_value ==0x99
                || cmd_value == 0x31)
        {
            Message message = Message.obtain();
            message.what = CameraPublishActivity.MessageType.msgNetworkData.ordinal();
            message.arg1 = len;
            message.obj = sock_data;
            if (mHandler != null) mHandler.sendMessage(message);
        }
    }

    public Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            //outputInfo("Handle:" + msg.what);
            if (msg.what >= MessageType.values().length)
                return;

            MessageType mt = MessageType.values()[msg.what];
            //outputInfo("Enum is" + mt.toString());

            switch (mt) {
                case msgDelayRunInit:
                    {
                       if (mComPort == null) {
                            mComPort = new ComPort(mHandler);
                        }
                        if(mComPort!=null)
                            mComPort.Start();

                        if( android_mainboard_type ==0) {
                            gpio7130.GPIO_SetValue(1);
                        }
                        else if(android_mainboard_type == 1) {
                            gpioPB5.setDirectionValue(Gpio3288.TYPE_DIRECTION_OUT, Gpio3288.TYPE_VALUE_HIGH);
                        }
                        if (getLocalIpAddress().equals("")) {
                            mHandler.sendEmptyMessage(MessageType.msgWaitIP.ordinal());//网卡尚未就绪 IP地址没有获取。等待IP就绪
                        } else {
                            mHandler.sendEmptyMessage(MessageType.msgIpGOT.ordinal());
                        }

                        mHandler.sendEmptyMessage(MessageType.msgCheckWawajiReady.ordinal());//循环检查娃娃机是否就绪

                        if( VideoConfig.instance.is_need_local_recorder )
                        {
                            List<String> ss = getAllExternalSdcardPath();
                            if( ss.size() <=0 )
                            {
                                sdCardPath = "";
                                initRecordUI( sdCardPath, 0);
                            }
                            else
                            {
                                sdCardPath = ss.get(0);
                                int frontCount = GetRecFileList( sdCardPath + fronDirName );
                                int backCount = GetRecFileList( sdCardPath + backDirName );

                                //检查可用空间 和已有文件大小是否满足要求。不满足，则置空。因为会频繁触发文件检查 这是不允许的
                                if( frontCount + backCount <200 && getSDFreesSpace(sdCardPath)<300)
                                {
                                    if(CameraPublishActivity.DEBUG)  Log.e(TAG, "U盘即使删除文件也无法满足临界要求。不存储");
                                    Toast.makeText(getApplicationContext(), "U盘即使删除文件也无法满足临界要求。不存储", Toast.LENGTH_SHORT).show();
                                    sdCardPath= "";
                                    initRecordUI("",0);
                                }
                                else
                                    initRecordUI(ss.get(0), frontCount + backCount);
                            }

                            if (checkSpaceThread == null) {
                                outputInfo("开始空间检查", false);
                                checkSpaceThread = new CheckSpaceThread(mHandler, sdCardPath);//空循环等待 没事
                                checkSpaceThread.start();
                            }else
                            {
                                checkSpaceThread.Check( sdCardPath );
                            }
                        }
                    }
                    break;
                case msgWaitIP: {
                    if (getLocalIpAddress().equals("")) {
                        Toast.makeText(getApplicationContext(), "IP未就绪。等待IP", Toast.LENGTH_SHORT).show();
                        mHandler.sendEmptyMessageDelayed(MessageType.msgWaitIP.ordinal(), 3000);
                    } else {
                        mHandler.sendEmptyMessage(MessageType.msgIpGOT.ordinal());
                    }
                }
                break;
                case msgIpGOT: {
                    outputInfo("IP已就绪。配置线程运行。开始检查时间", false);
                    //Ip已获取。更新界面
                    VideoConfig.instance.hostIP = getLocalIpAddress();

                    findViewById(R.id.my_ip_addr).setEnabled(true);
                    EditText eti = (EditText) findViewById(R.id.my_ip_addr);
                    eti.setText(VideoConfig.instance.hostIP);

                    //监听本地局域网端口
                    lis_server = new MyTCServer();
                    lis_server.init();

                    //连接配置服务器
                    confiThread = new SockConfig();
                    {
                        if (VideoConfig.instance.enableConfigServer == true)
                            confiThread.StartWokring(mHandler, VideoConfig.instance.configHost, VideoConfig.instance.GetConfigPort());
                        else
                            confiThread.StartWokring(mHandler, "", 0);
                    }

                    //开始检查时间
                    mHandler.sendEmptyMessage(MessageType.msgCheckTime.ordinal());

                    //IP已获取到，给娃娃机设置本机IP
                    if (isWawajiReady) {
                        ComParamSet(true, true, true);//给串口发 MAC 本机IP 密码

                        //连接应用服务器
                        if (sendThread == null) {
                            outputInfo("Ready - start connecting to the application server.", false);
                            sendThread = new SockAPP();//空循环等待 没事
                            sendThread.StartWokring(mHandler, VideoConfig.instance.destHost, VideoConfig.instance.GetAppPort());
                        }

                        //本地无配置服务器地址配置 先跟串口要
                        /*if (VideoConfig.instance.configHost.equals("") || VideoConfig.instance.GetConfigPort() == 0) {
                            send_com_data(0x3c);//跟串口要IP和端口 要到以后 如果合法 它会自己开始连接并心跳
                        }*/
                    }
                }
                break;
                case msgCheckTime://空循环检查时间是否准备就绪 才能开始推
                {
                    Calendar c = Calendar.getInstance();
                    int year = c.get(Calendar.YEAR);
                    if (year < 2018) {
                        isTimeReady = false;
                        timeWaitCount--;
                        Toast.makeText(getApplicationContext(), "Not ready. Wait. Otherwise the preview will be stuck. Remaining" + timeWaitCount + "Forced flow after the second", Toast.LENGTH_SHORT).show();
                        if (timeWaitCount <= 0) {
                            isTimeReady = true;
                            //延迟2秒后开始预览
                            mHandler.sendEmptyMessageDelayed(MessageType.msgOnTimeOK.ordinal(), 2000);
                        } else
                            mHandler.sendEmptyMessageDelayed(MessageType.msgCheckTime.ordinal(), 6000);

                    } else {
                        isTimeReady = true;
                        //延迟2秒后开始预览
                        mHandler.sendEmptyMessageDelayed(MessageType.msgOnTimeOK.ordinal(), 2000);//因为联网取到时间以后会导致预览卡顿。（安卓系统的问题。已通过开相机拔插网线验证）所以，如果是先开机，后网络可用的情况，直接重启。不然就会因为bug推流失败
                    }
                }
                break;
                case msgOnTimeOK: {
                    outputInfo("Ready.", false);

                    //调用摄像头 初始化预览
                    Camera front_camera = GetCameraObj(FRONT);
                    if (front_camera != null && mSurfaceHolderFront != null) {
                        front_camera.stopPreview();
                        initCamera(FRONT, mSurfaceHolderFront);
                    }
                    if(front_camera != null)
                    {
                        Camera.Parameters parameters;
                        try {
                            parameters = front_camera.getParameters();

                            VideoConfig.instance.defaultStaturation = Integer.parseInt( parameters.get("staturation" ) );
                            VideoConfig.instance.defaultContrast = Integer.parseInt( parameters.get("contrast" ) );
                            VideoConfig.instance.defaultBrightness = Integer.parseInt( parameters.get("brightness" ) );

                            parameters.flatten();
                            front_camera.setParameters(parameters);
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }

                    Camera back_camera = GetCameraObj(BACK);

                    if (back_camera != null && mSurfaceHolderBack != null) {
                        back_camera.stopPreview();
                        initCamera(BACK, mSurfaceHolderBack);
                    }

                    if(back_camera != null)
                    {
                        Camera.Parameters parameters;
                        try {
                            parameters = back_camera.getParameters();
                            VideoConfig.instance.defaultStaturation = Integer.parseInt( parameters.get("staturation" ) );
                            VideoConfig.instance.defaultContrast = Integer.parseInt( parameters.get("contrast" ) );
                            VideoConfig.instance.defaultBrightness = Integer.parseInt( parameters.get("brightness" ) );

                            parameters.flatten();
                            back_camera.setParameters(parameters);
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }

                    if( VideoConfig.instance.usingCustomConfig == false )
                    {
                        VideoConfig.instance.staturation = VideoConfig.instance.defaultStaturation;
                        VideoConfig.instance.contrast = VideoConfig.instance.defaultContrast;
                        VideoConfig.instance.brightness =  VideoConfig.instance.defaultBrightness;
                    }

                    //先更新界面
                    updateCamUI();

                    //延迟开始推流
                    mHandler.sendEmptyMessageDelayed(MessageType.msgDelayPush.ordinal(), 2000);
                }
                break;
                case msgDelayPush: {
                    //时间就绪。这时候预览已经开始了。可以开启检查线程
                    mHandler.sendEmptyMessage(MessageType.msgCheckPreview.ordinal());//每隔5秒 就判断isFrontCameraPreviewOK isBackCameraPreviewOK 是否是真。如果是真，则

                    UpdateConfigToUI();//有可能拿到了新的应用服务器端口地址。所以更新UI

                    //开始推流
                    Log.e("@@@####","msgDelayPush");
                    UIClickStartPush();
                }
                break;
                case msgCheckWawajiReady://每隔一秒给娃娃机发状态查询指令 看看它是否就绪
                {
                    if (isWawajiReady == false) {
                        outputInfo("正在发送0x34检查娃娃机是否就绪", false);
                        //Log.e(TAG, "正在发送0x34检查娃娃机是否就绪");
                        send_com_data(0x34);
                        mHandler.sendEmptyMessageDelayed(MessageType.msgCheckWawajiReady.ordinal(), 1000);
                    }
                }
                break;
                case msgNetworkData://收到socket过来的消息
                {
                    int msg_len = msg.arg1;
                    byte test_data[] = (byte[]) (msg.obj);

                    int net_cmd = (test_data[7] & 0xff);
                    if (net_cmd == 0x88)//收到要求重启命令
                    {
                        if (sendThread != null) {
                            sendThread.StopNow();
                            sendThread = null;
                        }

                        if(CameraPublishActivity.DEBUG)  Log.e(TAG, "收到重启指令，立刻重启");

                        Intent intent = new Intent();
                        intent.setAction("ACTION_RK_REBOOT");
                        sendBroadcast(intent, null);

                    }
                    else if( net_cmd == 0x93 )//收到停止推流或码率变更命令
                    {
                        int onOff = (test_data[8] & 0xff);
                        int newBitRate = (test_data[10] & 0xff) * 256 + (test_data[9] & 0xff);

                        //先回应成功。如果不成功在发失败
                        byte[] abc = make_cmd(0x93,  test_data[8],test_data[9], test_data[10], 1 );
                        if (sendThread != null) {
                            sendThread.sendMsg(abc);
                        }

                        if( onOff == 0){
                            if( android_mainboard_type ==0) {
                                gpio7130.GPIO_SetValue(0);
                            }
                            else if(android_mainboard_type == 1) {
                                gpioPB5.setDirectionValue(Gpio3288.TYPE_DIRECTION_OUT, Gpio3288.TYPE_VALUE_LOW);
                            }
                            UIClickStopPush();
                        }
                        else if(onOff == 1)
                        {
                            if( android_mainboard_type ==0)
                            {
                                gpio7130.GPIO_SetValue(1);
                            }else if( android_mainboard_type ==1){
                                gpioPB5.setDirectionValue(Gpio3288.TYPE_DIRECTION_OUT, Gpio3288.TYPE_VALUE_HIGH);
                            }

                            /*0x93逻辑
                            收到1，如果当前没在推流，则检查bitrate，不一致则保存并更新。然后开推。
                            如果当前已在推流，检查bitrate。=0或与当前一致，啥也不做。 仅当不等于0且不与当前一致时，保存并更新。停推。开推。
                            收到0，直接停推*/
                            if( isPushing== false ) {
                                if(newBitRate != 0) {
                                    if( VideoConfig.instance.encoderKpbs!= newBitRate) {
                                        VideoConfig.instance.encoderKpbs = newBitRate;
                                        //更新界面
                                        EditText etib = findViewById(R.id.push_bitrate);
                                        etib.setText(Integer.toString(VideoConfig.instance.encoderKpbs));
                                    }
                                }
                                UIClickStartPush();
                            }else if( isPushing == true)
                            {
                                if( newBitRate != 0 && newBitRate != VideoConfig.instance.encoderKpbs)
                                {
                                    VideoConfig.instance.encoderKpbs = newBitRate;
                                    //更新界面
                                    EditText etib = findViewById(R.id.push_bitrate);
                                    etib.setText(Integer.toString(VideoConfig.instance.encoderKpbs));

                                    UIClickStopPush();
                                    UIClickStartPush();
                                }
                            }
                        }
                    }
                    else if(net_cmd == 0x99) {//todo remove for debug use.不接娃娃机时的临时实现。用来模仿游戏结束的。此处用来停止录像。意思是接收到游戏结束后停止录像
                        outputInfo("结束，停止录像", false);
                        stopRecorder();
                        isRecording = false;

                        //更新界面
                        if( sdCardPath.equals("") == false)
                        {
                            int frontCount = GetRecFileList( sdCardPath + fronDirName );
                            int backCount = GetRecFileList( sdCardPath + backDirName );
                            initRecordUI(sdCardPath, frontCount + backCount);
                        }
                    }
                    else if( net_cmd == 0x31)//开局指令 检查是否需要录像
                    {
                        outputInfo("开局，开始录像", false);
                        if( checkSpaceThread != null)
                        {
                            checkSpaceThread.Check( sdCardPath );
                        }

                        if( VideoConfig.instance.is_need_local_recorder)
                            BeginRecord();
                    }
                }
                break;
                case msgOutputLog:
                    {
                        String ss = msg.obj.toString();
                        outputInfo(ss, false);
                    }
                    break;
                case msgOutputDetialLog:
                    {
                        String ss = msg.obj.toString();
                        outputDetailLog(ss);
                    }
                    break;
                case msgConfigData: {
                    //收到配置口过来的数据
                    outputInfo("应用更改.", false);
                    UpdateConfigToUI();
                    UIClickStopPush();

                    //SaveConfigHostInfoToCom();

                    if (sendThread != null)
                        sendThread.ApplyNewServer(VideoConfig.instance.destHost, VideoConfig.instance.GetAppPort());

                    if (confiThread != null) {
                        if (VideoConfig.instance.enableConfigServer == true)
                            confiThread.ApplyNewServer(VideoConfig.instance.configHost, VideoConfig.instance.GetConfigPort());
                        else
                            confiThread.ApplyNewServer("", 0);
                    }

                    boolean is_applyok = true;
                    Camera front_camera = GetCameraObj(FRONT);
                    if (front_camera != null && mSurfaceHolderFront != null) {
                        front_camera.stopPreview();
                        is_applyok = initCamera(FRONT, mSurfaceHolderFront);
                    }

                    Camera back_camera = GetCameraObj(BACK);
                    if (back_camera != null && mSurfaceHolderBack != null) {
                        back_camera.stopPreview();
                        if (is_applyok)
                            is_applyok = initCamera(BACK, mSurfaceHolderBack);
                    }

                    //当两个摄像头都不存在 并且是预设分辨率时 视为失败。不要问为什么。和小林商量的时候确定的
                    if (front_camera == null && back_camera == null && VideoConfig.instance.GetResolutionIndex() == -1)
                        is_applyok = false;

                    Socket ssa = (Socket) msg.obj;
                    if (is_applyok == false) {
                        RestoreConfigAndUpdateVideoUI();
                        Toast.makeText(getApplicationContext(), "无效的视频配置，已恢复原状!", Toast.LENGTH_SHORT).show();
                        mHandler.sendEmptyMessageDelayed(MessageType.msgDelay2sPush.ordinal(), 2000);

                        try {
                            if (ssa != null && ssa.isConnected()) {
                                String s = "{\"result\":\"failed\"}";//将结果发回发送端
                                OutputStream outputStream = ssa.getOutputStream();
                                outputStream.write(s.getBytes(), 0, s.getBytes().length);
                                outputStream.flush();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            if(CameraPublishActivity.DEBUG)  Log.e("返回结果", "失败");
                        }
                    } else {
                        try {
                            if (ssa != null && ssa.isConnected()) {
                                String s = "{\"result\":\"ok\"}";//将结果发回发送端
                                OutputStream outputStream = ssa.getOutputStream();
                                outputStream.write(s.getBytes(), 0, s.getBytes().length);
                                outputStream.flush();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            if(CameraPublishActivity.DEBUG)   Log.e("返回结果", "失败");
                        }
                        mHandler.sendEmptyMessageDelayed(MessageType.msgDelay2sPush.ordinal(), 2000);
                    }
                }
                break;
                case msgOnUpdate://收到更新命令
                {
                    String jsonStr = (String) (msg.obj);
                    try {
                        JSONObject jsonObject = new JSONObject(jsonStr);
                        String url = jsonObject.getString("url");

                        int versionCode = 0;
                        if (jsonObject.has("versionCode"))
                            versionCode = jsonObject.getInt("versionCode");

                        if(CameraPublishActivity.DEBUG) Log.e("收到更新命令", "url" + url + " 当前版本:" + VideoConfig.instance.appVersion);
                        SilentInstall upobj = new SilentInstall(getApplicationContext());
                        upobj.startUpdate(url);
                    } catch (JSONException jse) {
                        jse.printStackTrace();
                    }
                }
                break;
                case msgComData: //串口过来的消息
                {
                    byte com_data[] = (byte[]) (msg.obj);
                    int data_len = msg.arg1;

                    //处理从串口过来的消息。只处理相应的部分
                    int cmd_value = com_data[7]&0xff;
                    if(cmd_value == 0x17)
                    {
                        //构造mac返回给主板
                        if(com_data[6] ==0x09)
                        {
                            byte msg_content[] = new byte[21];
                            msg_content[0] = (byte) 0xfe;
                            msg_content[1] = (byte) (0);
                            msg_content[2] = (byte) (0);
                            msg_content[3] = (byte) ~msg_content[0];
                            msg_content[4] = (byte) ~msg_content[1];
                            msg_content[5] = (byte) ~msg_content[2];
                            msg_content[6] = (byte) (msg_content.length);
                            msg_content[7] = (byte) 0x17;
                            String strMAC = VideoConfig.instance.getMac();
                            System.arraycopy(strMAC.getBytes(), 0, msg_content, 8, strMAC.getBytes().length);
                            int total_c = 0;
                            for (int i = 6; i < msg_content.length - 1; i++) {
                                total_c += (msg_content[i] & 0xff);
                            }
                            msg_content[msg_content.length - 1] = (byte) (total_c % 100);
                            if(mComPort!=null) mComPort.SendData(msg_content, msg_content.length);
                            String sss = SockAPP.bytesToHexString(msg_content);
                            outputInfo("收到0x17.MaC发往串口" + sss, false);
                        }
                    }
                    else if( cmd_value == 0x33)
                    {
                        outputInfo("结束，停止录像", false);
                        stopRecorder();
                        isRecording = false;

                        //更新界面
                        if( sdCardPath!= null && sdCardPath.equals("") == false)
                        {
                            int frontCount = GetRecFileList( sdCardPath + fronDirName );
                            int backCount = GetRecFileList( sdCardPath + backDirName );
                            initRecordUI(sdCardPath, frontCount + backCount);
                        }
                    }

                    if (cmd_value ==   0x35 || cmd_value == 0x34) {
                        if (cmd_value ==  0x34 && com_data[6] == (byte) 0x0e && isShouldRebootSystem == true) {
                            wawajiCurrentState = com_data[8] & 0xff;
                            //fe 00 00 01 ff ff 0e 34 num1 num2 num3 num4 num5 [校验位1] Num1表示机台状态0，1，2是正常状态，其它看 ** [通知]故障上报 **
                            if (com_data[8] == 1 || com_data[8] == 2)//要求重启的时候，娃娃机正在有人玩.啥事也不做。等待
                            {

                            } else {
                                if (sendThread != null) {
                                    sendThread.StopNow();
                                    sendThread = null;
                                }

                                if(CameraPublishActivity.DEBUG)  Log.e(TAG, "娃娃机已满足重启要求。立刻重启");

                                Log.e(TAG, "娃娃机已满足重启要求。立刻重启");
                                Intent intent = new Intent();
                                intent.setAction("ACTION_RK_REBOOT");
                                sendBroadcast(intent, null);
                            }
                        }

                        if (isWawajiReady == false)//娃娃机就绪。检查是否需要跟娃娃机获取应用服务器端口 如果不用。则直接生成连接应用服务器的对象
                        {
                            isWawajiReady = true;
                            outputInfo("娃娃机已就绪.", false);

                            //连接应用服务器
                            if (sendThread == null) {
                                outputInfo("娃机就绪之-开始连接应用服务器.", false);
                                sendThread = new SockAPP();//空循环等待 没事
                                sendThread.StartWokring(mHandler, VideoConfig.instance.destHost, VideoConfig.instance.GetAppPort());
                            }

                            //本地无配置服务器地址配置 先跟串口要
                            /*if (VideoConfig.instance.configHost.equals("") || VideoConfig.instance.GetConfigPort() == 0) {
                                send_com_data(0x3c);//跟串口要IP和端口 要到以后 如果合法 它会自己开始连接并心跳
                            }*/

                            //先发MAC。然后检查ip是否可用。如果有，也要发给他。
                            ComParamSet(true, false, true);//给串口发 MAC 密码
                            if (getLocalIpAddress().equals("") == false) {
                                ComParamSet(false, true, false);//给串口发本机IP
                            }
                        }

                        //wawaji is alive
                    }
                }
                break;
                case msgUDiskMount://插U盘
                {
                    String UPath = (String) (msg.obj);
                    if(CameraPublishActivity.DEBUG)  Log.e(TAG, "msgUDiskMount...........");

                    // 获取sd卡的对应的存储目录
                    //获取指定文件对应的输入流
                    try {
                        sdCardPath = UPath;
                        int frontCount = GetRecFileList( sdCardPath + fronDirName );
                        int backCount = GetRecFileList( sdCardPath + backDirName );

                        //检查可用空间 和已有文件大小是否满足要求。不满足，则置空。因为会频繁触发文件检查 这是不允许的
                        if( frontCount + backCount <200 && getSDFreesSpace(sdCardPath)<300)
                        {
                            if(CameraPublishActivity.DEBUG)  Log.e(TAG, "U盘即使删除文件也无法满足临界要求。不存储");
                            Toast.makeText(getApplicationContext(), "U盘即使删除文件也无法满足临界要求。不存储", Toast.LENGTH_SHORT).show();
                            sdCardPath= "";
                            initRecordUI("",0);
                        }
                        else
                            initRecordUI(sdCardPath, frontCount + backCount);

                        if( USB_WIFI_CONFIG_ENABLE )
                        {
                            FileInputStream fis = new FileInputStream(UPath + "/config.txt");
                            //将指定输入流包装成 BufferedReader
                            BufferedReader br = new BufferedReader(new InputStreamReader(fis, "GBK"));

                            StringBuilder sb = new StringBuilder("");
                            String line = null;
                            //循环读取文件内容
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }

                            //关闭资源
                            br.close();
                            if(CameraPublishActivity.DEBUG)  Log.e("file content", sb.toString());

                            try {
                                JSONObject jsonOBJ = new JSONObject(sb.toString());
                                if (jsonOBJ.has("wifiSSID")) {
                                    String wifiSSID = jsonOBJ.getString("wifiSSID");

                                    String wifiPassword = "";
                                    if (jsonOBJ.has("wifiPassword"))
                                        wifiPassword = jsonOBJ.getString("wifiPassword");

                                    //启用wifi
                                    if (!wifiManager.isWifiEnabled())
                                        wifiManager.setWifiEnabled(true);

                                    //连接特定的wifi
                                    int ntype = wifiPassword.equals("") ? 1 : 3;
                                    WifiAutoConnectManager.WifiCipherType ntr = wifiPassword.equals("") ?
                                            WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS : WifiAutoConnectManager.WifiCipherType.WIFICIPHER_WPA;

                                    if(CameraPublishActivity.DEBUG) Log.e("连接wifi", "ssid" + wifiSSID + " pwd " + wifiPassword + "type" + ntype);
                                    //WifiUtil.createWifiInfo(wifiSSID, wifiPassword, ntype, wifiManager);

                                    wifiauto.connect(wifiSSID, wifiPassword, ntr);
                                }

                                boolean apply_ret = VideoConfig.instance.ApplyConfig(sb.toString(), null);

                            } catch (Exception e) {
                                e.printStackTrace();
                                if(CameraPublishActivity.DEBUG)  Log.e("u盘配置文件错误", "Json file Error.");
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
                case msgUDiskUnMount:
                    {
                        if( isRecording )
                            stopRecorder();

                        sdCardPath = "";

                        initRecordUI( sdCardPath, 0);
                    }
                    break;
                case msgUpdateFreeSpace:
                    {
                        //更新界面
                        if( sdCardPath.equals("") == false)
                        {
                            int frontCount = GetRecFileList( sdCardPath + fronDirName );
                            int backCount = GetRecFileList( sdCardPath + backDirName );
                            initRecordUI(sdCardPath, frontCount + backCount);
                        }
                    }
                    break;
                case msgCheckPreview: {
                    //if( isWawajiReady == false)
                    //Log.i(TAG, "CheckingPreview");

                    if (mCameraFront != null) {
                       //outputInfo("isFrontCameraPreviewOK" + isFrontCameraPreviewOK, false);

                        if (isFrontCameraPreviewOK)
                            isFrontCameraPreviewOK = false;
                        else if (isFrontCameraPreviewOK == false) {
                            if (isShouldRebootSystem == false) {
                                isShouldRebootSystem = true;
                                wawajiCurrentState = -1;
                                mHandler.sendEmptyMessageDelayed(MessageType.msgCheckWawaNowState.ordinal(), 3000);

                                int backCamValue = 0;
                                if (mCameraBack == null) backCamValue = 2;
                                else if (isBackCameraPreviewOK == false)
                                    backCamValue = 1;

                                byte[] abc = make_cmd(0x89, 01, backCamValue, 0, 0, 0, 0);//4位空的预留 0x89命令码 前置状态  后置状态 4位预留。 其中：状态为:00 正常 1 使用中掉线 2摄像头缺失
                                if (sendThread != null) {
                                    sendThread.sendMsg(abc);
                                }

                                outputInfo("前置摄像头有效性错误。设备需要重启。", false);
                                if(CameraPublishActivity.DEBUG)  Log.e(TAG, "前置摄像头有效性错误。设备需要重启。");
                                mHandler.sendEmptyMessage(MessageType.msgQueryWawajiState.ordinal());
                            }
                        }
                    }

                    if (mCameraBack != null) {
                        //outputInfo("isBackCameraPreviewOK" + isBackCameraPreviewOK, false);

                        if (isBackCameraPreviewOK)
                            isBackCameraPreviewOK = false;
                        else if (isBackCameraPreviewOK == false) {
                            if (isShouldRebootSystem == false) {
                                //isShouldRebootSystem = true; //0904去掉了后置需要重启的步骤 只需报告错误即可
                                //wawajiCurrentState = -1;//0904去掉了后置需要重启的步骤 只需报告错误即可
                                //mHandler.sendEmptyMessageDelayed(MessageType.msgCheckWawaNowState.ordinal(), 3000);

                                int frontCamValue = 0;
                                if (mCameraFront == null) frontCamValue = 2;
                                else if (isFrontCameraPreviewOK == false)
                                    frontCamValue = 1;

                                byte[] abc = make_cmd(0x89, frontCamValue, 01, 1, 0, 0, 0);//4位空的预留
                                if (sendThread != null) {
                                    sendThread.sendMsg(abc);
                                }
                                mCameraBack = null;
                                CameraPublishActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        TextView tvFr = findViewById(R.id.cam2_url_tip);
                                        if (tvFr != null) tvFr.setTextColor(Color.rgb(0, 0, 0));
                                        getWindow().getDecorView().postInvalidate();
                                    }
                                });

                                //outputInfo("后置摄像头有效性错误。报故障。", false);//0904去掉了后置需要重启的步骤 只需报告错误即可
                                //if(CameraPublishActivity.DEBUG) Log.e(TAG, "后置摄像头有效性错误。设备需要重启。");//0904去掉了后置需要重启的步骤 只需报告错误即可
                               // mHandler.sendEmptyMessage(MessageType.msgQueryWawajiState.ordinal());//0904去掉了后置需要重启的步骤 只需报告错误即可
                            }
                        }
                    }

                    mHandler.sendEmptyMessageDelayed(MessageType.msgCheckPreview.ordinal(), 5000);
                }
                break;
                case msgCheckWawaNowState: {
                    if (wawajiCurrentState != 1 && wawajiCurrentState != 2) {
                        if(CameraPublishActivity.DEBUG) Log.e(TAG, "娃娃机状态已满足重启要求，立刻重启");

                        Intent intent = new Intent();
                        intent.setAction("ACTION_RK_REBOOT");
                        sendBroadcast(intent, null);
                    }
                }
                break;
                case msgQueryWawajiState: {
                    //queryStateTimeoutTime++;
                    send_com_data(0x34);
                    mHandler.sendEmptyMessageDelayed(MessageType.msgQueryWawajiState.ordinal(), 1000);
						/*if( queryStateTimeoutTime >= 10)
						{
							Log.e(TAG,"状态查询超时10次，立刻重启" );

							Intent intent=new Intent();
							intent.setAction("ACTION_RK_REBOOT");
							sendBroadcast(intent,null);
						}*/
                }
                break;
                case msgApplyCamparam:
                    {
                        ApplyCam3Params();
                        VideoConfig.instance.usingCustomConfig = true;
                    }
                    break;
                case msgRestoreCamparam:
                    {
                        VideoConfig.instance.usingCustomConfig = false;
                        VideoConfig.instance.staturation =  VideoConfig.instance.defaultStaturation;
                        VideoConfig.instance.contrast =  VideoConfig.instance.defaultContrast;
                        VideoConfig.instance.brightness =  VideoConfig.instance.defaultBrightness;
                        ApplyCam3Params();
                        updateCamUI();
                    }
                    break;
                case msgQueryNetworkState:
                    {
                        //构造0x92发送
                        byte[] confirm_data = make_cmd(0x92);
                        if(sendThread != null)
                            sendThread.sendMsg( confirm_data );

                        network_query_issend = true;

                        //直接起个计时器。正常来说只要触发读超时，那就没什么好讲了。直接重连都行
                        new Timer().schedule(new TimerTask() {
                            public void run() {
                                if( network_query_issend )
                                {
                                    network_query_issend = false;
                                    if( network_query_isrecv == false)
                                    {
                                        Log.e(TAG, "网络检测超时。断网了。启用重连机制.");
                                        if(sendThread != null) sendThread.CloseFireRetry();
                                    }
                                    else
                                        {
                                            network_query_isrecv = false;
                                        }
                                }
                            }
                        }, 3000 );
                    }
                    break;
                case msgRestartH5:
                    {
                       // Log.e(TAG,"check state result" + h5_video1_push_state + "2:" + h5_video2_push_state);
                        begin_check_h5 = false;//停止检测状态

                            if( h5_video1_push_state != 0 || (h5_video2_push_state != 0 && VideoConfig.instance.url2.equals("")==false))
                            {
                                Log.e(TAG,"断网了，全部都要重推流");

                                if( h5_video1_push_state!=0)
                                {
                                    TextView tvFr = findViewById(R.id.cam1_url_tip);
                                    if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                    getWindow().getDecorView().postInvalidate();
                                }

                                if( h5_video2_push_state!=0)
                                {
                                    TextView tvFr = findViewById(R.id.cam2_url_tip);
                                    if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                    getWindow().getDecorView().postInvalidate();
                                }

                                //在主线程里面重新推流
                                Log.e(TAG,"UUUUUUUUUUUUUUmsgRestartH5" );
                                UIClickStopPush();

                                mHandler.sendEmptyMessageDelayed(MessageType.msgDelay2sPush.ordinal(), 2000);
                            }


                       // if( isPushing )
                        //{
                       //     int reto = ffmpegH.initVideo1(VideoConfig.instance.url1);
                       //     Log.e(TAG,"1重推结果" + reto);
                       // }
                    }
                    break;

                case msgDelayClose:
                    {
                        ffmpegH.close1();
                        ffmpegH.close2();
                        ffmpegH = null;
                    }
                    break;
                case msgDelay2sPush:
                    {
                        UIClickStartPush();

                        if( ffmpegH == null )
                            mHandler.sendEmptyMessageDelayed(MessageType.msgDelay2sPush.ordinal(), 2000);
                    }
                    break;
            }
        }
    };



    void SwitchResolution(int position) {
        if (isTimeReady == false) return;
        //分辨率配置
        //"960*720", "640*480","640*360", "352*288","320*240"
        if(CameraPublishActivity.DEBUG) Log.i(TAG, "Current Resolution position: " + position);

        VideoConfig.instance.SetResolutionIndex(position);

        switch (position) {
            case 0: {
                VideoConfig.instance.SetVideoWidth(960);
                VideoConfig.instance.SetVideoHeight(720);
            }
            break;
            case 1:
                VideoConfig.instance.SetVideoWidth(640);
                VideoConfig.instance.SetVideoHeight(480);
                break;
            case 2:
                VideoConfig.instance.SetVideoWidth(640);
                VideoConfig.instance.SetVideoHeight(360);
                break;
            case 3:
                VideoConfig.instance.SetVideoWidth(352);
                VideoConfig.instance.SetVideoHeight(288);
                break;
            case 4:
                VideoConfig.instance.SetVideoWidth(320);
                VideoConfig.instance.SetVideoHeight(240);
                break;
            case 5: {
                VideoConfig.instance.SetVideoWidth(555);
                VideoConfig.instance.SetVideoHeight(555);
            }
            break;
            default:
                VideoConfig.instance.SetVideoWidth(640);
                VideoConfig.instance.SetVideoHeight(360);
        }

        boolean is_applyok = true;
        Camera front_camera = GetCameraObj(FRONT);
        if (front_camera != null && mSurfaceHolderFront != null) {
            front_camera.stopPreview();
            is_applyok = initCamera(FRONT, mSurfaceHolderFront);
        }

        Camera back_camera = GetCameraObj(BACK);
        if (back_camera != null && mSurfaceHolderBack != null) {
            back_camera.stopPreview();
            if (is_applyok != false)
                is_applyok = initCamera(BACK, mSurfaceHolderBack);
        }

        //当两个摄像头都不存在 并且是预设分辨率时 视为失败。不要问为什么。和小林商量的时候确定的
        if (front_camera == null && back_camera == null && VideoConfig.instance.GetResolutionIndex() == -1)
            is_applyok = false;

        if (is_applyok == false) {
            Toast.makeText(getApplicationContext(), "错误的配置,回退到正确的配置", Toast.LENGTH_SHORT).show();

            RestoreConfigAndUpdateVideoUI();
        }
    }

    void RestoreConfigAndUpdateVideoUI() {
        VideoConfig.instance.RestoreLastVideoSizeAndIndex(this);

        if (VideoConfig.instance.GetResolutionIndex() != -1) {
            CheckBox cbPrefernce = findViewById(R.id.checkUsePrefence);
            cbPrefernce.setChecked(true);

            findViewById(R.id.resolutionSelctor).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_w_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_wideo_w).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_h_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_wideo_h).setVisibility(View.INVISIBLE);
            resolutionSelector.setSelection(VideoConfig.instance.GetResolutionIndex());
        } else {
            CheckBox cbPrefernce = findViewById(R.id.checkUsePrefence);
            cbPrefernce.setChecked(false);
            findViewById(R.id.resolutionSelctor).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_w_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_wideo_w).setVisibility(View.VISIBLE);
            EditText eti = (EditText) findViewById(R.id.custum_wideo_w);
            eti.setText(Integer.toString(VideoConfig.instance.GetVideoWidth()));

            findViewById(R.id.custum_h_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_wideo_h).setVisibility(View.VISIBLE);
            eti = (EditText) findViewById(R.id.custum_wideo_h);
            eti.setText(Integer.toString(VideoConfig.instance.GetVideoHeight()));
        }
    }

    //Configure recorder related function.
    void ConfigRecorderFuntion(String rec, long handle, boolean isNeedLocalRecorder) {
        if (libPublisher != null) {
            if (isNeedLocalRecorder) {
                if (rec != null && !rec.isEmpty()) {
                    int ret = libPublisher.SmartPublisherCreateFileDirectory(rec);
                    if (0 == ret) {
                        if (0 != libPublisher.SmartPublisherSetRecorderDirectory(handle, rec)) {
                            if(CameraPublishActivity.DEBUG)  Log.e(TAG, "Set recoder dir failed , path:" + rec);
                            return;
                        }

                        if (0 != libPublisher.SmartPublisherSetRecorder(handle, 1)) {
                            if(CameraPublishActivity.DEBUG)  Log.e(TAG, "SmartPublisherSetRecoder failed.");
                            return;
                        }

                        if (0 != libPublisher.SmartPublisherSetRecorderFileMaxSize(handle, 200)) {
                            if(CameraPublishActivity.DEBUG) Log.e(TAG, "SmartPublisherSetRecoderFileMaxSize failed.");
                            return;
                        }

                    } else {
                        if(CameraPublishActivity.DEBUG) Log.e(TAG, "Create recoder dir failed, path:" + rec);
                    }
                }
            } else {
                if (0 != libPublisher.SmartPublisherSetRecorder(handle, 0)) {
                    if(CameraPublishActivity.DEBUG)  Log.e(TAG, "SmartPublisherSetRecoder failed.");
                    return;
                }
            }
        }
    }

    class ButtonHardwareEncoderListener implements OnClickListener {
        public void onClick(View v) {
            VideoConfig.instance.is_hardware_encoder = !VideoConfig.instance.is_hardware_encoder;

            if (VideoConfig.instance.is_hardware_encoder) {
                btnHWencoder.setText("当前硬编码");
                //显示软编码选项
                findViewById(R.id.swVideoEncoderProfileSelector).setVisibility(View.INVISIBLE);
                findViewById(R.id.speed_tip).setVisibility(View.INVISIBLE);
                findViewById(R.id.sw_video_encoder_speed_selctor).setVisibility(View.INVISIBLE);
            } else {
                btnHWencoder.setText("当前软编码");
                //显示软编码选项
                findViewById(R.id.swVideoEncoderProfileSelector).setVisibility(View.VISIBLE);
                findViewById(R.id.speed_tip).setVisibility(View.VISIBLE);
                findViewById(R.id.sw_video_encoder_speed_selctor).setVisibility(View.VISIBLE);
            }
        }
    }

    void NotifyStreamResult(int CameraType, PushState nowPS)//当推流状态变化时，通知服务器端
    {
        /*if (CameraType == 0) {
            if (nowPS == pst_front)
                return;

            pst_front = nowPS;
        } else if (CameraType == 1) {
            if (nowPS == pst_back)
                return;

            pst_back = nowPS;
        }

        byte msg_content[] = new byte[22];
        msg_content[0] = (byte) 0xfe;
        msg_content[1] = (byte) (0);
        msg_content[2] = (byte) (0);
        msg_content[3] = (byte) ~msg_content[0];
        msg_content[4] = (byte) ~msg_content[1];
        msg_content[5] = (byte) ~msg_content[2];
        msg_content[6] = (byte) (msg_content.length);
        msg_content[7] = (byte) 0xa0;

        System.arraycopy(VideoConfig.instance.getMac().getBytes(), 0, msg_content, 8, VideoConfig.instance.getMac().getBytes().length);

        if (CameraType == 0 && nowPS == PushState.FAILED) {
            msg_content[20] = 0x00;//
        } else if (CameraType == 0 && nowPS == PushState.OK) {
            msg_content[20] = 0x01;//
        } else if (CameraType == 0 && nowPS == PushState.CLOSE) {
            msg_content[20] = 0x02;//
        } else if (CameraType == 1 && nowPS == PushState.FAILED) {
            msg_content[20] = 0x10;//
        } else if (CameraType == 1 && nowPS == PushState.OK) {
            msg_content[20] = 0x11;//
        } else if (CameraType == 1 && nowPS == PushState.CLOSE) {
            msg_content[20] = 0x12;//
        }

        int total_c = 0;
        for (int i = 6; i < msg_content.length - 1; i++) {
            total_c += (msg_content[i] & 0xff);
        }
        msg_content[msg_content.length - 1] = (byte) (total_c % 100);

        if (sendThread != null) sendThread.sendMsg(msg_content);*/
    }

    class EventHandeV2 implements NTSmartEventCallbackV2 {
        @Override
        public void onNTSmartEventCallbackV2(long handle, int id, long param1, long param2, String param3, String param4, Object param5) {

            Log.d(TAG, "EventHandeV2: handle=" + handle + " id:" + id);

            switch (id) {
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_STARTED:
                    txt = "开始。。";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTING:
                    txt = "连接中。。";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTION_FAILED:
                    txt = "连接失败。。";
                    if (handle == publisherHandleFront) {
                        VideoConfig.instance.videoPushState_1 = false;
                        NotifyStreamResult(0, PushState.FAILED);
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if (handle == publisherHandleBack) {
                        NotifyStreamResult(1, PushState.FAILED);
                        VideoConfig.instance.videoPushState_2 = false;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam2_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTED:
                    txt = "连接成功。。";
                    if (handle == publisherHandleFront) {
                        NotifyStreamResult(0, PushState.OK);
                        VideoConfig.instance.videoPushState_1 = true;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(0, 255, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if (handle == publisherHandleBack) {
                        NotifyStreamResult(1, PushState.OK);
                        VideoConfig.instance.videoPushState_2 = true;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam2_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(0, 255, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_DISCONNECTED:
                    txt = "连接断开。。";
                    if (handle == publisherHandleFront) {
                        VideoConfig.instance.videoPushState_1 = false;
                        NotifyStreamResult(0, PushState.FAILED);
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if (handle == publisherHandleBack) {
                        NotifyStreamResult(1, PushState.FAILED);
                        VideoConfig.instance.videoPushState_2 = false;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam2_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_STOP:
                    txt = "关闭。。";
                    if (handle == publisherHandleFront) {
                        NotifyStreamResult(0, PushState.CLOSE);
                        VideoConfig.instance.videoPushState_1 = false;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if (handle == publisherHandleBack) {
                        NotifyStreamResult(1, PushState.CLOSE);
                        VideoConfig.instance.videoPushState_2 = false;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam2_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_RECORDER_START_NEW_FILE:
                    Log.i(TAG, "开始一个新的录像文件 : " + param3);
                    txt = "开始一个新的录像文件。。";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_ONE_RECORDER_FILE_FINISHED:
                    Log.i(TAG, "已生成一个录像文件 : " + param3);
                    txt = "已生成一个录像文件。。";
                    break;

                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_SEND_DELAY:
                    Log.i(TAG, "发送时延: " + param1 + " 帧数:" + param2);
                    txt = "收到发送时延..";
                    break;

                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CAPTURE_IMAGE:
                    Log.i(TAG, "快照: " + param1 + " 路径：" + param3);

                    if (param1 == 0) {
                        txt = "截取快照成功。.";
                    } else {
                        txt = "截取快照失败。.";
                    }
                    break;
            }

            String str = "当前回调状态：" + txt;

            Log.d(TAG, str);
        }
    }

    private void ConfigControlEnable(boolean isEnable) {
        btnHWencoder.setEnabled(isEnable);

        findViewById(R.id.id_my_name1).setEnabled(isEnable);
        findViewById(R.id.checkBoxAutoGet).setEnabled(isEnable);//dhcp
        if (isEnable) {
            if (VideoConfig.instance.using_dhcp == false) {
                findViewById(R.id.my_ip_addr).setEnabled(true);
            } else {
                findViewById(R.id.my_ip_addr).setEnabled(false);
            }
        } else {
            findViewById(R.id.my_ip_addr).setEnabled(false);
        }

        findViewById(R.id.rb_rtmp).setEnabled(isEnable);
        findViewById(R.id.rb_mpeg).setEnabled(isEnable);

        findViewById(R.id.enableConfServer).setEnabled(isEnable);
        if (isEnable) {
            if (VideoConfig.instance.enableConfigServer == false) {
                findViewById(R.id.config_server_ip).setEnabled(false);
                findViewById(R.id.config_server_port).setEnabled(false);
            } else {
                findViewById(R.id.config_server_ip).setEnabled(true);
                findViewById(R.id.config_server_port).setEnabled(true);
            }
        } else {
            findViewById(R.id.config_server_ip).setEnabled(false);
            findViewById(R.id.config_server_port).setEnabled(false);
        }

        findViewById(R.id.my_gate_addr).setEnabled(isEnable);
        findViewById(R.id.my_netmask_addr).setEnabled(isEnable);

        findViewById(R.id.checkUsePrefence).setEnabled(isEnable);
        findViewById(R.id.resolutionSelctor).setEnabled(isEnable);
        findViewById(R.id.custum_wideo_w).setEnabled(isEnable);
        findViewById(R.id.custum_wideo_h).setEnabled(isEnable);

        findViewById(R.id.swVideoEncoderProfileSelector).setEnabled(isEnable);
        findViewById(R.id.sw_video_encoder_speed_selctor).setEnabled(isEnable);
        findViewById(R.id.push_rate).setEnabled(isEnable);
        findViewById(R.id.cam1_url_edit).setEnabled(isEnable);
        findViewById(R.id.cam2_url_edit).setEnabled(isEnable);

        findViewById(R.id.push_bitrate).setEnabled(isEnable);

        findViewById(R.id.server_ip).setEnabled(isEnable);
        findViewById(R.id.server_port).setEnabled(isEnable);


        findViewById(R.id.checkRecord).setEnabled(isEnable);
        findViewById(R.id.cbIncludeAudio).setEnabled(isEnable);
    }

    private void SetConfig(long handle) {
        if (libPublisher == null)
            return;

        if (handle == 0)
            return;

        int iMute = 0;
        if( VideoConfig.instance.containAudio == true)
            iMute = 0;
        else
            iMute = 1;

        //非镜像
        libPublisher.SmartPublisherSetMirror(handle, 0);

        //静音
        libPublisher.SmartPublisherSetMute(handle,iMute);

        //设置码率
        if (VideoConfig.instance.is_hardware_encoder) {
            //设置硬编码的码率
           // int hwHWKbps = setHardwareEncoderKbps(VideoConfig.instance.GetVideoWidth(), VideoConfig.instance.GetVideoHeight());

            if(CameraPublishActivity.DEBUG)  Log.i(TAG, "Kbps: " +  VideoConfig.instance.encoderKpbs);

            int isSupportHWEncoder = libPublisher.SetSmartPublisherVideoHWEncoder(handle, VideoConfig.instance.encoderKpbs);

            if (isSupportHWEncoder == 0) {
                if(CameraPublishActivity.DEBUG)  Log.i(TAG, "Great, it supports hardware encoder!");
            }
        }else {
            //设置软编码的码率
            int maxBitRate = (int)((float)VideoConfig.instance.encoderKpbs*(float)1.5);
            int bitRateSettingRes = libPublisher.SmartPublisherSetSWVideoBitRate(handle, VideoConfig.instance.encoderKpbs,maxBitRate);
            if(bitRateSettingRes ==0)
            {
                if(CameraPublishActivity.DEBUG)  Log.i(TAG, "ok, now bitrate is " + bitRateSettingRes);
            }
        }

        //硬编码 2018.04.26-fixed.现在帧率可以起作用了
       // if (VideoConfig.instance.is_hardware_encoder) {
        libPublisher.SmartPublisherSetFPS(handle, VideoConfig.instance.GetFPS());
       // }

        libPublisher.SetSmartPublisherEventCallbackV2(handle, new EventHandeV2());

        //音频-set AAC encoder
        libPublisher.SmartPublisherSetAudioCodecType(handle, 1);
        //音频-噪音抑制
        libPublisher.SmartPublisherSetNoiseSuppression(handle, 1);
        //音频编码
        libPublisher.SmartPublisherSetAGC(handle, 0);

        libPublisher.SmartPublisherSetSWVideoEncoderProfile(handle, VideoConfig.instance.sw_video_encoder_profile);
        libPublisher.SmartPublisherSetSWVideoEncoderSpeed(handle, VideoConfig.instance.sw_video_encoder_speed);

        libPublisher.SmartPublisherSaveImageFlag(handle, 0);
        libPublisher.SmartPublisherSetClippingMode(handle, 0);
    }

    private void InitAndSetConfig() {
        int inCludeAudio = 0;
        if( VideoConfig.instance.containAudio == true)
            inCludeAudio = 1;
        else
            inCludeAudio = 0;

            Camera front_cam = GetCameraObj(FRONT);
            if(front_cam != null)
            {
                if( libPublisher== null) Log.e("###!!!@@@","handle is null");
                publisherHandleFront = libPublisher.SmartPublisherOpen(myContext,inCludeAudio, /*video_opt*/1,
                        VideoConfig.instance.GetVideoWidth(), VideoConfig.instance.GetVideoHeight());

                if (publisherHandleFront != 0) {
                    SetConfig(publisherHandleFront);
                    if(CameraPublishActivity.DEBUG) Log.e("前置摄像头", "ID" + publisherHandleFront);
                }
            }

            Camera back_cam = GetCameraObj(BACK);
            if( back_cam != null)
            {
                publisherHandleBack = libPublisher.SmartPublisherOpen(myContext, inCludeAudio, /*video_opt*/1,
                        VideoConfig.instance.GetVideoWidth(), VideoConfig.instance.GetVideoHeight());

                if (publisherHandleBack != 0) {
                    SetConfig(publisherHandleBack);
                    if(CameraPublishActivity.DEBUG)  Log.e("后置摄像头", "ID" + publisherHandleBack);
                }
            }


    }

    class NTAudioRecordV2CallbackImpl implements NTAudioRecordV2Callback
    {
        @Override
        public void onNTAudioRecordV2Frame(ByteBuffer data, int size, int sampleRate, int channel, int per_channel_sample_number)
        {

    		/* Log.e(TAG, "onNTAudioRecordV2Frame size=" + size + " sampleRate=" + sampleRate + " channel=" + channel
    				 + " per_channel_sample_number=" + per_channel_sample_number);*/

            if ( publisherHandleFront != 0 )
            {
                libPublisher.SmartPublisherOnPCMData(publisherHandleFront, data, size, sampleRate, channel, per_channel_sample_number);
            }

            if ( publisherHandleBack != 0 )
            {
                libPublisher.SmartPublisherOnPCMData(publisherHandleBack, data, size, sampleRate, channel, per_channel_sample_number);
            }
        }
    }

    void CheckInitAudioRecorder()
    {
        if( VideoConfig.instance.containAudio == false)
            return;

        if ( audioRecord_ == null )
        {
            //audioRecord_ = new NTAudioRecord(this, 1);

            audioRecord_ = new NTAudioRecordV2(this);
        }

        if( audioRecord_ != null )
        {
            if(CameraPublishActivity.DEBUG)  Log.i(TAG, "CheckInitAudioRecorder call audioRecord_.start()+++...");

            audioRecordCallback_ = new NTAudioRecordV2CallbackImpl();

            audioRecord_.AddCallback(audioRecordCallback_);

            audioRecord_.Start();

            if(CameraPublishActivity.DEBUG)  Log.i(TAG, "CheckInitAudioRecorder call audioRecord_.start()---...");


            //Log.i(TAG, "onCreate, call executeAudioRecordMethod..");
            // auido_ret: 0 ok, other failed
            //int auido_ret= audioRecord_.executeAudioRecordMethod();
            //Log.i(TAG, "onCreate, call executeAudioRecordMethod.. auido_ret=" + auido_ret);
        }
    }

    void StartH5CheckTimer()
    {
        //推流检查定时器启动条件
        //1.某路推流成功后启动。
        //2.检测失败时，停止
        if( tm_check_h5 == null) {
            tm_check_h5= new Timer();
            tm_check_h5.schedule(new TimerTask() {
                public void run() {
                    if( isPushing  && ffmpegH != null)
                    {
                        //Log.e(TAG,"checking h5 state");
                        h5_video1_push_state = -1;
                        h5_video2_push_state = -1;
                        begin_check_h5 = true;

                        //在主线程里面重新推流
                        new Timer().schedule(new TimerTask() {
                            public void run() {
                                //Log.e(TAG,"check state result" + h5_video1_push_state + "2:" + h5_video2_push_state);
                                begin_check_h5 = false;//停止检测状态
                                if( ffmpegH != null && isPushing) {
                                    if( (mCameraFront != null &&  h5_video1_push_state != 0) || (mCameraBack != null && h5_video2_push_state != 0))
                                    {
                                            //在主线程里面重新推流
                                            mHandler.sendEmptyMessageDelayed(MessageType.msgRestartH5.ordinal(), 100);
                                    }
                                }
                            }
                        }, 5000);
                    }
                }
            }, 10000, 10000 );
        }

    }

    void UIClickStartPush() {
        if (getLocalIpAddress().equals(""))
        {
            outputInfo("IP地址是空，不推。可能没插网线",false);
            return;
        }

        if(VideoConfig.instance.url1.equals("") && VideoConfig.instance.url2.equals(""))
        {
            outputInfo("推流地址是空，不推。",false);
            return;
        }

        if(VideoConfig.instance.url2.equals(VideoConfig.instance.url1))
        {
            outputInfo("两个推流地址一样，不推。",false);
            return;
        }

        Log.e("HHHHH", "pushing h5 " + VideoConfig.instance.pushH5);

        if (VideoConfig.instance.pushH5== false )
        {
            if (libPublisher == null) {
                libPublisher = new SmartPublisherJniV2();
            }

            if( tm_check_h5 != null)
            {
                tm_check_h5.cancel();tm_check_h5 = null;
            }
        }

        if( VideoConfig.instance.pushH5 == true )
        {
            if(yuv_cam1 != null) yuv_cam1 = null;
            if(yuv_cam2 != null) yuv_cam2 = null;

            yuv_cam1 = new byte[VideoConfig.instance.GetVideoWidth() * VideoConfig.instance.GetVideoHeight() * 3 / 2];
            yuv_cam2 = new byte[VideoConfig.instance.GetVideoWidth() * VideoConfig.instance.GetVideoHeight() * 3 / 2];

          if( ffmpegH == null)
              ffmpegH = new FFmpegHandle();

            StartH5CheckTimer();
        }

        Log.e(TAG,"开推");
        outputInfo("开推.", false);

        VideoConfig.instance.SaveConfig(this);

        if ( !isRecording && VideoConfig.instance.pushH5 == false)
        {
            InitAndSetConfig();
        }

        if( VideoConfig.instance.pushH5 == true)
        {
            if( ffmpegH != null)
                ffmpegH.SetConfig( VideoConfig.instance.GetVideoHeight(), VideoConfig.instance.GetVideoWidth(), 25, VideoConfig.instance.encoderKpbs);
        }

        //应用摄像头参数
        ApplyCam3Params();

            isPushing = true;

            VideoConfig.instance.videoPushState_1 = false;
            VideoConfig.instance.videoPushState_2 = false;

            Camera front_cam = GetCameraObj(FRONT);
            if (front_cam != null && VideoConfig.instance.url1.equals("") == false) {
                if( VideoConfig.instance.pushH5 == false)
                {
                    if (libPublisher.SmartPublisherSetURL(publisherHandleFront, VideoConfig.instance.url1) != 0) {
                        if(CameraPublishActivity.DEBUG) Log.e(TAG, "Failed to set publish stream URL..");
                        outputInfo("前置推流地址应用失败.", false);
                    }

                    int startRet = libPublisher.SmartPublisherStartPublisher(publisherHandleFront);
                    if (startRet != 0) {
                        isPushing = false;
                        if(CameraPublishActivity.DEBUG)  Log.e(TAG, "Failed to start push stream..");
                        TextView tvFr = findViewById(R.id.cam1_url_tip);
                        if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                    }
                }else if(VideoConfig.instance.pushH5 == true && ffmpegH != null){
                    int initRET =-1;
                    if( VideoConfig.instance.url1.equals("") == false && mCameraFront!= null)
                        initRET= ffmpegH.initVideo1(VideoConfig.instance.url1);

                    if( initRET !=0) {
                        isPushing = false;
                        ffmpegH = null;
                        if(CameraPublishActivity.DEBUG)  Log.e(TAG, "Failed to start push stream1..");
                        TextView tvFr = findViewById(R.id.cam1_url_tip);
                        if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                    }else {//推流成功
                        //启动H5检查对象
                        TextView tvFr = findViewById(R.id.cam1_url_tip);
                        if (tvFr != null) tvFr.setTextColor(Color.rgb(0, 255, 0));
                    }
                }
            }

            if ( !isRecording  && VideoConfig.instance.pushH5 == false)
            {
                if(CameraPublishActivity.DEBUG)  Log.e(TAG, "CheckInitAudioRecorder");
                CheckInitAudioRecorder();
            }

            Camera back_cam = GetCameraObj(BACK);
            if (back_cam != null && VideoConfig.instance.url2.equals("") == false) {
                if( VideoConfig.instance.pushH5 == false)
                {
                    if (libPublisher.SmartPublisherSetURL(publisherHandleBack, VideoConfig.instance.url2) != 0) {
                        if(CameraPublishActivity.DEBUG)  Log.e(TAG, "Failed to set publish stream URL..");
                    }
                    int startRet = libPublisher.SmartPublisherStartPublisher(publisherHandleBack);
                    if (startRet != 0) {
                        isPushing = false;
                        if(CameraPublishActivity.DEBUG) Log.e(TAG, "Failed to start push stream back..");
                        TextView tvFr = findViewById(R.id.cam2_url_tip);
                        if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                    }
                }else if(VideoConfig.instance.pushH5 == true && ffmpegH != null){
                    int initRET=-1;
                    if( VideoConfig.instance.url2.equals("") == false && mCameraBack != null)
                     initRET = ffmpegH.initVideo2(VideoConfig.instance.url2);

                        if( initRET !=0) {
                           // isPushing = false;
                            ffmpegH = null;
                            if(CameraPublishActivity.DEBUG)  Log.e(TAG, "Failed to start push stream2..");
                            TextView tvFr = findViewById(R.id.cam2_url_tip);
                            if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                        }else {

                            TextView tvFr = findViewById(R.id.cam2_url_tip);
                            if (tvFr != null) tvFr.setTextColor(Color.rgb(0, 255, 0));
                        }
                    }
            }

            if( VideoConfig.instance.pushH5 == false)
            {
                if (!isRecording && isPushing == true) {
                    ConfigControlEnable(false);
                    btnStartPush.setText(" 停止推送 ");
                } else if (isPushing == false) {
                    ConfigControlEnable(true);
                    btnStartPush.setText(" 推送");
                    outputInfo("推送失败。检查推流URL,或摄像头是否已插好", false);
                }
            }else if(VideoConfig.instance.pushH5 == true){

                if (  isPushing == true) {
                    ConfigControlEnable(false);
                    btnStartPush.setText(" 停止推送 ");
                } else if (isPushing == false) {
                    ConfigControlEnable(true);
                    btnStartPush.setText(" 推送");
                    outputInfo("推送失败。检查推流URL,或摄像头是否已插好", false);
                }
            }
    }

    void UIClickStopPush() {
        outputInfo("停推.", false);
        Log.e(TAG,"停推");
        stopPush();

        if (!isRecording && VideoConfig.instance.pushH5 == false) {
            ConfigControlEnable(true);
        }else if(VideoConfig.instance.pushH5 == true)
            ConfigControlEnable(true);

        btnStartPush.setText(" 推送");
        isPushing = false;

        return;
    }

    class ButtonStartPushListener implements OnClickListener {
        public void onClick(View v) {
            if (isPushing) {
                UIClickStopPush();
                return;
            } else {
                boolean previewOK = SaveConfigFromUI();

                //SaveConfigHostInfoToCom();

                //检查是否需要重连服务器
                if (sendThread != null)
                    sendThread.ApplyNewServer(VideoConfig.instance.destHost, VideoConfig.instance.GetAppPort());

                //检查是否需要连接配置服务器
                if (confiThread != null) {
                    if (VideoConfig.instance.enableConfigServer == true)
                        confiThread.ApplyNewServer(VideoConfig.instance.configHost, VideoConfig.instance.GetConfigPort());
                    else
                        confiThread.ApplyNewServer("", 0);
                }

                if (previewOK) {
                    UIClickStartPush();
                }
            }
        }
    }

    private void stopPush() {
        if( VideoConfig.instance.pushH5 == false) {
            if (!isRecording) {
                if (audioRecord_ != null) {
                    if (CameraPublishActivity.DEBUG)
                        Log.i(TAG, "stopPush, call audioRecord_.StopRecording..");

                    audioRecord_.Stop();

                    if (audioRecordCallback_ != null) {
                        audioRecord_.RemoveCallback(audioRecordCallback_);
                        audioRecordCallback_ = null;
                    }

                    audioRecord_ = null;
                }
            }

                if (libPublisher != null && publisherHandleFront != 0) {
                    libPublisher.SmartPublisherStopPublisher(publisherHandleFront);
                }

                if (!isRecording) {
                    if (publisherHandleFront != 0) {
                        if (libPublisher != null) {
                            libPublisher.SmartPublisherClose(publisherHandleFront);
                            publisherHandleFront = 0;
                        }
                    }
                }

                if (libPublisher != null && publisherHandleBack != 0) {
                    libPublisher.SmartPublisherStopPublisher(publisherHandleBack);
                }

                if (!isRecording) {
                    if (publisherHandleBack != 0) {
                        if (libPublisher != null) {
                            libPublisher.SmartPublisherClose(publisherHandleBack);
                            publisherHandleBack = 0;
                        }
                    }
                }

        }else {
            isPushing = false;
            if( ffmpegH != null) {
                mHandler.sendEmptyMessageDelayed(MessageType.msgDelayClose.ordinal(), 1000);
                //ffmpegH.close1();
                //ffmpegH.close2();
                //ffmpegH = null;
            }
        }

        TextView tvFr1 = findViewById(R.id.cam1_url_tip);
        if (tvFr1 != null) tvFr1.setTextColor(Color.rgb(0, 0, 0));

        TextView tvFr2 = findViewById(R.id.cam2_url_tip);
        if (tvFr2 != null) tvFr2.setTextColor(Color.rgb(0, 0, 0));
    }

    void BeginRecord()
    {
        if (isRecording) {
            stopRecorder();
            isRecording = false;
        }

        if(CameraPublishActivity.DEBUG) Log.i(TAG, "onClick start recorder..");

        if (libPublisher == null)
            return;

        if( sdCardPath.equals("") == true)
            return;

        isRecording = true;

        if (!isPushing) {
            InitAndSetConfig();
        }
            if( mCameraFront != null && publisherHandleFront != 0)
            {
                ConfigRecorderFuntion(sdCardPath + fronDirName, publisherHandleFront, true);
            }

            if( mCameraBack != null && publisherHandleBack != 0)
            {
                ConfigRecorderFuntion(sdCardPath + backDirName, publisherHandleBack, true);
            }

            int recordCount = 0;
            int startRet = 0;
            if( mCameraFront != null )
            {
                recordCount = 1;
                libPublisher.SmartPublisherStartRecorder(publisherHandleFront);
                if (startRet != 0) {
                    isRecording = false;

                    if(CameraPublishActivity.DEBUG)  Log.e(TAG, "Failed to start front cam recorder.");
                    return;
                }
            }

            //因为业务需求 只录一路
            if( mCameraBack != null)
            {
                if( recordCount == 0)
                {
                    startRet = libPublisher.SmartPublisherStartRecorder(publisherHandleBack);
                    if (startRet != 0) {
                        isRecording = false;

                        if(CameraPublishActivity.DEBUG)  Log.e(TAG, "Failed to start back cam recorder .");
                        return;
                    }
                }
            }


        if ( !isPushing )
        {
            CheckInitAudioRecorder();	//enable pure video publisher..
        }
    }

    private void stopRecorder() {
        isRecording = false;

        if( VideoConfig.instance.pushH5 == true)
            return;

        if (!isPushing) {
            if (audioRecord_ != null) {
                if(CameraPublishActivity.DEBUG) Log.i(TAG, "stopRecorder, call audioRecord_.StopRecording..");

                audioRecord_.Stop();

                if (audioRecordCallback_ != null) {
                    audioRecord_.RemoveCallback(audioRecordCallback_);
                    audioRecordCallback_ = null;
                }

                audioRecord_ = null;
            }
        }

            if (libPublisher != null && publisherHandleFront != 0) {
                libPublisher.SmartPublisherStopRecorder(publisherHandleFront);
            }

            if (!isPushing) {
                if (publisherHandleFront != 0) {
                    if (libPublisher != null) {
                        libPublisher.SmartPublisherClose(publisherHandleFront);
                        publisherHandleFront = 0;
                    }
                }
            }

            if (libPublisher != null && publisherHandleBack != 0) {
                libPublisher.SmartPublisherStopRecorder(publisherHandleBack);
            }

            if (!isPushing) {
                if (publisherHandleBack != 0) {
                    if (libPublisher != null) {
                        libPublisher.SmartPublisherClose(publisherHandleBack);
                        publisherHandleBack = 0;
                    }
                }
            }

    }

    private void SetCameraFPS(Camera.Parameters parameters) {
        if (parameters == null)
            return;

        int[] findRange = null;

        int defFPS = 20 * 1000;

        List<int[]> fpsList = parameters.getSupportedPreviewFpsRange();
        if (fpsList != null && fpsList.size() > 0) {
            for (int i = 0; i < fpsList.size(); ++i) {
                int[] range = fpsList.get(i);
                if (range != null
                        && Camera.Parameters.PREVIEW_FPS_MIN_INDEX < range.length
                        && Camera.Parameters.PREVIEW_FPS_MAX_INDEX < range.length) {
                    if(CameraPublishActivity.DEBUG)  Log.i(TAG, "Camera index:" + i + " support min fps:" + range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]);

                    if(CameraPublishActivity.DEBUG)  Log.i(TAG, "Camera index:" + i + " support max fps:" + range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);

                    if (findRange == null) {
                        if (defFPS <= range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]) {
                            findRange = range;

                            if(CameraPublishActivity.DEBUG)   Log.i(TAG, "Camera found appropriate fps, min fps:" + range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
                                    + " ,max fps:" + range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
                        }
                    }
                }
            }
        }

        if (findRange != null) {
            parameters.setPreviewFpsRange(findRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX], findRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
        }
    }

    /*it will call when surfaceChanged*/
    private boolean initCamera(int camera_type, SurfaceHolder holder) {
        if(CameraPublishActivity.DEBUG)  Log.i(TAG, "initCa11mera..");

        if (isTimeReady == false)
            return false;

        Camera camera = GetCameraObj(camera_type);
        if (camera == null) {
            if(CameraPublishActivity.DEBUG) Log.e(TAG, "initCa111mera camera is null, type=" + camera_type);
            //return false;
        }

        int cameraIndex = GetCameraIndex(camera_type);
        if (-1 == cameraIndex) {
            if(CameraPublishActivity.DEBUG)  Log.e(TAG, "initCam11era cameraIndex is -1, type=" + camera_type);
            //return false;
        }

        if (FRONT == camera_type && camera != null) {
            if (mPreviewRunningFront) {
                camera.stopPreview();
            }
        } else if (BACK == camera_type && camera != null) {
            if (mPreviewRunningBack) {
                camera.stopPreview();
            }
        }

        Camera.Parameters parameters;
        try {
            parameters = camera.getParameters();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        parameters.setPreviewSize(VideoConfig.instance.GetVideoWidth(), VideoConfig.instance.GetVideoHeight());
        parameters.setPictureFormat(PixelFormat.JPEG);
        parameters.setPreviewFormat(PixelFormat.YCbCr_420_SP);

        SetCameraFPS(parameters);

        if (camera != null) camera.setDisplayOrientation(90);

        if(CameraPublishActivity.DEBUG)  Log.e("Cmeraaaa", "apply w:" + VideoConfig.instance.GetVideoWidth() + "h " + VideoConfig.instance.GetVideoHeight());
        try {
            if (camera != null) camera.setParameters(parameters);
        } catch (Exception ex) {
            if(CameraPublishActivity.DEBUG)   Log.e("*******", "Apply Camera Config failed.");
            return false;
        }

        int bufferSize = (((VideoConfig.instance.GetVideoWidth() | 0xf) + 1) * VideoConfig.instance.GetVideoHeight() * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat())) / 8;

        if (camera != null) camera.addCallbackBuffer(new byte[bufferSize]);

        if (camera != null)
            camera.setPreviewCallbackWithBuffer(new NT_SP_CameraPreviewCallback(camera_type));

        try {
            if (camera != null) camera.setPreviewDisplay(holder);
        } catch (Exception ex) {
            if (null != camera) {
                camera.release();
                camera = null;
                SetCameraObj(camera_type, null);
            }
            ex.printStackTrace();

            return false;
        }

        if (camera != null) {
            try {
                camera.startPreview();
            } catch (Exception ea) {
                ea.printStackTrace();
            }
        }

        try{
            if (FRONT == camera_type && camera != null) {
                camera.autoFocus(myAutoFocusCallbackFront);
                mPreviewRunningFront = true;
            } else if (BACK == camera_type && camera != null) {
                camera.autoFocus(myAutoFocusCallbackBack);
                mPreviewRunningBack = true;
            }
        }catch (Exception e)
        {
            e.printStackTrace();
        }

        return true;
    }

    int GetCameraIndex(int type) {
        if (FRONT == type) {
            return curFrontCameraIndex;
        } else if (BACK == type) {
            return curBackCameraIndex;
        } else {
            if(CameraPublishActivity.DEBUG) Log.i(TAG, "GetCameraIndex type error, type=" + type);
            return -1;
        }
    }

    Camera GetCameraObj(int type) {
        if (FRONT == type) {
            return mCameraFront;
        } else if (BACK == type) {
            return mCameraBack;
        } else {
            if(CameraPublishActivity.DEBUG)  Log.i(TAG, "GetCameraObj type error, type=" + type);
            return null;
        }
    }

    void SetCameraObj(int type, Camera c) {
        if (FRONT == type) {
            mCameraFront = c;
        } else if (BACK == type) {
            mCameraBack = c;
        } else {
            if(CameraPublishActivity.DEBUG)  Log.i(TAG, "SetCameraObj type error, type=" + type);
        }
    }

    class NT_SP_SurfaceHolderCallback implements Callback {
        private int type_ = 0;

        public NT_SP_SurfaceHolderCallback(int type) {
            type_ = type;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {

            if(CameraPublishActivity.DEBUG)  Log.i(TAG, "surfaceCreated..type_=" + type_);

            if (type_ != FRONT && type_ != BACK) {
                if(CameraPublishActivity.DEBUG)  Log.e(TAG, "surfaceCreated type error, type=" + type_);
                return;
            }

            try {

                if (type_ == FRONT) {
                    int cammeraIndex = findFrontCamera();
                    if (cammeraIndex == -1) {
                        if(CameraPublishActivity.DEBUG)  Log.e(TAG, "surfaceCreated, There is no front camera!!");
                        return;
                    }
                } else if (type_ == BACK) {
                    int cammeraIndex = findBackCamera();
                    if (-1 == cammeraIndex) {
                        if(CameraPublishActivity.DEBUG)  Log.e(TAG, "surfaceCreated, there is no back camera");

                        return;
                    }
                }

                if (GetCameraObj(type_) == null) {
                    Camera c = openCamera(type_);
                    SetCameraObj(type_, c);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if(CameraPublishActivity.DEBUG) Log.e(TAG, "surfaceChanged..");

            if (type_ != FRONT && type_ != BACK)
                return;

            //initCamera(type_, holder);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if(CameraPublishActivity.DEBUG)  Log.i(TAG, "Surface Destroyed");
        }
    }

    private void  rotateYUVDegree90(byte[] data, byte[] yuv, int imageWidth, int imageHeight) {
        //Log.e(TAG, "wwww"+ imageWidth + "hhhhh"+ imageHeight);
        //byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y luma
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i--;
            }
        }
       // return yuv;
    }

    //ExecutorService  executor_front = Executors.newSingleThreadExecutor();;//Executors.newFixedThreadPool(5);
    //ExecutorService  executor_back = Executors.newSingleThreadExecutor();

    class NT_SP_CameraPreviewCallback implements PreviewCallback {
        private int type_ = 0;

        private int frameCount_ = 0;

        public NT_SP_CameraPreviewCallback(int type) {
            type_ = type;
        }

        @Override
        public void onPreviewFrame(final byte[] data, Camera camera) {

            frameCount_++;
            if (frameCount_ % 5000 == 0) {
                //Log.i("OnPre", "gc+");
                System.gc();
                //Log.i("OnPre", "gc-");
            }

            if (type_ == FRONT) {
                isFrontCameraPreviewOK = true;
                //Log.e(TAG,"前前前前前前前前摄像头onPreviewFrame");
            } else if (type_ == BACK) {
                isBackCameraPreviewOK = true;
                //Log.e(TAG,"后后后后后后后后后后后后摄像头onPreviewFrame");
            }

            if (data == null) {
                Parameters params = camera.getParameters();
                Size size = params.getPreviewSize();
                int bufferSize = (((size.width | 0x1f) + 1) * size.height * ImageFormat.getBitsPerPixel(params.getPreviewFormat())) / 8;
                camera.addCallbackBuffer(new byte[bufferSize]);

                if (type_ == FRONT) {
                    //	Log.e(TAG,"前前前前前前前前摄像头data= null");
                } else if (type_ == BACK) {
                    ///		Log.e(TAG,"后后后后后后后后后后后后摄像头data= null");
                }

            } else {

                        if (FRONT == type_ ) {
                            if(VideoConfig.instance.pushH5 == true)
                            {
                                if( ffmpegH != null&& isPushing)
                                {
                                   // executor_front.execute(new Runnable() {
                                    //    @Override
                                   //     public void run() {
                                           // Log.e(TAG,"vilen" + data.length+ "hadnle"+(ffmpegH== null));


                                                rotateYUVDegree90(data,yuv_cam1,VideoConfig.instance.GetVideoWidth(),VideoConfig.instance.GetVideoHeight());

                                                int rets =0;
                                                if( ffmpegH != null)
                                                    rets = ffmpegH.onFrameCallback1(yuv_cam1);

                                                //Log.e("FRONTCAM", "onFrameCallback1 ret" + rets);

                                                if(rets == 0 && begin_check_h5)
                                                {
                                                    h5_video1_push_state = 0;
                                                }

                                      //  }
                                   // });
                                }
                            } else {
                                if (libPublisher != null && publisherHandleFront != 0)
                                    libPublisher.SmartPublisherOnCaptureVideoData(publisherHandleFront, data, data.length, BACK, VideoConfig.instance.currentOrigentation);
                            }
                        }

                        if (BACK == type_ ) {
                            if(VideoConfig.instance.pushH5 == true)
                            {
                                if( ffmpegH != null && isPushing && VideoConfig.instance.url2.equals("") == false)
                                {
                                   // executor_back.execute(new Runnable() {
                                    //    @Override
                                   //     public void run() {

                                            rotateYUVDegree90(data,yuv_cam2, VideoConfig.instance.GetVideoWidth(),VideoConfig.instance.GetVideoHeight());
                                            int rets =0;

                                            if( ffmpegH != null )
                                                rets  = ffmpegH.onFrameCallback2(yuv_cam2);

                                            //Log.e(TAG, "onFrameCallback2 ret" + rets);
                                            if(rets == 0 && begin_check_h5) {
                                                h5_video2_push_state = 0;
                                            }
                                      //  }
                                   // });
                                }
                            }else if(VideoConfig.instance.pushH5 == false){
                                if (libPublisher != null&& publisherHandleBack != 0)
                                    libPublisher.SmartPublisherOnCaptureVideoData(publisherHandleBack, data, data.length, BACK, VideoConfig.instance.currentOrigentation);
                            }
                        }


                camera.addCallbackBuffer(data);
            }
        }
    }

    @SuppressLint("NewApi")
    private Camera openCamera(int type) {
        int frontIndex = -1;
        int backIndex = -1;
        int cameraCount = Camera.getNumberOfCameras();
        if(CameraPublishActivity.DEBUG) Log.i(TAG, "cameraCount: " + cameraCount);

        CameraInfo info = new CameraInfo();
        for (int cameraIndex = 0; cameraIndex < cameraCount; cameraIndex++) {
            Camera.getCameraInfo(cameraIndex, info);

            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                frontIndex = cameraIndex;
            } else if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                backIndex = cameraIndex;
            }
        }

        if (type == FRONT && frontIndex != -1) {
            curFrontCameraIndex = frontIndex;
            return Camera.open(frontIndex);
        } else if (type == BACK && backIndex != -1) {
            curBackCameraIndex = backIndex;
            return Camera.open(backIndex);
        }

        return null;
    }


    //Check if it has front camera
    private int findFrontCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return camIdx;
            }
        }
        return -1;
    }

    //Check if it has back camera
    private int findBackCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return camIdx;
            }
        }
        return -1;
    }

    /**
     * 根据目录创建文件夹
     *
     * @param context
     * @param cacheDir
     * @return
     */
    public static File getOwnCacheDirectory(Context context, String cacheDir) {
        File appCacheDir = null;
        //判断sd卡正常挂载并且拥有权限的时候创建文件
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) && hasExternalStoragePermission(context)) {
            appCacheDir = new File(Environment.getExternalStorageDirectory(), cacheDir);
            Log.i(TAG, "appCacheDir: " + appCacheDir);
        }
        if (appCacheDir == null || !appCacheDir.exists() && !appCacheDir.mkdirs()) {
            appCacheDir = context.getCacheDir();
        }
        return appCacheDir;
    }

    /**
     * 检查是否有权限
     *
     * @param context
     * @return
     */
    private static boolean hasExternalStoragePermission(Context context) {
        int perm = context.checkCallingOrSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE");
        return perm == 0;
    }


}