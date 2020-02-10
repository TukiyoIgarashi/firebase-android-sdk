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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.crashlytics.internal.common.CrashlyticsReportDataCapture.EventDataHandler;
import com.google.firebase.crashlytics.internal.common.CrashlyticsReportDataCapture.ReportDataHandler;
import com.google.firebase.crashlytics.internal.stacktrace.StackTraceTrimmingStrategy;
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class CrashlyticsReportDataCaptureTest {

  private CrashlyticsReportDataCapture dataCapture;

  private IdManager idManager;
  private AppData appData;

  @Mock private StackTraceTrimmingStrategy stackTraceTrimmingStrategy;

  @Mock private FirebaseInstanceIdInternal instanceIdMock;

  @Mock private ReportDataHandler mockReportDataHandler;

  @Mock private EventDataHandler mockEventDataHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(instanceIdMock.getId()).thenReturn("installId");
    final Context context = ApplicationProvider.getApplicationContext();
    idManager = new IdManager(context, context.getPackageName(), instanceIdMock);
    appData = AppData.create(context, idManager, "googleAppId", "buildId");
    dataCapture =
        new CrashlyticsReportDataCapture(context, idManager, appData, stackTraceTrimmingStrategy);
  }

  @Test
  public void captureReportData_callsAllHandlerMethodsInOrder() {
    final InOrder reportMethods = inOrder(mockReportDataHandler);
    final InOrder sessionMethods = inOrder(mockReportDataHandler);
    final InOrder sessionAppMethods = inOrder(mockReportDataHandler);
    final InOrder sessionOSMethods = inOrder(mockReportDataHandler);
    final InOrder sessionDeviceMethods = inOrder(mockReportDataHandler);
    dataCapture.captureReportData(mockReportDataHandler);

    reportMethods
        .verify(mockReportDataHandler)
        .startReportDataCapture(
            anyString(), eq(appData.googleAppId), anyString(), anyString(), anyString());
    sessionMethods.verify(mockReportDataHandler).startSessionDataCapture(anyString());
    sessionAppMethods
        .verify(mockReportDataHandler)
        .startSessionApplicationDataCapture(
            eq(appData.packageName),
            eq(appData.versionCode),
            eq(appData.versionName),
            eq(idManager.getCrashlyticsInstallId()));
    sessionAppMethods.verify(mockReportDataHandler).endSessionApplicationDataCapture();
    sessionOSMethods
        .verify(mockReportDataHandler)
        .startSessionOSDataCapture(anyString(), anyString(), anyBoolean());
    sessionOSMethods.verify(mockReportDataHandler).endSessionOSDataCapture();
    sessionDeviceMethods
        .verify(mockReportDataHandler)
        .startSessionDeviceDataCapture(
            anyInt(),
            anyString(),
            anyInt(),
            anyLong(),
            anyLong(),
            anyBoolean(),
            anyInt(),
            anyString(),
            anyString());
    sessionDeviceMethods.verify(mockReportDataHandler).endSessionDeviceDataCapture();
    sessionMethods.verify(mockReportDataHandler).endSessionDataCapture();
    reportMethods.verify(mockReportDataHandler).endReportDataCapture();
  }

  @Test
  public void captureEventData_callsAllHandlerMethodsInOrder() {
    final IllegalStateException e = new IllegalStateException();
    when(stackTraceTrimmingStrategy.getTrimmedStackTrace(e.getStackTrace()))
        .thenReturn(e.getStackTrace());

    final InOrder eventMethods = inOrder(mockEventDataHandler);
    final InOrder threadMethods = inOrder(mockEventDataHandler);
    final InOrder exceptionMethods = inOrder(mockEventDataHandler);
    final InOrder signalMethods = inOrder(mockEventDataHandler);
    final InOrder binaryImagesMethods = inOrder(mockEventDataHandler);
    final InOrder applicationMethods = inOrder(mockEventDataHandler);
    final InOrder deviceMethods = inOrder(mockEventDataHandler);

    dataCapture.captureEventData(e, Thread.currentThread(), 8, false, mockEventDataHandler);

    eventMethods.verify(mockEventDataHandler).startEventDataCapture();
    applicationMethods
        .verify(mockEventDataHandler)
        .startApplicationDataCapture(anyBoolean(), anyInt());

    threadMethods.verify(mockEventDataHandler).startThreadListCapture();
    threadMethods
        .verify(mockEventDataHandler)
        .startThreadDataCapture(Thread.currentThread().getName());
    threadMethods.verify(mockEventDataHandler).startThreadFrameListCapture();
    for (int i = 0; i < e.getStackTrace().length; ++i) {
      threadMethods
          .verify(mockEventDataHandler)
          .startThreadFrameDataCapture(anyLong(), anyString(), anyString(), anyLong());
      threadMethods.verify(mockEventDataHandler).endThreadFrameDataCapture();
    }
    threadMethods.verify(mockEventDataHandler).endThreadFrameListCapture();
    threadMethods.verify(mockEventDataHandler).endThreadDataCapture();
    threadMethods.verify(mockEventDataHandler).endThreadListCapture();

    exceptionMethods
        .verify(mockEventDataHandler)
        .startExceptionDataCapture(anyString(), nullable(String.class), eq(0));
    exceptionMethods.verify(mockEventDataHandler).startExceptionFramesListCapture();
    exceptionMethods
        .verify(mockEventDataHandler)
        .startExceptionFrameDataCapture(anyLong(), anyString(), anyString(), anyLong());
    exceptionMethods.verify(mockEventDataHandler).endExceptionFrameDataCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionFramesListCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionDataCapture();

    signalMethods
        .verify(mockEventDataHandler)
        .startSignalDataCapture(anyString(), anyString(), anyLong());
    signalMethods.verify(mockEventDataHandler).endSignalDataCapture();

    binaryImagesMethods.verify(mockEventDataHandler).startBinaryImagesListCapture();
    binaryImagesMethods
        .verify(mockEventDataHandler)
        .startBinaryImageDataCapture(anyLong(), anyLong(), anyString(), anyString());
    binaryImagesMethods.verify(mockEventDataHandler).endBinaryImageDataCapture();
    binaryImagesMethods.verify(mockEventDataHandler).endBinaryImagesListCapture();

    applicationMethods.verify(mockEventDataHandler).endApplicationDataCapture();

    deviceMethods
        .verify(mockEventDataHandler)
        .startDeviceDataCapture(
            anyDouble(), anyInt(), anyBoolean(), anyInt(), anyLong(), anyLong());
    deviceMethods.verify(mockEventDataHandler).endDeviceDataCapture();

    eventMethods.verify(mockEventDataHandler).endEventDataCapture();
  }

  @Test
  public void captureEventData_withChainedExceptions_callsAppropriateMethodsInOrder() {
    final IllegalArgumentException cause2 = new IllegalArgumentException();
    final IllegalStateException cause = new IllegalStateException(cause2);
    final RuntimeException ex = new RuntimeException(cause);
    when(stackTraceTrimmingStrategy.getTrimmedStackTrace(ex.getStackTrace()))
        .thenReturn(ex.getStackTrace());

    final InOrder exceptionMethods = inOrder(mockEventDataHandler);

    dataCapture.captureEventData(ex, Thread.currentThread(), 8, mockEventDataHandler);
    exceptionMethods
        .verify(mockEventDataHandler)
        .startExceptionDataCapture(anyString(), nullable(String.class), eq(0));
    exceptionMethods.verify(mockEventDataHandler).startExceptionFramesListCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionFramesListCapture();
    exceptionMethods.verify(mockEventDataHandler).startExceptionCauseCapture();
    exceptionMethods
        .verify(mockEventDataHandler)
        .startExceptionDataCapture(anyString(), nullable(String.class), eq(0));
    exceptionMethods.verify(mockEventDataHandler).startExceptionFramesListCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionFramesListCapture();
    exceptionMethods.verify(mockEventDataHandler).startExceptionCauseCapture();
    exceptionMethods
        .verify(mockEventDataHandler)
        .startExceptionDataCapture(anyString(), nullable(String.class), eq(0));
    exceptionMethods.verify(mockEventDataHandler).startExceptionFramesListCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionFramesListCapture();

    // Make sure we don't start a new cause
    exceptionMethods.verify(mockEventDataHandler, never()).startExceptionCauseCapture();

    exceptionMethods.verify(mockEventDataHandler).endExceptionDataCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionCauseCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionDataCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionCauseCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionDataCapture();
  }

  @Test
  public void captureEventData_withChainedExceptionsOverMax_callsAppropriateMethodsInOrder() {
    final NullPointerException cause3 = new NullPointerException();
    final IllegalArgumentException cause2 = new IllegalArgumentException(cause3);
    final IllegalStateException cause = new IllegalStateException(cause2);
    final RuntimeException ex = new RuntimeException(cause);
    when(stackTraceTrimmingStrategy.getTrimmedStackTrace(ex.getStackTrace()))
        .thenReturn(ex.getStackTrace());

    final InOrder exceptionMethods = inOrder(mockEventDataHandler);

    dataCapture.captureEventData(ex, Thread.currentThread(), 1, mockEventDataHandler);
    exceptionMethods
        .verify(mockEventDataHandler)
        .startExceptionDataCapture(anyString(), nullable(String.class), eq(0));
    exceptionMethods.verify(mockEventDataHandler).startExceptionFramesListCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionFramesListCapture();
    exceptionMethods.verify(mockEventDataHandler).startExceptionCauseCapture();
    exceptionMethods
        .verify(mockEventDataHandler)
        .startExceptionDataCapture(anyString(), nullable(String.class), eq(2));
    exceptionMethods.verify(mockEventDataHandler).startExceptionFramesListCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionFramesListCapture();

    // Make sure we don't start a new cause
    exceptionMethods.verify(mockEventDataHandler, never()).startExceptionCauseCapture();

    exceptionMethods.verify(mockEventDataHandler).endExceptionDataCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionCauseCapture();
    exceptionMethods.verify(mockEventDataHandler).endExceptionDataCapture();
  }

  @Test
  public void captureEventData_withAllThreads_callsAppropriateMethodsInOrder() {
    final RuntimeException ex = new RuntimeException();
    when(stackTraceTrimmingStrategy.getTrimmedStackTrace(ex.getStackTrace()))
        .thenReturn(ex.getStackTrace());

    final InOrder threadMethods = inOrder(mockEventDataHandler);

    final int numThreads = Thread.getAllStackTraces().size();

    dataCapture.captureEventData(ex, Thread.currentThread(), 8, mockEventDataHandler);

    threadMethods.verify(mockEventDataHandler).startThreadListCapture();
    for (int i = 0; i < numThreads; ++i) {
      threadMethods.verify(mockEventDataHandler).startThreadDataCapture(anyString());
      threadMethods.verify(mockEventDataHandler).endThreadDataCapture();
    }
    threadMethods.verify(mockEventDataHandler).endThreadListCapture();
  }
}
