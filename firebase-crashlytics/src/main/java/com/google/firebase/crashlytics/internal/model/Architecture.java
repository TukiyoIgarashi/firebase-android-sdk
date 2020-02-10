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

package com.google.firebase.crashlytics.internal.model;

import android.os.Build;
import android.text.TextUtils;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum Architecture {
  ARMV6(5),
  ARMV7(6),
  ARM64(9),
  X86_32(0),
  X86_64(1),
  UNKNOWN(7);

  private static final Map<String, Architecture> nameMapping = new HashMap<>();

  static {
    nameMapping.put("armeabi", ARMV6);
    nameMapping.put("armeabi-v7a", ARMV7);
    nameMapping.put("arm64-v8a", ARM64);
    nameMapping.put("x86", X86_32);
    nameMapping.put("x86_64", X86_64);
  }

  public static Architecture getDeviceArchitecture() {
    final String primaryAbi = Build.CPU_ABI;

    if (TextUtils.isEmpty(primaryAbi)) {
      return UNKNOWN;
    }

    final Architecture arch = nameMapping.get(primaryAbi.toLowerCase(Locale.US));
    if (arch == null) {
      return UNKNOWN;
    }

    return arch;
  }

  private final int value;

  Architecture(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
