# Session 2 driving capture — distilled evidence

Source: 10 min device-side logcat capture, 22543 lines, 2.5 MB (not committed).
Command: adb shell "nohup logcat -v time -s HUPROBE:V Gps:V -f /sdcard/hu-drive.log -r 20480 -n 40 &"

## U_TEMP_OUT transitions (module 0, code 40)
```
09-08 16:43:22.379 I/HUPROBE (29947): MAIN    U_TEMP_OUT                   c=40    [268437296]
09-08 16:50:48.896 I/HUPROBE (29947): MAIN    U_TEMP_OUT                   c=40    [268437306]
09-08 16:51:24.909 I/HUPROBE (29947): MAIN    U_TEMP_OUT                   c=40    [268437296]
09-08 16:52:22.828 I/HUPROBE (29947): MAIN    U_TEMP_OUT                   c=40    [268437306]
09-08 16:53:03.895 I/HUPROBE (29947): MAIN    U_TEMP_OUT                   c=40    [268437316]
```

## U_LAMPLET transitions (module 0, code 4) — headlight toggle
```
09-08 16:45:31.809 I/HUPROBE (29947): MAIN    U_LAMPLET                    c=4     [1]
09-08 16:49:14.956 I/HUPROBE (29947): MAIN    U_LAMPLET                    c=4     [0]
```

## U_CUR_SPEED samples with nearby GPS velocity
```
09-08 16:43:14.696 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h38m54s2ms alt=416.5 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=26, maxCn0=49, meanCn0=36}]}]
09-08 16:43:15.700 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h38m55s3ms alt=416.5 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=26, maxCn0=50, meanCn0=37}]}]
09-08 16:43:16.700 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h38m56s2ms alt=418.3 vAcc=0.0 vel=0.03601108 bear=40.21 {Bundle[{satellites=26, maxCn0=49, meanCn0=38}]}]
09-08 16:43:17.698 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.1 et=+11d4h38m57s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=36}]}]
09-08 16:43:18.697 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h38m58s2ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=37}]}]
09-08 16:43:19.698 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.1 et=+11d4h38m59s2ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=37}]}]
09-08 16:43:20.710 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h39m0s7ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=49, meanCn0=37}]}]
09-08 16:43:21.709 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.1 et=+11d4h39m1s9ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=49, meanCn0=37}]}]
09-08 16:43:22.698 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h39m2s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=49, meanCn0=37}]}]
09-08 16:43:23.699 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h39m3s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=36}]}]
09-08 16:43:24.698 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h39m4s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=37}]}]
09-08 16:43:25.700 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.1 et=+11d4h39m5s4ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=49, meanCn0=37}]}]
09-08 16:43:26.700 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h39m6s4ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=51, meanCn0=36}]}]
09-08 16:43:27.701 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h39m7s4ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=49, meanCn0=36}]}]
09-08 16:43:28.700 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h39m8s4ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=49, meanCn0=36}]}]
09-08 16:43:29.705 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.1 et=+11d4h39m9s10ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=36}]}]
09-08 16:43:30.699 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.06 et=+11d4h39m10s4ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=37}]}]
09-08 16:43:31.701 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.01 et=+11d4h39m11s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=49, meanCn0=37}]}]
09-08 16:43:32.702 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=0.98 et=+11d4h39m12s4ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=37}]}]
09-08 16:43:33.701 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.0 et=+11d4h39m13s1ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=36}]}]
09-08 16:43:34.707 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.03 et=+11d4h39m14s12ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=49, meanCn0=36}]}]
09-08 16:43:35.700 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=0.97 et=+11d4h39m15s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=51, meanCn0=36}]}]
09-08 16:43:36.699 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.1 et=+11d4h39m16s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=35}]}]
09-08 16:43:37.699 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.15 et=+11d4h39m17s0ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=35}]}]
09-08 16:43:38.698 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.2 et=+11d4h39m18s1ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=37}]}]
09-08 16:43:39.700 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.08 et=+11d4h39m19s2ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=36}]}]
09-08 16:43:40.701 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.14 et=+11d4h39m20s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=36}]}]
09-08 16:43:41.698 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.2 et=+11d4h39m21s2ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=36}]}]
09-08 16:43:42.701 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.34 et=+11d4h39m22s4ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=51, meanCn0=36}]}]
09-08 16:43:43.698 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.09 et=+11d4h39m23s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=35}]}]
09-08 16:43:44.702 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.2 et=+11d4h39m24s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=35}]}]
09-08 16:43:45.698 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.03 et=+11d4h39m25s2ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=35}]}]
09-08 16:43:46.703 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.1 et=+11d4h39m26s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=51, meanCn0=36}]}]
09-08 16:43:47.700 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.11 et=+11d4h39m27s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=36}]}]
09-08 16:43:48.702 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.11 et=+11d4h39m28s5ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=51, meanCn0=35}]}]
09-08 16:43:49.701 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.11 et=+11d4h39m29s4ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=51, meanCn0=35}]}]
09-08 16:43:50.699 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.16 et=+11d4h39m30s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=35}]}]
09-08 16:43:51.700 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.01 et=+11d4h39m31s4ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=51, meanCn0=35}]}]
09-08 16:43:52.700 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.02 et=+11d4h39m32s4ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=51, meanCn0=35}]}]
09-08 16:43:53.698 E/Gps     ( 2354):  Known Location : Location[gps 44****** ,-97******  hAcc=1.08 et=+11d4h39m33s3ms alt=418.3 vAcc=0.0 vel=0.0 bear=40.21 {Bundle[{satellites=27, maxCn0=50, meanCn0=36}]}]
```

## U_STEER_ANGLE — inert throughout
```
80]
```
