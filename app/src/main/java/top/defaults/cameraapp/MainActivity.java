package top.defaults.cameraapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.jakewharton.rxbinding2.view.RxView;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import top.defaults.cameraapp.options.Commons;

public class MainActivity extends AppCompatActivity {

    private View prepareToRecord;

    @BindView(R.id.media_dir) TextView mediaDir;
    @BindView(R.id.gallery) GridView gallery;
    private List<File> mediaFiles = new ArrayList<>();
    private MediaFileAdapter adapter;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        RxPermissions rxPermissions = new RxPermissions(this);
        prepareToRecord = findViewById(R.id.open_camera);
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

        mediaDir.setText(String.format("Media files are saved under:\n%s", Commons.MEDIA_DIR));

        adapter = new MediaFileAdapter(this, mediaFiles);
        gallery.setAdapter(adapter);
        gallery.setOnItemClickListener((parent, view, position, id) -> {
            File file = adapter.getItem(position);
            playOrViewMedia(file);
        });
    }

    private void startVideoRecordActivity() {
        Intent intent = new Intent(this, PhotographerActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        File file = new File(Commons.MEDIA_DIR);
        if (file.isDirectory()) {
            mediaFiles.clear();
            File[] files = file.listFiles();
            Arrays.sort(files, (f1, f2) -> {
                if (f1.lastModified() - f2.lastModified() == 0) {
                    return 0;
                } else {
                    return f1.lastModified() - f2.lastModified() > 0 ? -1 : 1;
                }
            });
            mediaFiles.addAll(Arrays.asList(files));
            adapter.notifyDataSetChanged();
        }
    }

    private void playOrViewMedia(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uriForFile = FileProvider.getUriForFile(MainActivity.this, getApplicationContext().getPackageName() + ".provider", file);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            uriForFile = Uri.fromFile(file);
        }
        intent.setDataAndType(uriForFile, isVideo(file) ? "video/mp4" : "image/jpg");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
        boolean isIntentSafe = activities.size() > 0;

        if (isIntentSafe) {
            startActivity(intent);
        } else {
            Toast.makeText(MainActivity.this, "No media viewer found", Toast.LENGTH_SHORT).show();
        }
    }

    private class MediaFileAdapter extends BaseAdapter {

        private List<File> files;

        private Context context;

        MediaFileAdapter(Context c, List<File> files) {
            context = c;
            this.files = files;
        }

        public int getCount() {
            return files.size();
        }

        public File getItem(int position) {
            return files.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_media, parent, false);
            }
            imageView = convertView.findViewById(R.id.item_image);
            View indicator = convertView.findViewById(R.id.item_indicator);
            File file = getItem(position);
            if (isVideo(file)) {
                indicator.setVisibility(View.VISIBLE);
            } else {
                indicator.setVisibility(View.GONE);
            }
            Glide.with(context).load(file).into(imageView);
            return convertView;
        }
    }

    private boolean isVideo(File file) {
        return file != null && file.getName().endsWith(".mp4");
    }
}
