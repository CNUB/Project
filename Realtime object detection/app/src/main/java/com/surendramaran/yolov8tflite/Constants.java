package com.surendramaran.yolov8tflite;

import java.util.Arrays;
import java.util.List;

public class Constants {
    public static final String MODEL_PATH = "model.tflite";
    public static final String LABELS_PATH = "labels.txt";
    public static final List<String> FILTERED_CLASSES = Arrays.asList("포장도로", "데크", "보도블럭");
    // Private constructor to prevent instantiation
    private Constants() {
    }
}
