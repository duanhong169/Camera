package top.defaults.videoapp;

import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.Toast;

import java.util.Collections;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;
import top.defaults.video.AutoFitTextureView;
import top.defaults.video.Error;
import top.defaults.video.Keys;
import top.defaults.video.Photographer;
import top.defaults.video.PhotographerFactory;

public class PhotographerActivity extends AppCompatActivity {

    private boolean isRecordingVideo;

    @BindView(R.id.texture)
    AutoFitTextureView textureView;

    @BindView(R.id.video)
    Button videoButton;

    @OnClick(R.id.video)
    void video() {
        if (isRecordingVideo) {
            photographer.stopRecording();
        } else {
            photographer.startRecording(null);
        }
    }

    @OnClick(R.id.cancel)
    void cancel() {
        photographer.stopRecording();
        finish();
    }

    Photographer photographer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_record);
        ButterKnife.bind(this);
        photographer = PhotographerFactory.createPhotographerWithCamera2(this, textureView);
        photographer.setOnEventListener(new Photographer.OnEventListener() {
            @Override
            public void onStartRecording() {
                isRecordingVideo = true;
            }

            @Override
            public void onPauseRecording() {

            }

            @Override
            public void onResumeRecording() {

            }

            @Override
            public void onStopRecording(String filePath) {
                isRecordingVideo = false;
                Toast.makeText(PhotographerActivity.this, "File: " + filePath, Toast.LENGTH_SHORT).show();
                Intent data = new Intent();
                data.putExtra(MainActivity.EXTRA_RECORDED_VIDEO_FILE_PATH, filePath);
                setResult(RESULT_OK, data);
                finish();
            }

            @Override
            public void onError(Error error) {
                Timber.e("Error happens: %s", error.getMessage());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        photographer.startPreview(Collections.singletonMap(Keys.LENS_FACING, CameraCharacteristics.LENS_FACING_FRONT));
    }

    @Override
    protected void onPause() {
        photographer.stopPreview();
        super.onPause();
    }
}
