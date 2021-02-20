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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val MATERIAL = packageNameHash("androidx.compose.material")
private val FOUNDATION_TEXT = packageNameHash("androidx.compose.foundation.text")
private val EXAMPLE = packageNameHash("com.example.myexampleapp")

class ComposeViewNodeTest {
  @get:Rule
  val appRule = ApplicationRule()

  @Before
  fun before() {
    appRule.testApplication.registerService(PropertiesComponent::class.java, PropertiesComponentMock())
  }

  @Test
  fun testIsSystemNode() {
    val model = model {
      view(ROOT) {
        compose(VIEW1, "MyApplicationTheme", composePackageHash = EXAMPLE) {
          compose(VIEW2, "Text", composePackageHash = EXAMPLE) {
            compose(VIEW3, "Text", composePackageHash = MATERIAL) {
              compose(VIEW4, "CoreText", composePackageHash = FOUNDATION_TEXT)
            }
          }
        }
      }
    }

    val user1 = model[VIEW1]!!
    val user2 = model[VIEW2]!!
    val system1 = model[VIEW3]!!
    val system2 = model[VIEW4]!!
    assertThat(system1.isSystemNode).isTrue()
    assertThat(system2.isSystemNode).isTrue()
    assertThat(user1.isSystemNode).isFalse()
    assertThat(user2.isSystemNode).isFalse()

    TreeSettings.hideSystemNodes = true
    assertThat(system1.isInComponentTree).isFalse()
    assertThat(system2.isInComponentTree).isFalse()
    assertThat(user1.isInComponentTree).isTrue()
    assertThat(user2.isInComponentTree).isTrue()

    TreeSettings.hideSystemNodes = false
    assertThat(system1.isInComponentTree).isTrue()
    assertThat(system2.isInComponentTree).isTrue()
    assertThat(user1.isInComponentTree).isTrue()
    assertThat(user2.isInComponentTree).isTrue()
  }
}