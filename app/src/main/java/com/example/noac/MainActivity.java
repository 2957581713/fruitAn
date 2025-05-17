package com.example.noac;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.noac.resp.CommonResp;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private FrameLayout cameraFrame;
    private Handler handler = new Handler();
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable;

    private ImageView menuIcon;
    private LinearLayout dropdownMenu;
    private Button cameraButton;
    private Button galleryButton;
    private TextView dialogMessage;

    private static final String API_URL = "http://10.180.116.93:8000/yolo/yolo/detectImage"; // 替换为实际的后端 API 地址

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        menuIcon = findViewById(R.id.menu_icon);
        dropdownMenu = findViewById(R.id.dropdown_menu);
        cameraButton = findViewById(R.id.camera_button);
        galleryButton = findViewById(R.id.gallery_button);
        cameraFrame = findViewById(R.id.camera_frame);
        surfaceView = new SurfaceView(this);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        cameraFrame.addView(surfaceView);
        dialogMessage = findViewById(R.id.dialog_message);

        menuIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dropdownMenu.getVisibility() == View.GONE) {
                    dropdownMenu.setVisibility(View.VISIBLE);
                } else {
                    dropdownMenu.setVisibility(View.GONE);
                }
            }
        });

        TextView personalInfo = findViewById(R.id.personal_info);
        TextView about = findViewById(R.id.about);
        TextView logout = findViewById(R.id.logout);

        personalInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 处理个人信息点击事件
            }
        });

        about.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 处理关于点击事件
            }
        });

        logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 处理退出登录点击事件
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        });

        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 处理摄像头页面点击事件
            }
        });

        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 处理相册上传页面点击事件
                Intent intent = new Intent(MainActivity.this, GalleryUploadActivity.class);
                startActivity(intent);
            }
        });
        checkCameraPermission();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            // 延迟 500 毫秒打开摄像头，给系统一些时间完成初始化
            handler.postDelayed(this::openCamera, 500);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handler.postDelayed(this::openCamera, 500);
            } else {
                Toast.makeText(this, "未授予摄像头权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openCamera() {
        try {
            releaseCamera();
            camera = Camera.open();
            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
            Camera.Size optimalSize = getOptimalPreviewSize(previewSizes, cameraFrame.getWidth(), cameraFrame.getHeight());
            parameters.setPreviewSize(optimalSize.width, optimalSize.height);
            camera.setParameters(parameters);
            adjustSurfaceViewSize(optimalSize);
            setCameraDisplayOrientation();
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();

            // 启动定时任务
            timerRunnable = new Runnable() {
                @Override
                public void run() {
                    captureFrameAndSend();
                    timerHandler.postDelayed(this, 5000);
                }
            };
            timerHandler.postDelayed(timerRunnable, 5000);

        } catch (IOException e) {
            Log.e("MainActivity", "Failed to start camera preview: " + e.getMessage());
            Toast.makeText(this, "无法打开摄像头，请检查设备", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Log.e("MainActivity", "Failed to open camera: " + e.getMessage());
            Toast.makeText(this, "无法打开摄像头，请检查设备", Toast.LENGTH_SHORT).show();
        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    private void adjustSurfaceViewSize(Camera.Size previewSize) {
        int width = cameraFrame.getWidth();
        int height = cameraFrame.getHeight();
        double previewRatio = (double) previewSize.width / previewSize.height;
        double viewRatio = (double) width / height;

        if (previewRatio > viewRatio) {
            height = (int) (width / previewRatio);
        } else {
            width = (int) (height * previewRatio);
        }

        ViewGroup.LayoutParams params = surfaceView.getLayoutParams();
        params.width = width;
        params.height = height;
        surfaceView.setLayoutParams(params);
    }

    private void setCameraDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case android.view.Surface.ROTATION_0:
                degrees = 90;
                break;
            case android.view.Surface.ROTATION_90:
                // 调整为270度以纠正旋转问题
                degrees = 270;
                break;
            case android.view.Surface.ROTATION_180:
                degrees = 180;
                break;
            case android.view.Surface.ROTATION_270:
                // 调整为90度以纠正旋转问题
                degrees = 0;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    private void releaseCamera() {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.release();
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to release camera: " + e.getMessage());
            }
            camera = null;
            // 停止定时任务
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        try {
            camera.stopPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkCameraPermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseCamera();
        handler.removeCallbacksAndMessages(null);
        timerHandler.removeCallbacksAndMessages(null);
    }

    private void captureFrameAndSend() {
        if (camera != null) {
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    // 重新启动预览
                    camera.startPreview();

                    // 发送图片到后端
                    sendImageToBackend(data);
                }
            });
        }
    }

    private void sendImageToBackend(byte[] imageData) {
        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "image.jpg", RequestBody.create(MediaType.parse("image/jpeg"), imageData))
                .build();

        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("MainActivity", "Network request failed: " + e.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "网络请求失败，请检查网络连接", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    Gson gson = new Gson();
                    CommonResp<String> commonResp = gson.fromJson(responseData, CommonResp.class);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            System.out.println(commonResp.getMessage()+"adsadasad");
                            System.out.println(dialogMessage.getText().toString());
                            String s = dialogMessage.getText().toString();
                            String[] split = s.split("\n");
                            if(split.length>10){
                                s=commonResp.getMessage();
                                for (int i = 0; i < 10; i++) {
                                    s=s+"\n"+split[i];
                                }
                                dialogMessage.setText(s);
                            }
                            else dialogMessage.setText(commonResp.getMessage()+"\n"+s);
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "请求失败，请稍后重试", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }
}