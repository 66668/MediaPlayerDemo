package demo.com;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;

/**
 * Created by sjy on 2017/11/28.
 */

public class VideoViewPlayerActivity extends AppCompatActivity implements MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener, View.OnClickListener {
    private String path;//路径
    private String uriPath;//网络路径
    private Uri uri;

    //按钮
    private Button btn_start;
    private Button btn_pause;
    private Button btn_stop;
    private Button btn_restart;
    //时间显示
    private TextView tvCurrentTime;
    private TextView tvTotalTime;

    private VideoView videoView;
    private MediaController controller;
    private SeekBar mSeekbar;
    private File file;

    private boolean isStopUpdatingProgress = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_videoview);
        initMyView();
    }

    /**
     * 控件初始化
     */
    private void initMyView() {

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
        videoView = findViewById(R.id.videoView);

        path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "demo.mp4";//测试的mp4提前放到内置sd卡根目录
        uriPath = "http://200000594.vod.myqcloud.com/200000594_1617cc56708f11e596723b988fc18469.f20.mp4";
        uri = Uri.parse(uriPath);
        controller = new MediaController(this);
        file = new File(path);

    }


    /**
     * 播放输入框的文件
     */
    private void play() {

//        videoView.setVideoPath(file.getAbsolutePath());
                    videoView.setVideoURI(uri);

        //videoView与MediaController设置关联
        videoView.setMediaController(controller);
        //让controller获取焦点
        videoView.requestFocus();

        videoView.start();

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();
            }
        });
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Toast.makeText(VideoViewPlayerActivity.this, "over", Toast.LENGTH_SHORT).show();
            }
        });

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
                play();
                break;

            case R.id.btn_pause:
                videoView.pause();
                break;

            case R.id.btn_stop:
                videoView.stopPlayback();

                break;

            case R.id.btn_restart:
                play();
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
                int currentPosition = 10;
                //                      =  mMediapPlayer.getCurrentPosition();
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
        //        mMediapPlayer.seekTo(progress);
        isStopUpdatingProgress = false;
        new Thread(new UpdateProgressRunnable()).start();
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onStop() {
        super.onStop();

    }
}
