package demo.com;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/**
 * Created by sjy on 2017/11/28.
 */

public class SurfceVideoPlayerActivity extends AppCompatActivity implements MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener, View.OnClickListener {

    private String path;//路径

    //按钮
    private Button btn_start;
    private Button btn_pause;
    private Button btn_stop;
    private Button btn_restart;
    //时间显示
    private TextView tvCurrentTime;
    private TextView tvTotalTime;

    private SurfaceHolder holder;
    private SurfaceView surfaceView;
    private MediaPlayer mMediapPlayer;
    private SeekBar mSeekbar;

    private boolean isStopUpdatingProgress = false;

    private final int NORMAL = 0;//闲置
    private final int PLAYING = 1;//播放中
    private final int PAUSING = 2;//暂停
    private final int STOPING = 3;//停止中

    private int currentstate = NORMAL;//播放器当前的状态，默认是空闲状态


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_surfaceplayer);
        initMyView();
        //是采用自己内部的双缓冲区，而是等待别人推送数据
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    /**
     * 控件初始化
     */
    private void initMyView() {
        path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "demo.mp4";//测试的mp4提前放到内置sd卡根目录

        btn_start = findViewById(R.id.btn_start);
        btn_pause = findViewById(R.id.btn_pause);
        btn_stop = findViewById(R.id.btn_stop);
        btn_restart = findViewById(R.id.btn_restart);

        btn_start.setOnClickListener(this);
        btn_pause.setOnClickListener(this);
        btn_stop.setOnClickListener(this);
        btn_restart.setOnClickListener(this);

        mSeekbar = (SeekBar) findViewById(R.id.sb_progress);
        tvCurrentTime = (TextView) findViewById(R.id.tv_current_time);
        tvTotalTime = (TextView) findViewById(R.id.tv_total_time);

        mSeekbar.setOnSeekBarChangeListener(this);
        surfaceView = findViewById(R.id.surfaceview);
        holder = surfaceView.getHolder();//SurfaceView 帮助类对象
        holder.setKeepScreenOn(true);

        mMediapPlayer = new MediaPlayer();
    }

    /**
     * 播放输入框的文件
     */
    private void play() {
        mMediapPlayer.reset();
        try {
            //设置音频流类型
            mMediapPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            //准备资源
            mMediapPlayer.setDataSource(path);
            //设置以下播放器显示的位置
            mMediapPlayer.setDisplay(holder);

            mMediapPlayer.prepare();
            mMediapPlayer.start();

            mMediapPlayer.setOnCompletionListener(this);
            //把当前播放器的状态置为：播放中
            currentstate = PLAYING;
            int duration = mMediapPlayer.getDuration();//总时长
            mSeekbar.setMax(duration);
            //把总时间显示在TextView上
            int min = duration / 1000 / 60;
            int sec = duration / 1000 % 60;
            tvTotalTime.setText("/" + min + ":" + sec);
            tvCurrentTime.setText("00:00");

            isStopUpdatingProgress = false;
            new Thread(new UpdateProgressRunnable()).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
                if (mMediapPlayer != null) {
                    if (currentstate == PAUSING) {
                        mMediapPlayer.start();
                        currentstate = PLAYING;
                        isStopUpdatingProgress = false;//每次在调用刷新线程时，都要设为false
                        new Thread(new UpdateProgressRunnable()).start();
                        return;

                        //下面这个判断完美的解决了停止后重新播放的，释放两个资源的问题
                    } else if (currentstate == STOPING) {
                        mMediapPlayer.reset();
                        mMediapPlayer.release();
                        mMediapPlayer = null;
                    }
                }

                play();
                break;

            case R.id.btn_pause:
                if (mMediapPlayer != null && currentstate == PLAYING) {
                    mMediapPlayer.pause();
                    currentstate = PAUSING;
                    isStopUpdatingProgress = true;//停止刷新主线程UI
                }
                break;

            case R.id.btn_stop:
                if (mMediapPlayer != null) {
                    mMediapPlayer.stop();
                    currentstate = STOPING;
                }
                break;

            case R.id.btn_restart:
                if (mMediapPlayer != null) {
                    mMediapPlayer.release();

                    play();
                }
                break;
        }

    }

    /**
     * 刷新进度和时间的任务
     *
     * @author sucy
     */
    class UpdateProgressRunnable implements Runnable {
        @Override
        public void run() {
            //每隔1秒钟取一下当前正在播放的进度，设置给seekbar
            while (!isStopUpdatingProgress) {
                //得到当前进度
                int currentPosition = mMediapPlayer.getCurrentPosition();
                mSeekbar.setProgress(currentPosition);
                final int m = currentPosition / 1000 / 60;
                final int s = currentPosition / 1000 % 60;
                //此方法给定的runable对象，会执行主线程（UI线程中）
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvCurrentTime.setText(m + ":" + s);
                    }
                });
                SystemClock.sleep(1000);
            }
        }
    }


    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        Toast.makeText(this, "播放完了，重新再播放", Toast.LENGTH_SHORT).show();
        mediaPlayer.start();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        isStopUpdatingProgress = true;//当开始拖动时，那么就开始停止刷新线程
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int progress = seekBar.getProgress();
        //播放器切换到指定的进度位置上
        mMediapPlayer.seekTo(progress);
        isStopUpdatingProgress = false;
        new Thread(new UpdateProgressRunnable()).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMediapPlayer != null && currentstate == PLAYING) {
            mMediapPlayer.pause();
            currentstate = PAUSING;
            isStopUpdatingProgress = true;//停止刷新主线程UI
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mMediapPlayer != null) {
            mMediapPlayer.stop();
            currentstate = STOPING;
        }
    }
}
