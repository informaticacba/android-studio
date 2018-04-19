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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureBasicActivityStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.text.StringUtil.getOccurrenceCount;
import static org.junit.Assert.assertEquals;

@RunWith(GuiTestRunner.class)
public class CreateDefaultActivityTest {
  private static final String PROVIDED_ACTIVITY = "app/src/main/java/google/simpleapplication/MyActivity.java";
  private static final String PROVIDED_MANIFEST = "app/src/main/AndroidManifest.xml";
  private static final String APP_BUILD_GRADLE = "app/build.gradle";
  private static final String DEFAULT_ACTIVITY_NAME = "MainActivity";
  private static final String DEFAULT_LAYOUT_NAME = "activity_main";
  private static final String DEFAULT_ACTIVITY_TITLE = "MainActivity";

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private EditorFixture myEditor;
  private NewActivityWizardFixture myDialog;
  private ConfigureBasicActivityStepFixture<NewActivityWizardFixture> myConfigActivity;

  @Before
  public void setUp() throws IOException {
    guiTest.importSimpleLocalApplication();
    guiTest.ideFrame().getProjectView().selectProjectPane();
    myEditor = guiTest.ideFrame().getEditor();
    myEditor.open(PROVIDED_ACTIVITY);

    guiTest.ideFrame().getProjectView().assertFilesExist(
      "settings.gradle",
      "app",
      PROVIDED_ACTIVITY,
      PROVIDED_MANIFEST
    );

    invokeNewActivityMenu();
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, DEFAULT_ACTIVITY_TITLE);
    assertThat(getSavedKotlinSupport()).isFalse();
    assertThat(getSavedRenderSourceLanguage()).isEqualTo(Language.JAVA);
  }

  /**
   * Verifies that a new activity can be created through the Wizard
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 9ab45c50-1eb0-44aa-95fb-17835baf2274
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Right click on the application module and select New > Activity > Basic Activity
   *   2. Enter activity and package name. Click Finish
   *   Verify:
   *   Activity class and layout.xml files are created. The activity previews correctly in layout editor.
   *   </pre>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void createDefaultActivity() {
    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    guiTest.ideFrame().getProjectView().assertFilesExist(
      "app/src/main/java/google/simpleapplication/MainActivity.java",
      "app/src/main/res/layout/activity_main.xml"
    );

    String manifesText = myEditor.open(PROVIDED_MANIFEST).getCurrentFileContents();
    assertEquals(getOccurrenceCount(manifesText, "android:name=\".MainActivity\""), 1);
    assertEquals(getOccurrenceCount(manifesText, "@string/title_activity_main"), 1);
    assertEquals(getOccurrenceCount(manifesText, "android.intent.category.LAUNCHER"), 1);

    String gradleText = myEditor.open(APP_BUILD_GRADLE).getCurrentFileContents();
    assertEquals(getOccurrenceCount(gradleText, "com.android.support.constraint:constraint-layout"), 1);
  }

  // Note: This should be called only when the last open file was a Java/Kotlin file
  private void invokeNewActivityMenu() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "Activity", "Basic Activity");
    myDialog = NewActivityWizardFixture.find(guiTest.ideFrame());

    myConfigActivity = myDialog.getConfigureActivityStep();
  }

  private void assertTextFieldValues(@NotNull String activityName, @NotNull String layoutName, @NotNull String title) {
    assertThat(myConfigActivity.getTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.NAME)).isEqualTo(activityName);
    assertThat(myConfigActivity.getTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.LAYOUT)).isEqualTo(layoutName);
    assertThat(myConfigActivity.getTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.TITLE)).isEqualTo(title);
  }

  private static boolean getSavedKotlinSupport() {
    return PropertiesComponent.getInstance().isTrueValue("SAVED_PROJECT_KOTLIN_SUPPORT");
  }

  @NotNull
  private static Language getSavedRenderSourceLanguage() {
    return Language.fromName(PropertiesComponent.getInstance().getValue("SAVED_RENDER_LANGUAGE"), Language.JAVA);
  }
}
