package top.defaults.cameraapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.jakewharton.rxbinding2.view.RxView;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_RECORDED_VIDEO_FILE_PATH = "extra_recorded_video_file_path";

    private static final int REQUEST_VIDEO_RECORD = 2;

    private View prepareToRecord;
    private View playLayout;
    private TextView play;
    private TextView fileInfo;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RxPermissions rxPermissions = new RxPermissions(this);
        prepareToRecord = findViewById(R.id.prepare_to_record);
        RxView.clicks(prepareToRecord)
                .compose(rxPermissions.ensure(Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE))
                .subscribe(granted -> {
                    if (granted) {
                        startVideoRecordActivity();
                    } else {
                        Snackbar.make(prepareToRecord, getString(R.string.no_enough_permission), Snackbar.LENGTH_SHORT).setAction("Confirm", null).show();
                    }
                });

        playLayout = findViewById(R.id.play_layout);
        play = findViewById(R.id.play);
        fileInfo = findViewById(R.id.file_info);
    }

    private void startVideoRecordActivity() {
        playLayout.setVisibility(View.GONE);
        play.setText(R.string.play);
        fileInfo.setText("");
        play.setOnClickListener(null);

        Intent intent = new Intent(this, PhotographerActivity.class);
        startActivityForResult(intent, REQUEST_VIDEO_RECORD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == REQUEST_VIDEO_RECORD) {
            String filePath = data.getStringExtra(EXTRA_RECORDED_VIDEO_FILE_PATH);
            if (!TextUtils.isEmpty(filePath)) {
                File file = new File(filePath);
                play.setText(String.format(Locale.US, getString(R.string.play_file), file.getName()));
                fileInfo.setText(String.format(Locale.US, "File size：%dB", file.length()));
                playLayout.setVisibility(View.VISIBLE);
                play.setOnClickListener(view -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri videoURI = FileProvider.getUriForFile(MainActivity.this, getApplicationContext().getPackageName() + ".provider", file);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        videoURI = Uri.fromFile(file);
                    }
                    intent.setDataAndType(videoURI, "video/mp4");
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    PackageManager packageManager = getPackageManager();
                    List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
                    boolean isIntentSafe = activities.size() > 0;

                    if (isIntentSafe) {
                        startActivity(intent);
                    } else {
                        Toast.makeText(MainActivity.this, "No video player found", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
}
