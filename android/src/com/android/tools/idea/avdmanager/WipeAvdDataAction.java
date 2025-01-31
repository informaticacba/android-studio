/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.analytics.UsageTracker;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Messages;
import java.awt.event.ActionEvent;
import org.jetbrains.annotations.NotNull;

public class WipeAvdDataAction extends AvdUiAction {
  private final boolean myLogDeviceManagerEvents;

  WipeAvdDataAction(@NotNull AvdInfoProvider avdInfoProvider, boolean logDeviceManagerEvents) {
    super(avdInfoProvider, "Wipe Data", "Wipe the user data of this AVD", AllIcons.Actions.Edit);
    myLogDeviceManagerEvents = logDeviceManagerEvents;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (myLogDeviceManagerEvents) {
      DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.VIRTUAL_WIPE_DATA_ACTION)
        .build();

      AndroidStudioEvent.Builder builder = AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.DEVICE_MANAGER)
        .setDeviceManagerEvent(event);

      UsageTracker.log(builder);
    }

    AvdManagerConnection connection = AvdManagerConnection.getDefaultAvdManagerConnection();
    AvdInfo avdInfo = getAvdInfo();
    if (avdInfo == null) {
      return;
    }
    if (connection.isAvdRunning(avdInfo)) {
      Messages.showErrorDialog(myAvdInfoProvider.getAvdProviderComponent(),
                               "The selected AVD is currently running in the Emulator. " +
                               "Please exit the emulator instance and try wiping again.", "Cannot Wipe A Running AVD");
      return;
    }
    int result = Messages.showYesNoDialog(myAvdInfoProvider.getAvdProviderComponent(),
                                          "Do you really want to wipe user files from AVD " + avdInfo.getName() + "?",
                                          "Confirm Data Wipe", AllIcons.General.QuestionDialog);
    if (result == Messages.YES) {
      connection.wipeUserData(avdInfo);
      refreshAvds();
    }
  }

  @Override
  public boolean isEnabled() {
    return getAvdInfo() != null;
  }
}
