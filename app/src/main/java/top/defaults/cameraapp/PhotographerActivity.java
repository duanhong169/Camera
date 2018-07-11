package top.defaults.cameraapp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import timber.log.Timber;
import top.defaults.camera.CameraView;
import top.defaults.camera.CanvasDrawer;
import top.defaults.camera.Error;
import top.defaults.camera.Photographer;
import top.defaults.camera.PhotographerFactory;
import top.defaults.camera.PhotographerHelper;
import top.defaults.camera.SimpleOnEventListener;
import top.defaults.camera.Size;
import top.defaults.camera.Utils;
import top.defaults.camera.Values;
import top.defaults.cameraapp.dialog.PickerDialog;
import top.defaults.cameraapp.dialog.SimplePickerDialog;
import top.defaults.cameraapp.options.AspectRatioItem;
import top.defaults.cameraapp.options.Commons;
import top.defaults.cameraapp.options.SizeItem;
import top.defaults.view.TextButton;

public class PhotographerActivity extends AppCompatActivity {

    Photographer photographer;
    PhotographerHelper photographerHelper;
    private boolean isRecordingVideo;

    @BindView(R.id.preview) CameraView preview;
    @BindView(R.id.status) TextView statusTextView;

    @BindView(R.id.chooseSize) TextButton chooseSizeButton;
    @BindView(R.id.flash) TextButton flashTextButton;
    @BindView(R.id.flash_torch) ImageButton flashTorch;

    @BindView(R.id.switch_mode) TextButton switchButton;
    @BindView(R.id.action) ImageButton actionButton;
    @BindView(R.id.flip) ImageButton flipButton;

    @BindView(R.id.zoomValue) TextView zoomValueTextView;

    private int currentFlash = Values.FLASH_AUTO;

    private static final int[] FLASH_OPTIONS = {
            Values.FLASH_AUTO,
            Values.FLASH_OFF,
            Values.FLASH_ON,
    };

    private static final int[] FLASH_ICONS = {
            R.drawable.ic_flash_auto,
            R.drawable.ic_flash_off,
            R.drawable.ic_flash_on,
    };

    private static final int[] FLASH_TITLES = {
            R.string.flash_auto,
            R.string.flash_off,
            R.string.flash_on,
    };

    @OnClick(R.id.chooseRatio)
    void chooseRatio() {
        List<AspectRatioItem> supportedAspectRatios = Commons.wrapItems(photographer.getSupportedAspectRatios(), AspectRatioItem::new);
        if (supportedAspectRatios != null) {
            SimplePickerDialog<AspectRatioItem> dialog = SimplePickerDialog.create(new PickerDialog.ActionListener<AspectRatioItem>() {
                @Override
                public void onCancelClick(PickerDialog<AspectRatioItem> dialog) { }

                @Override
                public void onDoneClick(PickerDialog<AspectRatioItem> dialog) {
                    AspectRatioItem item = dialog.getSelectedItem(AspectRatioItem.class);
                    photographer.setAspectRatio(item.get());
                }
            });
            dialog.setItems(supportedAspectRatios);
            dialog.setInitialItem(Commons.findEqual(supportedAspectRatios, photographer.getAspectRatio()));
            dialog.show(getFragmentManager(), "aspectRatio");
        }
    }

    @OnClick(R.id.chooseSize)
    void chooseSize() {
        Size selectedSize = null;
        List<SizeItem> supportedSizes = null;
        int mode = photographer.getMode();
        if (mode == Values.MODE_VIDEO) {
            Set<Size> videoSizes = photographer.getSupportedVideoSizes();
            selectedSize = photographer.getVideoSize();
            if (videoSizes != null && videoSizes.size() > 0) {
                supportedSizes = Commons.wrapItems(videoSizes, SizeItem::new);
            }
        } else if (mode == Values.MODE_IMAGE) {
            Set<Size> imageSizes = photographer.getSupportedImageSizes();
            selectedSize = photographer.getImageSize();
            if (imageSizes != null && imageSizes.size() > 0) {
                supportedSizes = Commons.wrapItems(imageSizes, SizeItem::new);
            }
        }

        if (supportedSizes != null) {
            SimplePickerDialog<SizeItem> dialog = SimplePickerDialog.create(new PickerDialog.ActionListener<SizeItem>() {
                @Override
                public void onCancelClick(PickerDialog<SizeItem> dialog) { }

                @Override
                public void onDoneClick(PickerDialog<SizeItem> dialog) {
                    SizeItem sizeItem = dialog.getSelectedItem(SizeItem.class);
                    if (mode == Values.MODE_VIDEO) {
                        photographer.setVideoSize(sizeItem.get());
                    } else {
                        photographer.setImageSize(sizeItem.get());
                    }
                }
            });
            dialog.setItems(supportedSizes);
            dialog.setInitialItem(Commons.findEqual(supportedSizes, selectedSize));
            dialog.show(getFragmentManager(), "cameraOutputSize");
        }
    }

    @OnCheckedChanged(R.id.fillSpace)
    void onFillSpaceChecked(boolean checked) {
        preview.setFillSpace(checked);
    }

    @OnCheckedChanged(R.id.enableZoom)
    void onEnableZoomChecked(boolean checked) {
        preview.setPinchToZoom(checked);
    }

    @OnClick(R.id.flash)
    void flash() {
        currentFlash = (currentFlash + 1) % FLASH_OPTIONS.length;
        flashTextButton.setText(FLASH_TITLES[currentFlash]);
        flashTextButton.setCompoundDrawablesWithIntrinsicBounds(FLASH_ICONS[currentFlash], 0, 0, 0);
        photographer.setFlash(FLASH_OPTIONS[currentFlash]);
    }

    @OnClick(R.id.action)
    void action() {
        int mode = photographer.getMode();
        if (mode == Values.MODE_VIDEO) {
            if (isRecordingVideo) {
                finishRecordingIfNeeded();
            } else {
                isRecordingVideo = true;
                photographer.startRecording(null);
                actionButton.setEnabled(false);
            }
        } else if (mode == Values.MODE_IMAGE) {
            photographer.takePicture();
        }
    }

    @OnClick(R.id.flash_torch)
    void toggleFlashTorch() {
        int flash = photographer.getFlash();
        if (flash == Values.FLASH_TORCH) {
            photographer.setFlash(currentFlash);
            flashTextButton.setEnabled(true);
            flashTorch.setImageResource(R.drawable.light_off);
        } else {
            photographer.setFlash(Values.FLASH_TORCH);
            flashTextButton.setEnabled(false);
            flashTorch.setImageResource(R.drawable.light_on);
        }
    }

    @OnClick(R.id.switch_mode)
    void switchMode() {
        photographerHelper.switchMode();
    }

    @OnClick(R.id.flip)
    void flip() {
        photographerHelper.flip();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_record);
        ButterKnife.bind(this);

        preview.setFocusIndicatorDrawer(new CanvasDrawer() {
            private static final int SIZE = 300;
            private static final int LINE_LENGTH = 50;

            @Override
            public Paint[] initPaints() {
                Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                focusPaint.setStyle(Paint.Style.STROKE);
                focusPaint.setStrokeWidth(2);
                focusPaint.setColor(Color.WHITE);
                return new Paint[]{ focusPaint };
            }

            @Override
            public void draw(Canvas canvas, Point point, Paint[] paints) {
                if (paints == null || paints.length == 0) return;

                int left = point.x - (SIZE / 2);
                int top = point.y - (SIZE / 2);
                int right = point.x + (SIZE / 2);
                int bottom = point.y + (SIZE / 2);

                Paint paint = paints[0];

                canvas.drawLine(left, top + LINE_LENGTH, left, top, paint);
                canvas.drawLine(left, top, left + LINE_LENGTH, top, paint);

                canvas.drawLine(right - LINE_LENGTH, top, right, top, paint);
                canvas.drawLine(right, top, right, top + LINE_LENGTH, paint);

                canvas.drawLine(right, bottom - LINE_LENGTH, right, bottom, paint);
                canvas.drawLine(right, bottom, right - LINE_LENGTH, bottom, paint);

                canvas.drawLine(left + LINE_LENGTH, bottom, left, bottom, paint);
                canvas.drawLine(left, bottom, left, bottom - LINE_LENGTH, paint);
            }
        });
        photographer = PhotographerFactory.createPhotographerWithCamera2(this, preview);
        photographerHelper = new PhotographerHelper(photographer);
        photographerHelper.setFileDir(Commons.MEDIA_DIR);
        photographer.setOnEventListener(new SimpleOnEventListener() {
            @Override
            public void onDeviceConfigured() {
                if (photographer.getMode() == Values.MODE_VIDEO) {
                    actionButton.setImageResource(R.drawable.record);
                    chooseSizeButton.setText(R.string.video_size);
                    switchButton.setText(R.string.video_mode);
                } else {
                    actionButton.setImageResource(R.drawable.ic_camera);
                    chooseSizeButton.setText(R.string.image_size);
                    switchButton.setText(R.string.image_mode);
                }
            }

            @Override
            public void onZoomChanged(float zoom) {
                zoomValueTextView.setText(String.format(Locale.getDefault(), "%.1fX", zoom));
            }

            @Override
            public void onStartRecording() {
                switchButton.setVisibility(View.INVISIBLE);
                flipButton.setVisibility(View.INVISIBLE);
                actionButton.setEnabled(true);
                actionButton.setImageResource(R.drawable.stop);
                statusTextView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFinishRecording(String filePath) {
                announcingNewFile(filePath);
            }

            @Override
            public void onShotFinished(String filePath) {
                announcingNewFile(filePath);
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
        enterFullscreen();
        photographer.startPreview();
    }

    @Override
    protected void onPause() {
        finishRecordingIfNeeded();
        photographer.stopPreview();
        super.onPause();
    }

    private void enterFullscreen() {
        View decorView = getWindow().getDecorView();
        decorView.setBackgroundColor(Color.BLACK);
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void finishRecordingIfNeeded() {
        if (isRecordingVideo) {
            isRecordingVideo = false;
            photographer.finishRecording();
            statusTextView.setVisibility(View.INVISIBLE);
            switchButton.setVisibility(View.VISIBLE);
            flipButton.setVisibility(View.VISIBLE);
            actionButton.setEnabled(true);
            actionButton.setImageResource(R.drawable.record);
        }
    }

    private void announcingNewFile(String filePath) {
        Toast.makeText(PhotographerActivity.this, "File: " + filePath, Toast.LENGTH_SHORT).show();
        Utils.addMediaToGallery(PhotographerActivity.this, filePath);
    }
}
