package com.guaju.adoulive.ui.watch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.view.View;

import com.guaju.adoulive.R;
import com.guaju.adoulive.app.AdouApplication;
import com.guaju.adoulive.bean.DanmuMsgInfo;
import com.guaju.adoulive.bean.Gift;
import com.guaju.adoulive.bean.GiftMsgInfo;
import com.guaju.adoulive.bean.TextMsgInfo;
import com.guaju.adoulive.engine.MessageObservable;
import com.guaju.adoulive.engine.live.Constants;
import com.guaju.adoulive.engine.live.DemoFunc;
import com.guaju.adoulive.timcustom.CustomTimConstant;
import com.guaju.adoulive.utils.ToastUtils;
import com.guaju.adoulive.widget.BottomChatSwitchLayout;
import com.guaju.adoulive.widget.BottomSwitchLayout;
import com.guaju.adoulive.widget.HeightSensenableRelativeLayout;
import com.guaju.adoulive.widget.LiveMsgListView;
import com.guaju.adoulive.widget.danmu.DanmuView;
import com.guaju.adoulive.widget.gift.GiftItem;
import com.guaju.adoulive.widget.gift.GiftSendDialog;
import com.guaju.adoulive.widget.gift.GiftView;
import com.orhanobut.logger.Logger;
import com.tencent.TIMFriendshipManager;
import com.tencent.TIMMessage;
import com.tencent.TIMUserProfile;
import com.tencent.TIMValueCallBack;
import com.tencent.ilivesdk.ILiveCallBack;
import com.tencent.ilivesdk.ILiveConstants;
import com.tencent.ilivesdk.view.AVRootView;
import com.tencent.livesdk.ILVCustomCmd;
import com.tencent.livesdk.ILVLiveConfig;
import com.tencent.livesdk.ILVLiveManager;
import com.tencent.livesdk.ILVLiveRoomOption;
import com.tencent.livesdk.ILVText;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by guaju on 2018/1/12.
 */


public class WatchLiveActivity extends Activity implements ILVLiveConfig.ILVLiveMsgListener {
    private static final int FIRST_GIFT_SEND_FLAG = -1;
    public static final int REPEAT_GIFT_SEND_FLAG =1 ;
    //倒计时时间范围
    private int repeatTimeLimit=10;
    private GiftSendDialog giftSendDialog;
    long firstSendTimeMillion;
    GiftItem availableGiftItem;

    Handler repeatGiftTimer=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case FIRST_GIFT_SEND_FLAG:
                    if (repeatTimeLimit>0){
                        repeatTimeLimit--;//开始倒数
                        sendEmptyMessageDelayed(FIRST_GIFT_SEND_FLAG,80);
//                        bt.setText("发送（"+repeatTimeLimit+")");
                        giftSendDialog.setSendButtonText("发送（"+repeatTimeLimit+")");
                        //用户现在可以连发
                    }else{
                        //倒计时已经数完了，可以重新再开始
                        availableGiftItem.setIsRepeat(false);
                        firstSendTimeMillion=0;
                        repeatTimeLimit=10;
//                        bt.setText("发送");
                        giftSendDialog.setSendButtonText("发送");
                        //用户不能再连发了
                    }

                    break;

                case REPEAT_GIFT_SEND_FLAG:
                    //停止第一个事件 的处理


                    if (repeatTimeLimit>0){
                        repeatTimeLimit--;//开始倒数
                        sendEmptyMessageDelayed(REPEAT_GIFT_SEND_FLAG,80);
//                        bt.setText("发送（"+repeatTimeLimit+")");
                        giftSendDialog.setSendButtonText("发送（"+repeatTimeLimit+")");

                        //用户现在可以连发
                    }else{
                        //倒计时已经数完了，可以重新再开始
                        availableGiftItem.setIsRepeat(false);
                        availableGiftItem.repeatSendWithoutAddNum();
                        firstSendTimeMillion=0;
                        repeatTimeLimit=10;
//                        bt.setText("发送");
                        giftSendDialog.setSendButtonText("发送");
                        //用户不能再连发了
                    }


                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }



        }
    };

    private AVRootView avRootView;
    private int roomId;
    private String hostId;
    private BottomSwitchLayout bottomswitchlayout;
    private DanmuView danmuView;
    private Gift mselectedGift;

    private LiveMsgListView lmlv;
    //创建集合专门存储消息
    private ArrayList<TextMsgInfo> mList=new ArrayList<TextMsgInfo>();
    private BottomChatSwitchLayout chatswitchlayout;
    private HeightSensenableRelativeLayout hsrl;
    private GiftView giftView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_live);

        //初始化消息的接受者
        MessageObservable.getInstance().addObserver(this);
        initView();
        //设置默认状态
        setDefultStatus();

        lmlv.setData(mList);
        setListener();
        initRootView();

        //获取房间号，和主播号
        getinfoAndJoinRoom();

    }


    private void setDefultStatus() {
        chatswitchlayout.setVisibility(View.INVISIBLE);
        bottomswitchlayout.setVisibility(View.VISIBLE);
    }

    private void setListener() {
        hsrl.setOnLayoutHeightChangedListenser(new HeightSensenableRelativeLayout.OnLayoutHeightChangedListenser() {
            @Override
            public void showNormal() {
                setDefultStatus();
            }

            @Override
            public void showChat() {
                chatswitchlayout.setVisibility(View.VISIBLE);
                bottomswitchlayout.setVisibility(View.INVISIBLE);
            }
        });


        bottomswitchlayout.setOnSwitchListener(new BottomSwitchLayout.OnSwitchListener() {
            @Override
            public void onChat() {
                //聊天
                chatswitchlayout.setVisibility(View.VISIBLE);
                bottomswitchlayout.setVisibility(View.INVISIBLE);

            }

            @Override
            public void onClose() {
                //关闭时
                finish();
            }

            @Override
            public void onGift() {
                //发送按钮的回调
                GiftSendDialog.OnGiftSendListener onGiftSendListener = new GiftSendDialog.OnGiftSendListener() {
                    @Override
                    public void onSend(Gift selectedGift) {
                        //先得到选中的礼物
                        mselectedGift = selectedGift;
                        //做发送按钮的处理，发送自定义消息，因为观众需要给主播发消息，让主播看见
                        //消息内容
                        String text=CustomTimConstant.TYPE_GIFT+"送了一个"+selectedGift.getName();
                        //获取可用itemview
                        availableGiftItem = giftView.getAvailableGiftItem();
                        GiftMsgInfo giftMsgInfo = new GiftMsgInfo();
                        giftMsgInfo.setGift(selectedGift);
                        availableGiftItem.bindData(giftMsgInfo);
                        sendGift();
                        //给主播发送消息
                        sendTextMsg(text,CustomTimConstant.GIFT_MSG);

                    }
                };


                //弹出dialog
                giftSendDialog = new GiftSendDialog(WatchLiveActivity.this,R.style.custom_dialog,onGiftSendListener);
                giftSendDialog.show();
            }
        });
        chatswitchlayout.setOnMsgSendListener(new BottomChatSwitchLayout.OnMsgSendListener() {

            @Override
            public void sendMsg(String text) {
                //发送消息
                sendTextMsg(text,CustomTimConstant.TEXT_MSG);
            }

            @Override
            public void danmu(String text) {
                String newText = CustomTimConstant.TYPE_DAN + text;
                sendTextMsg(newText, CustomTimConstant.DANMU_MSG);
            }
        });

    }

    private void getinfoAndJoinRoom() {
        Intent intent = getIntent();
        if (intent != null) {
            roomId = intent.getIntExtra("roomId", -1);
            hostId = intent.getStringExtra("hostId");
            joinRoom(roomId + "");
        }
    }

    private void initRootView() {
        ILVLiveManager.getInstance().setAvVideoView(avRootView);
    }

    private void initView() {
        avRootView = findViewById(R.id.av_rootview);
        bottomswitchlayout = findViewById(R.id.bottomswitchlayout);
        chatswitchlayout = findViewById(R.id.chatswitchlayout);
        bottomswitchlayout.iv_switch_gift.setVisibility(View.VISIBLE);
        danmuView = findViewById(R.id.danmuview);
        giftView = findViewById(R.id.giftview);
        hsrl = findViewById(R.id.hsrl);
        lmlv = findViewById(R.id.lmlv);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ILVLiveManager.getInstance().onPause();
        Logger.e("onpause观看者");
    }

    @Override
    protected void onResume() {
        super.onResume();
        ILVLiveManager.getInstance().onResume();
        Logger.e("onresume观看者");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Logger.e("ondestoy观看者");
        quitRoom();
        ILVLiveManager.getInstance().onDestory();
    }

    // 加入房间
    private void joinRoom(String roomString) {
        int roomId = DemoFunc.getIntValue(roomString, -1);
        if (-1 == roomId) {
            ToastUtils.show("房间号不合法");
            //退出房间
            finish();
            return;
        }
        ILVLiveRoomOption option = new ILVLiveRoomOption("")
                .controlRole(Constants.ROLE_GUEST) //是一个浏览者
                .videoMode(ILiveConstants.VIDEOMODE_NORMAL)
                .autoCamera(false)
                .autoMic(false);
        ILVLiveManager.getInstance().joinRoom(roomId,
                option, new ILiveCallBack() {
                    @Override
                    public void onSuccess(Object data) {
                        //成功的时候怎么办 (增加房间观看数量)


                    }

                    @Override
                    public void onError(String module, int errCode, String errMsg) {
                        ToastUtils.show("加入房间失败，正在退出。。。");
                        //退出房间
                        finish();
                    }
                });

    }

    //退出房间的操作
    public void quitRoom() {
        ILVLiveManager.getInstance().quitRoom(new ILiveCallBack() {
            @Override
            public void onSuccess(Object data) {
                //退出成功
                ToastUtils.show("退出房间成功");
                //修改房间的观看数量
            }

            @Override
            public void onError(String module, int errCode, String errMsg) {

            }
        });
    }





        //腾讯云发送普通消息
    public void sendTextMsg(final String text,final  int cmd){
        //通过对方id获取对方的等级和对方的昵称

        List<String> ids = new ArrayList<>();
        ids.add(hostId);
        TIMFriendshipManager.getInstance().getFriendsProfile(ids, new TIMValueCallBack<List<TIMUserProfile>>() {
            @Override
            public void onError(int i, String s) {

            }

            @Override
            public void onSuccess(List<TIMUserProfile> timUserProfiles) {
                realSend(timUserProfiles,text,cmd);
            }
        });
    }
    //真正的发送消息
    private void realSend(List<TIMUserProfile> timUserProfiles, final String text,final int cmd) {
        //因为获取信息的时候 只传入了只有一个元素的集合，所以到这只能拿到一个用户的信息
        final TIMUserProfile profile = timUserProfiles.get(0);

        //发送普通消息
        ILVLiveManager.getInstance().sendText(new ILVText(ILVText.ILVTextType.eGroupMsg, profile.getIdentifier(), text), new ILiveCallBack() {
            @Override
            public void onSuccess(Object data) {
                String grade;
                //发送成功之后，加入到listview中去
                TextMsgInfo textMsgInfo = new TextMsgInfo();
                byte[] bytes = profile.getCustomInfo().get(CustomTimConstant.INFO_GRADE);
                if (bytes!=null){
                    grade = new String(bytes);
                }else{
                    grade="0";
                }
                String identifier = AdouApplication.getApp().getAdouTimUserProfile().getProfile().getIdentifier();
                textMsgInfo.setAdouID(identifier);
                textMsgInfo.setGrade(Integer.parseInt(grade));
                textMsgInfo.setText(text);
                textMsgInfo.setNickname(profile.getNickName());
                //更新列表

                if (cmd==CustomTimConstant.DANMU_MSG){
                    //发弹幕
                    String newMsg = text.substring(CustomTimConstant.TYPE_DAN.length(), text.length());
                    String avatar = AdouApplication.getApp().getAdouTimUserProfile().getProfile().getFaceUrl();
                    DanmuMsgInfo danmuMsgInfo = new DanmuMsgInfo();
                    danmuMsgInfo.setAdouID(hostId);
                    danmuMsgInfo.setAvatar(avatar);
                    danmuMsgInfo.setGrade(Integer.parseInt(grade));
                    danmuMsgInfo.setText(newMsg);

                    danmuView.addDanmu(danmuMsgInfo);
                    textMsgInfo.setText(newMsg);
                }
                if (cmd==CustomTimConstant.GIFT_MSG){
                    //准备数据
                    //通过上边的view开启动画
                     String newMsg = text.substring(CustomTimConstant.TYPE_GIFT.length(), text.length());
                     textMsgInfo.setText(text);
                }

                lmlv.addMsg(textMsgInfo);
            }

            @Override
            public void onError(String module, int errCode, String errMsg) {
                ToastUtils.show("发送失败，错误信息"+errMsg+"错误码"+errCode);
            }
        });
    }

    @Override
    public void onNewTextMsg(ILVText text, String SenderId, TIMUserProfile userProfile) {
        //当接受到普通消息的时候，展示到listview上边去
        TextMsgInfo textMsgInfo = new TextMsgInfo();
        String msg=text.getText();
        String nickName = userProfile.getNickName();
        String grade;
        byte[] bytes = userProfile.getCustomInfo().get(CustomTimConstant.INFO_GRADE);
        if (bytes!=null){
            grade = new String(bytes);
        }else{
            grade="0";
        }
        textMsgInfo.setAdouID(SenderId);
        textMsgInfo.setGrade(Integer.parseInt(grade));
        //需要判断发送的是否是弹幕
        if (msg.startsWith(CustomTimConstant.TYPE_DAN)) {
            //是弹幕
            String newMsg = msg.substring(CustomTimConstant.TYPE_DAN.length(), msg.length());
            textMsgInfo.setText(newMsg);
            //发送弹幕
            String avatar=userProfile.getFaceUrl();
            DanmuMsgInfo danmuMsgInfo = new DanmuMsgInfo();
            danmuMsgInfo.setText(newMsg);
            danmuMsgInfo.setGrade(Integer.parseInt(grade));
            danmuMsgInfo.setAvatar(avatar);
            danmuMsgInfo.setAdouID(SenderId);
            danmuView.addDanmu(danmuMsgInfo);

        } else if (msg.startsWith(CustomTimConstant.TYPE_GIFT)){
            //让接收到的消息是动画的话
            availableGiftItem=giftView.getAvailableGiftItem();
            sendGift();




        } else{textMsgInfo.setText(msg);}

        lmlv.addMsg(textMsgInfo);
    }

    @Override
    public void onNewCustomMsg(ILVCustomCmd cmd, String id, TIMUserProfile userProfile) {

    }

    @Override
    public void onNewOtherMsg(TIMMessage message) {

    }

    //考虑到联机的发送礼物
    public void sendGift(){
        //在第一次点的时候开始计时
        if (firstSendTimeMillion==0){
            //第一次点击不是连发，设置给giftitem
            availableGiftItem.setIsRepeat(false);
            //拿到第一次点的时间
            firstSendTimeMillion=System.currentTimeMillis();
            repeatGiftTimer.sendEmptyMessage(FIRST_GIFT_SEND_FLAG);
            //再执行动画
            availableGiftItem.setVisibility(View.VISIBLE);
            availableGiftItem.startAnimate();
        }
        else{//如果属于连击的话,需要把倒计时再从10开始倒数，并且增加礼物数
            //属于连发
            availableGiftItem.setIsRepeat(true);
            availableGiftItem.repeatSend();//连发操作
            //清空两个handler的处理
            repeatGiftTimer.removeMessages(FIRST_GIFT_SEND_FLAG);
            repeatGiftTimer.removeMessages(REPEAT_GIFT_SEND_FLAG);
            repeatGiftTimer.sendEmptyMessage(REPEAT_GIFT_SEND_FLAG);
            repeatTimeLimit=10;
        }
    }

}
