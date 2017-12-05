package demo.com;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by sjy on 2017/11/30.
 */

public class Camera2BaseActivity extends AppCompatActivity  {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.layout_main, Camera2BasicFragment.newInstance())
                .commit();
    }
}
