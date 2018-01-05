package demo.com;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

/**
 * Created by sjy on 2018/1/5.
 */

public class CameraGalleryActivity extends AppCompatActivity implements View.OnClickListener {
    Button btn_camera;
    Button btn_gallery;
    ImageView image;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_camera_gallery);
        btn_camera = findViewById(R.id.camera);
        btn_gallery = findViewById(R.id.gallery);
        image = findViewById(R.id.image);

        btn_camera.setOnClickListener(this);
        btn_gallery.setOnClickListener(this);


    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.camera:
                break;
            case R.id.gallery:
                break;
        }
    }
}
