package top.defaults.videoapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1;
    private static final int REQUEST_VIDEO_RECORD = 2;

    private View prepareToRecord;
    private View playLayout;
    private TextView play;
    private TextView fileInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                Timber.d("Camera Id: %s, facing: %d", cameraId, facing);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        prepareToRecord = findViewById(R.id.prepare_to_record);
        prepareToRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_PERMISSIONS);
                } else {
                    startVideoRecordActivity();
                }
            }
        });
        playLayout = findViewById(R.id.play_layout);
        play = findViewById(R.id.play);
        fileInfo = findViewById(R.id.file_info);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSIONS:
                if (grantResults.length > 2
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED
                        && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                    startVideoRecordActivity();
                } else {
                    Snackbar.make(prepareToRecord, getString(R.string.no_enough_permission), Snackbar.LENGTH_SHORT).setAction("确认", null).show();
                }
                break;
        }
    }

    private void startVideoRecordActivity() {
        playLayout.setVisibility(View.GONE);
        play.setText(R.string.play);
        fileInfo.setText("");
        play.setOnClickListener(null);

        Intent intent = new Intent(MainActivity.this, VideoRecordActivity.class);
        startActivityForResult(intent, REQUEST_VIDEO_RECORD);
    }
}
