// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.crashlytics.internal.common;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Environment;
import android.os.StatFs;
import com.google.firebase.crashlytics.BuildConfig;
import com.google.firebase.crashlytics.internal.model.Architecture;
import com.google.firebase.crashlytics.internal.stacktrace.StackTraceTrimmingStrategy;
import com.google.firebase.crashlytics.internal.stacktrace.TrimmedThrowableData;
import java.util.Locale;
import java.util.Map;

/**
 * This class is responsible for capturing information from the system and exception objects,
 * parsing them, and passing them to handlers implemented by callers to use the captured information
 * as they see fit.
 */
public class CrashlyticsReportDataCapture {

  /**
   * Defines methods for handling static report data gathered from the device, as well as system and
   * build properties.
   */
  public interface ReportDataHandler {
    void startReportDataCapture(
        String sdkVersion,
        String gmpAppId,
        String installationId,
        String buildVersion,
        String displayVersion);

    void startSessionDataCapture(String generator);

    void startSessionApplicationDataCapture(
        String packageName,
        String versionCode,
        String versionName,
        String installUuid); // TODO: Unity version

    void endSessionApplicationDataCapture();

    void startSessionOSDataCapture(String osRelease, String osCodeName, boolean isRooted);

    void endSessionOSDataCapture();

    void startSessionDeviceDataCapture(
        int arch,
        String model,
        int availableProcessors,
        long totalRam,
        long diskSpace,
        boolean isEmulator,
        int state,
        String manufacturer,
        String modelClass);

    void endSessionDeviceDataCapture();

    void endSessionDataCapture();

    void endReportDataCapture();
  }

  /** Defines methods for handling data for a given exception event. */
  public interface EventDataHandler {
    void startEventDataCapture();

    void startApplicationDataCapture(Boolean isBackground, int uiOrientation);

    void startThreadListCapture();

    void startThreadDataCapture(String name);

    void startThreadFrameListCapture();

    void startThreadFrameDataCapture(long pc, String symbol, String file, long offset);

    void endThreadFrameDataCapture();

    void endThreadFrameListCapture();

    void endThreadDataCapture();

    void endThreadListCapture();

    void startExceptionDataCapture(String type, String reason, int overflowCount);

    void startExceptionFramesListCapture();

    void startExceptionFrameDataCapture(long pc, String symbol, String file, long offset);

    void endExceptionFrameDataCapture();

    void endExceptionFramesListCapture();

    void startExceptionCauseCapture();

    void endExceptionCauseCapture();

    void endExceptionDataCapture();

    void startSignalDataCapture(String name, String code, long address);

    void endSignalDataCapture();

    void startBinaryImagesListCapture();

    void startBinaryImageDataCapture(long baseAddress, long size, String name, String uuid);

    void endBinaryImageDataCapture();

    void endBinaryImagesListCapture();

    void endApplicationDataCapture();

    void startDeviceDataCapture(
        double batteryLevel,
        int batteryVelocity,
        boolean proximityEnabled,
        int orientation,
        long usedRamBytes,
        long diskUsedBytes);

    void endDeviceDataCapture();

    void endEventDataCapture();
  }

  private static final String GENERATOR =
      String.format(Locale.US, "Crashlytics Android SDK/%s", BuildConfig.VERSION_NAME);

  private static final String SIGNAL_DEFAULT = "0";

  private final Context context;
  private final IdManager idManager;
  private final AppData appData;
  private final StackTraceTrimmingStrategy stackTraceTrimmingStrategy;

  public CrashlyticsReportDataCapture(
      Context context,
      IdManager idManager,
      AppData appData,
      StackTraceTrimmingStrategy stackTraceTrimmingStrategy) {
    this.context = context;
    this.idManager = idManager;
    this.appData = appData;
    this.stackTraceTrimmingStrategy = stackTraceTrimmingStrategy;
  }

  public void captureReportData(ReportDataHandler handler) {
    handler.startReportDataCapture(
        BuildConfig.VERSION_NAME,
        appData.googleAppId,
        idManager.getCrashlyticsInstallId(),
        appData.versionCode,
        appData.versionName);
    populateSessionData(handler);
    handler.endReportDataCapture();
  }

  public void captureEventData(
      Throwable event, Thread eventThread, int maxChainedExceptions, EventDataHandler handler) {
    captureEventData(event, eventThread, maxChainedExceptions, true, handler);
  }

  public void captureEventData(
      Throwable event,
      Thread eventThread,
      int maxChainedExceptions,
      boolean includeAllThreads,
      EventDataHandler handler) {
    final int orientation = context.getResources().getConfiguration().orientation;
    final TrimmedThrowableData trimmedEvent =
        new TrimmedThrowableData(event, stackTraceTrimmingStrategy);

    handler.startEventDataCapture();
    populateEventApplicationData(
        orientation, trimmedEvent, eventThread, maxChainedExceptions, includeAllThreads, handler);
    populateEventDeviceData(orientation, handler);
    handler.endEventDataCapture();
  }

  private void populateSessionData(ReportDataHandler handler) {
    handler.startSessionDataCapture(GENERATOR);
    populateSessionApplicationData(handler);
    populateSessionOperatingSystemData(handler);
    populateSessionDeviceData(handler);
    handler.endSessionDataCapture();
  }

  private void populateSessionApplicationData(ReportDataHandler handler) {
    handler.startSessionApplicationDataCapture(
        idManager.getAppIdentifier(),
        appData.versionCode,
        appData.versionName,
        idManager.getCrashlyticsInstallId());
    handler.endSessionApplicationDataCapture();
  }

  private void populateSessionOperatingSystemData(ReportDataHandler handler) {
    handler.startSessionOSDataCapture(
        VERSION.RELEASE, VERSION.CODENAME, CommonUtils.isRooted(context));
    handler.endSessionOSDataCapture();
  }

  private void populateSessionDeviceData(ReportDataHandler handler) {
    final StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
    final int arch = Architecture.getDeviceArchitecture().getValue();
    final int availableProcessors = Runtime.getRuntime().availableProcessors();
    final long totalRam = CommonUtils.getTotalRamInBytes();
    final long diskSpace = (long) statFs.getBlockCount() * (long) statFs.getBlockSize();
    final boolean isEmulator = CommonUtils.isEmulator(context);
    final int state = CommonUtils.getDeviceState(context);
    final String manufacturer = Build.MANUFACTURER;
    final String modelClass = Build.PRODUCT;
    handler.startSessionDeviceDataCapture(
        arch,
        Build.MODEL,
        availableProcessors,
        totalRam,
        diskSpace,
        isEmulator,
        state,
        manufacturer,
        modelClass);
    handler.endSessionDeviceDataCapture();
  }

  private void populateEventApplicationData(
      int orientation,
      TrimmedThrowableData trimmedEvent,
      Thread eventThread,
      int maxChainedExceptions,
      boolean includeAllThreads,
      EventDataHandler handler) {
    Boolean isBackground = null;
    final RunningAppProcessInfo runningAppProcessInfo =
        CommonUtils.getAppProcessInfo(appData.packageName, context);
    if (runningAppProcessInfo != null) {
      // Several different types of "background" states, easiest to check for not foreground.
      isBackground =
          runningAppProcessInfo.importance
              != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }

    handler.startApplicationDataCapture(isBackground, orientation);
    populateExecutionData(
        trimmedEvent, eventThread, maxChainedExceptions, includeAllThreads, handler);
    handler.endApplicationDataCapture();
  }

  private void populateEventDeviceData(int orientation, EventDataHandler handler) {
    final BatteryState battery = BatteryState.get(context);
    final double batteryLevel = (double) battery.getBatteryLevel();
    final int batteryVelocity = battery.getBatteryVelocity();
    final boolean proximityEnabled = CommonUtils.getProximitySensorEnabled(context);
    final long usedRamBytes =
        CommonUtils.getTotalRamInBytes() - CommonUtils.calculateFreeRamInBytes(context);
    final long diskUsedBytes =
        CommonUtils.calculateUsedDiskSpaceInBytes(Environment.getDataDirectory().getPath());

    handler.startDeviceDataCapture(
        batteryLevel, batteryVelocity, proximityEnabled, orientation, usedRamBytes, diskUsedBytes);
    handler.endDeviceDataCapture();
  }

  private void populateExecutionData(
      TrimmedThrowableData trimmedEvent,
      Thread eventThread,
      int maxChainedExceptions,
      boolean includeAllThreads,
      EventDataHandler handler) {
    populateThreadsList(trimmedEvent, eventThread, includeAllThreads, handler);
    populateExceptionData(trimmedEvent, 0, maxChainedExceptions, handler);
    populateSignalData(handler);
    populateBinaryImagesList(handler);
  }

  private void populateThreadsList(
      TrimmedThrowableData trimmedEvent,
      Thread eventThread,
      boolean includeAllThreads,
      EventDataHandler handler) {
    handler.startThreadListCapture();

    populateThreadData(eventThread, trimmedEvent.stacktrace, handler);

    if (includeAllThreads) {
      final Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
      for (Map.Entry<Thread, StackTraceElement[]> entry : allStackTraces.entrySet()) {
        final Thread thread = entry.getKey();
        // Skip the event thread, since we populated it first.
        if (!thread.equals(eventThread)) {
          populateThreadData(
              thread, stackTraceTrimmingStrategy.getTrimmedStackTrace(entry.getValue()), handler);
        }
      }
    }

    handler.endThreadListCapture();
  }

  private void populateThreadData(
      Thread thread, StackTraceElement[] stacktrace, EventDataHandler handler) {
    final String name = thread.getName();
    handler.startThreadDataCapture(name);
    stacktrace = (stacktrace != null) ? stacktrace : new StackTraceElement[0];
    populateThreadFramesList(stacktrace, handler);
    handler.endThreadDataCapture();
  }

  private void populateThreadFramesList(StackTraceElement[] stacktrace, EventDataHandler handler) {
    handler.startThreadFrameListCapture();
    for (StackTraceElement element : stacktrace) {
      populateThreadFrameData(element, handler);
    }
    handler.endThreadFrameListCapture();
  }

  private void populateThreadFrameData(StackTraceElement element, EventDataHandler handler) {
    populateFrameData(element, handler::startThreadFrameDataCapture);
    handler.endThreadFrameDataCapture();
  }

  private void populateExceptionData(
      TrimmedThrowableData trimmedEvent,
      int chainDepth,
      int maxChainedExceptions,
      EventDataHandler handler) {
    final String type = trimmedEvent.className;
    final String reason = trimmedEvent.localizedMessage;
    final StackTraceElement[] stacktrace =
        trimmedEvent.stacktrace != null ? trimmedEvent.stacktrace : new StackTraceElement[0];
    final TrimmedThrowableData cause = trimmedEvent.cause;

    int overflowCount = 0;
    if (chainDepth >= maxChainedExceptions) {
      TrimmedThrowableData skipped = cause;
      while (skipped != null) {
        skipped = skipped.cause;
        ++overflowCount;
      }
    }

    handler.startExceptionDataCapture(type, reason, overflowCount);
    populateExceptionFramesList(stacktrace, handler);
    if (cause != null && overflowCount == 0) {
      handler.startExceptionCauseCapture();
      populateExceptionData(cause, chainDepth + 1, maxChainedExceptions, handler);
      handler.endExceptionCauseCapture();
    }
    handler.endExceptionDataCapture();
  }

  private void populateExceptionFramesList(
      StackTraceElement[] stacktrace, EventDataHandler handler) {
    handler.startExceptionFramesListCapture();
    for (StackTraceElement element : stacktrace) {
      populateExceptionFrameData(element, handler);
    }
    handler.endExceptionFramesListCapture();
  }

  private void populateExceptionFrameData(StackTraceElement element, EventDataHandler handler) {
    populateFrameData(element, handler::startExceptionFrameDataCapture);
    handler.endExceptionFrameDataCapture();
  }

  private void populateFrameData(StackTraceElement element, FrameHandler handler) {
    long pc = 0L;
    if (element.isNativeMethod()) {
      // certain ProGuard configs will result in negative line numbers,
      // which cannot - by design - be mapped back to the source file.
      pc = Math.max(element.getLineNumber(), 0L);
    }

    final String symbol = element.getClassName() + "." + element.getMethodName();
    final String file = element.getFileName();

    // Same as with pc, ProGuard sometimes generates negative numbers.
    // Here the field is optional, so we can just skip it if we're negative.
    long offset = 0L;
    if (!element.isNativeMethod() && element.getLineNumber() > 0) {
      offset = element.getLineNumber();
    }

    handler.startFrameDataCapture(pc, symbol, file, offset);
  }

  private void populateBinaryImagesList(EventDataHandler handler) {
    handler.startBinaryImagesListCapture();
    populateBinaryImageData(handler);
    handler.endBinaryImagesListCapture();
  }

  private void populateBinaryImageData(EventDataHandler handler) {
    final long baseAddress = 0L;
    final long size = 0L;
    final String name = appData.packageName;
    final String uuid = appData.buildId;
    handler.startBinaryImageDataCapture(baseAddress, size, name, uuid);
    handler.endBinaryImageDataCapture();
  }

  private void populateSignalData(EventDataHandler handler) {
    handler.startSignalDataCapture(SIGNAL_DEFAULT, SIGNAL_DEFAULT, 0L);
    handler.endSignalDataCapture();
  }

  /** Functional interface for handling individual frames */
  private interface FrameHandler {
    void startFrameDataCapture(long pc, String symbol, String file, long offset);
  }
}
