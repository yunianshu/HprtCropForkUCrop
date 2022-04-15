package com.yunianshu.hprtcropforkucrop;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.yalantis.ucrop.util.BitmapLoadUtils;
import com.yalantis.ucrop.view.UCropView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.Calendar;
import java.util.List;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 */
public class ResultActivity extends BaseActivity {

    private static final String TAG = "ResultActivity";
    private static final String CHANNEL_ID = "3000";
    private static final int DOWNLOAD_NOTIFICATION_ID_DONE = 911;

    public static void startWithUri(@NonNull Context context, @NonNull Uri uri) {
        Intent intent = new Intent(context, ResultActivity.class);
        intent.setData(uri);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Uri uri = getIntent().getData();
        int width = 0;
        int height = 0;
        if (uri != null) {
            try {
                UCropView uCropView = findViewById(R.id.ucrop);
                uCropView.getCropImageView().setImageUri(uri, null);
                uCropView.getOverlayView().setShowCropFrame(false);
                uCropView.getOverlayView().setShowCropGrid(false);
                uCropView.getOverlayView().setDimmedColor(Color.TRANSPARENT);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && "content".equals(uri.getScheme())) {
                    TextView textViewExifWarning = findViewById(R.id.text_view_content_warning);
                    textViewExifWarning.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                Log.e(TAG, "setImageUri", e);
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }

            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            if ("content".equals(uri.getScheme())) {
                InputStream is = null;
                try {
                    is = getContentResolver().openInputStream(uri);
                    BitmapFactory.decodeStream(is, null, options);
                } catch (FileNotFoundException e) {
                    Log.d(TAG, e.getMessage(), e);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }
                }
            } else {
                File file = new File(getIntent().getData().getPath());
                BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            }

            width = options.outWidth;
            height = options.outHeight;
        }

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getString(R.string.format_crop_result_d_d, width, height));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_result, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_download) {
            saveCroppedImage();
        } else if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_STORAGE_WRITE_ACCESS_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveCroppedImage();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void saveCroppedImage() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    getString(R.string.permission_write_storage_rationale),
                    REQUEST_STORAGE_WRITE_ACCESS_PERMISSION);
        } else {
            Uri imageUri = getIntent().getData();
            if (imageUri != null && imageUri.getScheme().equals("file")) {
                try {
                    copyFileToDownloads(getIntent().getData());
                } catch (Exception e) {
                    Toast.makeText(ResultActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, imageUri.toString(), e);
                }
            } else if (BitmapLoadUtils.hasContentScheme(imageUri)){
                Toast.makeText(ResultActivity.this, getString(R.string.toast_already_saved), Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(ResultActivity.this, getString(R.string.toast_unexpected_error), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void copyFileToDownloads(Uri croppedFileUri) throws Exception {
        String downloadsDirectoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        String filename = String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), croppedFileUri.getLastPathSegment());

        File saveFile = new File(downloadsDirectoryPath, filename);

        FileInputStream inStream = new FileInputStream(new File(croppedFileUri.getPath()));
        FileOutputStream outStream = new FileOutputStream(saveFile);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();

        showNotification(saveFile);
        Toast.makeText(this, R.string.notification_image_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showNotification(@NonNull File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri fileUri = FileProvider.getUriForFile(
                this,
                getString(R.string.file_provider_authorities),
                file);

        intent.setDataAndType(fileUri, "image/*");

        List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : resInfoList) {
            grantUriPermission(
                    info.activityInfo.packageName,
                    fileUri, FLAG_GRANT_WRITE_URI_PERMISSION | FLAG_GRANT_READ_URI_PERMISSION);
        }

        NotificationCompat.Builder notificationBuilder;
        NotificationManager notificationManager = (NotificationManager) this
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(createChannel());
            }
            notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
        } else {
            notificationBuilder = new NotificationCompat.Builder(this);
        }

        notificationBuilder
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_image_saved_click_to_preview))
                .setTicker(getString(R.string.notification_image_saved))
                .setSmallIcon(R.drawable.ic_done)
                .setOngoing(false)
                .setContentIntent(PendingIntent.getActivity(this, 0, intent, 0))
                .setAutoCancel(true);
        if (notificationManager != null) {
            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID_DONE, notificationBuilder.build());
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    public NotificationChannel createChannel() {
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), importance);
        channel.setDescription(getString(R.string.channel_description));
        channel.enableLights(true);
        channel.setLightColor(Color.YELLOW);
        return channel;
    }

}
