/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.pickers

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.namespaceVariations
import com.android.tools.idea.compose.preview.pickers.properties.PsiCallPropertyModel
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyItem
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyModel
import com.android.tools.idea.compose.preview.pickers.tracking.NoOpTracker
import com.android.tools.idea.compose.preview.pickers.tracking.PickerTrackableValue
import com.android.tools.idea.compose.preview.pickers.tracking.PreviewPickerTracker
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.Sdks
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private fun PreviewElement.annotationText(): String = ReadAction.compute<String, Throwable> {
  previewElementDefinitionPsi?.element?.text ?: ""
}

@RunWith(Parameterized::class)
class PsiPickerTests(previewAnnotationPackage: String, composableAnnotationPackage: String) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}.Preview {1}.Composable")
    val namespaces = namespaceVariations
  }

  private val composableAnnotationFqName = "$composableAnnotationPackage.Composable"
  private val previewToolingPackage = previewAnnotationPackage

  @get:Rule
  val projectRule = ComposeProjectRule(previewAnnotationPackage = previewAnnotationPackage,
                                       composableAnnotationPackage = composableAnnotationPackage)

  @get:Rule
  val edtRule = EdtRule()
  private val fixture get() = projectRule.fixture
  private val project get() = projectRule.project
  private val module get() = projectRule.fixture.module

  @RunsInEdt
  @Test
  fun `the psi model reads the preview annotation correctly`() {
    @Language("kotlin")
    val fileContent = """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }

      @Composable
      @Preview("named")
      fun PreviewWithName() {
      }

      @Composable
      @Preview
      fun PreviewParameters() {
      }

      private const val nameFromConst = "Name from Const"

      @Composable
      @Preview(nameFromConst)
      fun PreviewWithNameFromConst() {
      }
    """.trimIndent()

    val file = fixture.configureByText("Test.kt", fileContent)
    val previews = AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile).toList()
    ReadAction.run<Throwable> {
      previews[0].also { noParametersPreview ->
        val parsed = PsiCallPropertyModel.fromPreviewElement(project, module, noParametersPreview, NoOpTracker)
        assertNotNull(parsed.properties["", "name"])
        assertNull(parsed.properties.getOrNull("", "name2"))
      }
      previews[1].also { namedPreview ->
        val parsed = PsiCallPropertyModel.fromPreviewElement(project, module, namedPreview, NoOpTracker)
        assertEquals("named", parsed.properties["", "name"].value)
      }
      previews[3].also { namedPreviewFromConst ->
        val parsed = PsiCallPropertyModel.fromPreviewElement(project, module, namedPreviewFromConst, NoOpTracker)
        assertEquals("Name from Const", parsed.properties["", "name"].value)
      }
    }
  }

  @RunsInEdt
  @Test
  fun `updating model updates the psi correctly`() {
    @Language("kotlin")
    val annotationWithParameters = """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
      """.trimIndent()

    assertUpdatingModelUpdatesPsiCorrectly(annotationWithParameters)

    @Language("kotlin")
    val emptyAnnotation = """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }
      """.trimIndent()

    assertUpdatingModelUpdatesPsiCorrectly(emptyAnnotation)
  }

  @RunsInEdt
  @Test
  fun `supported parameters displayed correctly`() {
    @Language("kotlin")
    val fileContent = """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview(name = "Test", fontScale = 1.2f, backgroundColor = 4294901760)
      fun PreviewWithParemeters() {
      }
    """.trimIndent()

    val model = getFirstModel(fileContent)
    assertNotNull(model.properties["", "backgroundColor"].colorButton)
    assertEquals("1.2", runReadAction { model.properties["", "fontScale"].value })
    assertEquals("0xFFFF0000", runReadAction { model.properties["", "backgroundColor"].value })

    model.properties["", "fontScale"].value = "0.5"
    model.properties["", "backgroundColor"].value = "0x00FF00"

    assertEquals("0.5", runReadAction { model.properties["", "fontScale"].value })
    assertEquals("0x0000FF00", runReadAction { model.properties["", "backgroundColor"].value })
  }

  @RunsInEdt
  @Test
  fun `preview default values`() {
    @Language("kotlin")
    val fileContent = """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
    """.trimIndent()

    Sdks.addLatestAndroidSdk(fixture.projectDisposable, module)
    val model = getFirstModel(fileContent)
    assertEquals("1f", model.properties["", "fontScale"].defaultValue)
    assertEquals("false", model.properties["", "showBackground"].defaultValue)
    assertEquals("false", model.properties["", "showDecoration"].defaultValue)
    assertEquals("Default (en-US)", model.properties["", "locale"].defaultValue)
    assertTrue(model.properties["", "apiLevel"].defaultValue!!.toInt() > 0)

    // Note that uiMode and device, are displayed through a ComboBox option and don't actually display these values
    assertEquals("Undefined", model.properties["", "uiMode"].defaultValue)
    assertEquals("Default", model.properties["", "Device"].defaultValue)

    // Hardware properties
    assertEquals("1080", model.properties["", "Width"].defaultValue)
    assertEquals("1920", model.properties["", "Height"].defaultValue)
    assertEquals("px", model.properties["", "DimensionUnit"].defaultValue)
    assertEquals("portrait", model.properties["", "Orientation"].defaultValue)
    assertEquals("420", model.properties["", "Density"].defaultValue)

    // We hide the default value of some values when the value's behavior is undefined
    assertEquals(null, model.properties["", "widthDp"].defaultValue)
    assertEquals(null, model.properties["", "heightDp"].defaultValue)
    // We don't take the library's default value for color
    assertEquals(null, model.properties["", "backgroundColor"].defaultValue)
  }

  @RunsInEdt
  @Test
  fun fontScaleEditing() {
    @Language("kotlin")
    val fileContent = """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }
      """.trimIndent()

    val model = getFirstModel(fileContent)
    val preview = AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, fixture.findFileInTempDir("Test.kt")).first()

    fun checkFontScaleChange(newValue: String, expectedPropertyValue: String) {
      val expectedTextValue = expectedPropertyValue + 'f'

      model.properties["", "fontScale"].value = newValue
      assertEquals(expectedPropertyValue, model.properties["", "fontScale"].value)
      assertEquals("@Preview(fontScale = $expectedTextValue)", preview.annotationText())
    }

    checkFontScaleChange("1", "1.0")
    checkFontScaleChange("2.", "2.0")
    checkFontScaleChange("3.01", "3.01")
    checkFontScaleChange("4.0f", "4.0")
    checkFontScaleChange("5.0d", "5.0")
    checkFontScaleChange("6f", "6.0")
    checkFontScaleChange("7d", "7.0")
    checkFontScaleChange("8.f", "8.0")
  }

  @Test
  fun `original order is preserved`() {
    @Language("kotlin")
    val fileContent = """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview(fontScale = 1.0f, name = "MyPreview", apiLevel = 1)
      fun PreviewWithParameters() {
      }
      """.trimIndent()

    val model = getFirstModel(fileContent)

    val properties = model.properties.values.iterator()
    assertEquals("name", properties.next().name)
    assertEquals("group", properties.next().name)
    assertEquals("apiLevel", properties.next().name)
    assertEquals("theme", properties.next().name)
    assertEquals("widthDp", properties.next().name)
    assertEquals("heightDp", properties.next().name)
    assertEquals("locale", properties.next().name)
    assertEquals("fontScale", properties.next().name)
  }

  @RunsInEdt
  @Test
  fun testDevicePropertiesTracked() {
    @Language("kotlin")
    val fileContent = """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
    """.trimIndent()
    val testTracker = object : PreviewPickerTracker {
      val valuesRegistered = mutableListOf<PickerTrackableValue>()

      override fun registerModification(name: String, value: PickerTrackableValue) {
        valuesRegistered.add(value)
      }

      override fun pickerShown() {} // Not tested
      override fun pickerClosed() {} // Not tested
      override fun logUsageData() {} // Not tested
    }

    val model = getFirstModel(fileContent, testTracker)

    model.properties["", "Device"].value = "hello world"

    model.properties["", "Orientation"].value = "portrait"
    model.properties["", "Orientation"].value = "landscape"
    model.properties["", "Orientation"].value = "bad input"

    model.properties["", "Density"].value = "480" // XXHIGH
    model.properties["", "Density"].value = "470" // Close to XXHIGH
    model.properties["", "Density"].value = "320" // XHIGH
    model.properties["", "Density"].value = "10000" // Extremely high (XXXHIGH is closest)
    model.properties["", "Density"].value = "bad input"

    model.properties["", "DimensionUnit"].value = "dp"
    model.properties["", "DimensionUnit"].value = "px"
    model.properties["", "DimensionUnit"].value = "bad input"

    model.properties["", "Width"].value = "100"
    model.properties["", "Height"].value = "200"

    assertEquals(14, testTracker.valuesRegistered.size)
    var index = 0
    // Device
    assertEquals(PickerTrackableValue.UNSUPPORTED_OR_OPEN_ENDED, testTracker.valuesRegistered[index++])

    // Orientation
    assertEquals(PickerTrackableValue.ORIENTATION_PORTRAIT, testTracker.valuesRegistered[index++])
    assertEquals(PickerTrackableValue.ORIENTATION_LANDSCAPE, testTracker.valuesRegistered[index++])
    assertEquals(PickerTrackableValue.UNKNOWN, testTracker.valuesRegistered[index++])

    // Density
    assertEquals(PickerTrackableValue.DENSITY_XX_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(PickerTrackableValue.DENSITY_XX_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(PickerTrackableValue.DENSITY_X_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(PickerTrackableValue.DENSITY_XXX_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(PickerTrackableValue.UNKNOWN, testTracker.valuesRegistered[index++])

    // DimensionUnit
    assertEquals(PickerTrackableValue.UNIT_DP, testTracker.valuesRegistered[index++])
    assertEquals(PickerTrackableValue.UNIT_PIXELS, testTracker.valuesRegistered[index++])
    assertEquals(PickerTrackableValue.UNKNOWN, testTracker.valuesRegistered[index++])

    // Width/Height
    assertEquals(PickerTrackableValue.UNSUPPORTED_OR_OPEN_ENDED, testTracker.valuesRegistered[index++])
    assertEquals(PickerTrackableValue.UNSUPPORTED_OR_OPEN_ENDED, testTracker.valuesRegistered[index])
  }

  private fun assertUpdatingModelUpdatesPsiCorrectly(fileContent: String) {
    val file = fixture.configureByText("Test.kt", fileContent)
    val noParametersPreview = AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile).first()
    val model = ReadAction.compute<PsiPropertyModel, Throwable> {
      PsiCallPropertyModel.fromPreviewElement(project, module, noParametersPreview, NoOpTracker)
    }
    var expectedModificationsCountdown = 13
    model.addListener(object : PropertiesModelListener<PsiPropertyItem> {
      override fun propertyValuesChanged(model: PropertiesModel<PsiPropertyItem>) {
        expectedModificationsCountdown--
      }
    })

    model.properties["", "name"].value = "NoHello"
    // Try to override our previous write. Only the last one should persist
    model.properties["", "name"].value = "Hello"
    assertEquals("@Preview(name = \"Hello\")", noParametersPreview.annotationText())

    // Add other properties
    model.properties["", "group"].value = "Group2"
    model.properties["", "widthDp"].value = "32"
    assertEquals("Hello", model.properties["", "name"].value)
    assertEquals("Group2", model.properties["", "group"].value)
    assertEquals("32", model.properties["", "widthDp"].value)
    assertEquals("@Preview(name = \"Hello\", group = \"Group2\", widthDp = 32)", noParametersPreview.annotationText())

    // Device parameters modifications
    model.properties["", "Width"].value = "720" // In pixels, this change should populate 'device' parameter in annotation
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:shape=Normal,width=720,height=1920,unit=px,dpi=480")""",
      noParametersPreview.annotationText()
    )

    model.properties["", "DimensionUnit"].value = "dp" // Should modify width and height in 'device' parameter
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:shape=Normal,width=240,height=640,unit=dp,dpi=480")""",
      noParametersPreview.annotationText()
    )

    model.properties["", "Density"].value = "240" // When changing back to pixels, the width and height should be different than originally
    model.properties["", "DimensionUnit"].value = "px"
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:shape=Normal,width=360,height=960,unit=px,dpi=240")""",
      noParametersPreview.annotationText()
    )

    model.properties["", "Orientation"].value = "landscape" // Changing orientation swaps width/height values
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:shape=Normal,width=960,height=360,unit=px,dpi=240")""",
      noParametersPreview.annotationText()
    )

    // Clear values
    model.properties["", "group"].value = null
    model.properties["", "widthDp"].value = "    " // Blank value is the same as null value
    model.properties["", "Device"].value = null
    assertEquals("@Preview(name = \"Hello\")", noParametersPreview.annotationText())

    model.properties["", "name"].value = null
    try {
      model.properties["", "notexists"].value = "3"
      fail("Nonexistent property should throw NoSuchElementException")
    }
    catch (expected: NoSuchElementException) {
    }

    // Verify final values on model
    assertNull(model.properties["", "name"].value)
    assertNull(model.properties["", "group"].value)
    assertNull(model.properties["", "widthDp"].value)
    // Verify final state of file
    assertEquals("@Preview", noParametersPreview.annotationText())
    // Verify that every modification (setting, overwriting and deleting values) triggered the listener
    assertEquals(0, expectedModificationsCountdown)
  }

  private fun getFirstModel(fileContent: String, tracker: PreviewPickerTracker = NoOpTracker): PsiPropertyModel {
    val file = fixture.configureByText("Test.kt", fileContent)
    val preview = AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile).first()
    ConfigurationManager.getOrCreateInstance(module)
    return ReadAction.compute<PsiPropertyModel, Throwable> {
      PsiCallPropertyModel.fromPreviewElement(project, module, preview, tracker)
    }
  }
}