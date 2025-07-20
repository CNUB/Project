package com.surendramaran.yolov8tflite;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements Detector.DetectorListener {

    private ActivityMainBinding binding;
    private boolean isFrontCamera = false;

    private Preview preview;
    private ImageAnalysis imageAnalyzer;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private Detector detector;

    private ExecutorService cameraExecutor;

    // 탐지 대상 클래스 리스트
    private static final List<String> ALERT_CLASSES = Arrays.asList(
            "크랙", "결손", "들뜸", "침하",
            "자동차(불법주정차)", "보행자", "유모차", "전동킥보드",
            "자전거", "휠체어", "오토바이", "동물",
            "볼라드", "분리봉", "난간", "표지판", "신호등"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        createNotificationChannel(); // 알림 채널 생성
        requestNotificationPermission(); // 권한 요청

        detector = new Detector(getApplicationContext(), Constants.MODEL_PATH, Constants.LABELS_PATH, this);
        detector.setup();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "DETECTION_ALERT_CHANNEL";
            String channelName = "Detection Alerts";
            String channelDescription = "Alerts for detected objects";

            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(channelDescription);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(this).get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) throw new IllegalStateException("Camera initialization failed.");

        int rotation = binding.viewFinder.getDisplay().getRotation();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build();

        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();

        imageAnalyzer.setAnalyzer(cameraExecutor, imageProxy -> {
            Bitmap bitmapBuffer = Bitmap.createBitmap(imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
            imageProxy.getPlanes()[0].getBuffer().rewind();
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());
            imageProxy.close();

            Matrix matrix = new Matrix();
            matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());

            if (isFrontCamera) {
                matrix.postScale(-1f, 1f, imageProxy.getWidth(), imageProxy.getHeight());
            }

            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.getWidth(), bitmapBuffer.getHeight(), matrix, true);
            detector.detect(rotatedBitmap);
        });

        cameraProvider.unbindAll();

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);
            preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());
        } catch (Exception exc) {
            Log.e(TAG, "Use case binding failed", exc);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(getBaseContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                if (result.containsKey(Manifest.permission.CAMERA) && result.get(Manifest.permission.CAMERA)) {
                    startCamera();
                }
            }
    );

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detector.clear();
        cameraExecutor.shutdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }

    @Override
    public void onEmptyDetect() {
        runOnUiThread(() -> binding.overlay.invalidate());
    }

    @Override
    public void onDetect(@NonNull List<BoundingBox> boundingBoxes, long inferenceTime) {
        runOnUiThread(() -> {
            binding.inferenceTime.setText(inferenceTime + "ms");
            binding.overlay.setResults(boundingBoxes);
            binding.overlay.invalidate();

            // 탐지된 클래스 중 알람 대상 확인
            for (BoundingBox box : boundingBoxes) {
                if (ALERT_CLASSES.contains(box.getClsName())) {
                    showDetectionAlert(box.getClsName());
                    break;
                }
            }
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
    }

    private void showDetectionAlert(String className) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "알림 권한이 없습니다.");
            return; // 권한 없을 시 알림 표시 중단
        }

        String channelId = "DETECTION_ALERT_CHANNEL";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_alert) // 기본 아이콘 사용
                .setContentTitle("탐지 알림")
                .setContentText(className + "이(가) 탐지되었습니다!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(5000); // 5초 후 자동 닫힘

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }

    private static final String TAG = "Camera";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
}
