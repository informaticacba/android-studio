/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.updateSettings.impl.UpdateData.parseUpdateData;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.updateSettings.impl.UpdateStrategy;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.JDOMUtil;
import org.intellij.lang.annotations.Language;
import org.junit.Test;

/** Test of {@link UpdateStrategy} with {@link AndroidStudioUpdateStrategyCustomization}. */
public final class AndroidStudioUpdateStrategyTest {
  @Test
  public void testUpdateStrategyDoesNotPreferSamePlatformVersion() throws Exception {
    @Language("XML") String updatesXml =
      "<products>" +
      "  <product name='Android Studio'>" +
      "    <code>AI</code>" +
      "    <channel id='AI-2-eap' status='eap'>" +
      "      <build number='AI-183.2153.8.34.5078398' version='3.4 Canary 2'/>" +
      "    </channel>" +
      "    <channel id='AI-2-beta' status='beta'>" +
      "      <build number='AI-182.4892.20.33.5078385' version='3.3 Beta 2'/>" +
      "    </channel>" +
      "  </product>" +
      "</products>";

    UpdateSettings settings = mock(UpdateSettings.class);
    when(settings.getSelectedChannelStatus()).thenReturn(ChannelStatus.EAP);

    assertThat(updateStrategy("AI-182.4505.22.34.5070326", updatesXml, settings).checkForUpdates().getNewBuild().getNumber().asString())
      .isEqualTo("AI-183.2153.8.34.5078398");  // not AI-182.4892.20.33.5078385. This scenario is taken directly from b/117996392.
  }

  @Test
  public void testUpdateStrategyIgnoresLowerAndroidStudioVersion() throws Exception {
    @Language("XML") String updatesXml =
      "<products>" +
      "  <product name='Android Studio'>" +
      "    <code>AI</code>" +
      "    <channel id='AI-2-beta' status='beta'>" +
      "      <build number='AI-191.8026.42.35.5781497' version='3.5 RC 3'/>" +
      "    </channel>" +
      "  </product>" +
      "</products>";

    UpdateSettings settings = mock(UpdateSettings.class);
    when(settings.getSelectedChannelStatus()).thenReturn(ChannelStatus.EAP);

    assertThat(updateStrategy("AI-191.7479.19.36.5721125", updatesXml, settings).checkForUpdates().getNewBuild())
      .isNull();  // not AI-191.8026.42.35.5781497. This scenario is taken directly from b/139118534.
  }

  // It's not clear this is important, but it's what overriding haveSameMajorVersion still does now that we also override isNewerVersion.
  @Test
  public void testUpdateStrategyPrefersSameAndroidStudioVersion() throws Exception {
    @Language("XML") String updatesXml =
      "<products>" +
      "  <product name='Android Studio'>" +
      "    <code>AI</code>" +
      "    <channel id='AI-2-eap' status='eap'>" +
      "      <build number='AI-191.7479.19.36.5721125' version='3.6 Canary 5'/>" +
      "    </channel>" +
      "    <channel id='AI-2-beta' status='beta'>" +
      "      <build number='AI-191.7479.19.35.5763348' version='3.5 RC 2'/>" +
      "    </channel>" +
      "  </product>" +
      "</products>";

    UpdateSettings settings = mock(UpdateSettings.class);
    when(settings.getSelectedChannelStatus()).thenReturn(ChannelStatus.EAP);

    assertThat(updateStrategy("AI-191.7479.19.35.5717577", updatesXml, settings).checkForUpdates().getNewBuild().getNumber().asString())
      .isEqualTo("AI-191.7479.19.35.5763348");  // not AI-191.7479.19.36.5721125
  }

  private static UpdateStrategy updateStrategy(String version, String updatesXml, UpdateSettings settings) throws Exception {
    return new UpdateStrategy(BuildNumber.fromString(version),
                              parseUpdateData(JDOMUtil.load(updatesXml), "AI"),
                              settings,
                              new AndroidStudioUpdateStrategyCustomization());
  }
}
