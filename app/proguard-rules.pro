
# RNF05: evita que R8 elimine clases usadas por reflexion en TFLite/OpenCV
-keep class org.tensorflow.lite.** { *; }
-keep class org.opencv.** { *; }
