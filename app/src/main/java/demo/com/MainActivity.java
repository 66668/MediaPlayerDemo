package demo.com;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {
    Button btn1;
    Button btn2;
    Button btn3;
    Button btn4;
    Button btn_camera;
    Button btn_gallery_camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initMyView();
    }

    private void initMyView() {
        btn1 = findViewById(R.id.btn1);
        btn2 = findViewById(R.id.btn2);
        btn3 = findViewById(R.id.btn3);
        btn4 = findViewById(R.id.btn4);
        btn4 = findViewById(R.id.btn4);
        btn_camera = findViewById(R.id.btn_camera);
        btn_gallery_camera = findViewById(R.id.btn_gallery_camera);

        btn1.setOnClickListener(this);
        btn2.setOnClickListener(this);
        btn3.setOnClickListener(this);
        btn4.setOnClickListener(this);
        btn_camera.setOnClickListener(this);
        btn_gallery_camera.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn1://视频播放
                Intent intent = new Intent(this, SurfceVideoPlayerActivity.class);
                startActivity(intent);

                break;
            case R.id.btn2://视频播放
                Intent intent2 = new Intent(this, VideoViewPlayerActivity.class);
                startActivity(intent2);

                break;
            case R.id.btn3://视频播放

                break;
            case R.id.btn4://系统视频播放
                //在手机内置sd下的 根目录存放个测试视频 不推荐直接用Uri.pase(path)方法，推荐使用file方式
                String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/demo.mp4";//
                File file = new File(path);
                Uri uri = Uri.fromFile(file);
                Intent intent4 = new Intent(Intent.ACTION_VIEW);
                intent4.setDataAndType(uri, "video/*");
                try {
                    this.startActivity(intent4);

                } catch (Exception e) {
                    Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }

                break;

            case R.id.btn_camera://google camera2
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCameraFragment();
                } else {
                    requestCameraPermission();
                }
                break;
            case R.id.btn_gallery_camera://相机相册图片
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCameraGallery();
                } else {
                    requestCameraPermission();
                }
                break;
        }
    }

    private void startCameraFragment() {
        Intent intent = new Intent(this, Camera2BaseActivity.class);
        startActivity(intent);

    }

    private void startCameraGallery() {
        Intent intent = new Intent(this, CameraGalleryActivity.class);
        startActivity(intent);

    }

    //==========================================权限相关===================================================
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Toast.makeText(MainActivity.this, "您已禁止该权限，需要重新开启。", Toast.LENGTH_SHORT).show();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    /**
     * requestPermissions 的回调
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCameraFragment();
                } else {
                    Toast.makeText(MainActivity.this, "抱歉，该功能无法使用！", Toast.LENGTH_SHORT).show();
                    //弹窗处理 让用户再调用
                }

                break;
        }
    }
}
