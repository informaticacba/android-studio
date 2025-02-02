/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.DeviceTableCellRenderer;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

final class VirtualDeviceTableCellRenderer extends DeviceTableCellRenderer<VirtualDevice> {
  VirtualDeviceTableCellRenderer() {
    super(VirtualDevice.class);
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    value = VirtualDevices.build((AvdInfo)value);
    return super.getTableCellRendererComponent(table, value, selected, focused, viewRowIndex, viewColumnIndex);
  }

  @Override
  protected @NotNull String getLine2(@NotNull VirtualDevice device) {
    return device.getTarget() + " | " + device.getCpuArchitecture();
  }
}
