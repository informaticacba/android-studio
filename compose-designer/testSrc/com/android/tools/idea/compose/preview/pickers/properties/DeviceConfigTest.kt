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
package com.android.tools.idea.compose.preview.pickers.properties

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class DeviceConfigTest {

  @Test
  fun parseTest() {
    var config = DeviceConfig.toDeviceConfigOrNull(null)
    assertNull(config)

    config = DeviceConfig.toDeviceConfigOrNull("spec:shape=Normal,width=120,height=240,unit=px,dpi=480")
    assertNotNull(config)
    assertEquals(120, config.width)
    assertEquals(240, config.height)
    assertEquals(DimUnit.px, config.dimUnit)
    assertEquals(480, config.dpi)
    assertEquals(Orientation.portrait, config.orientation)

    config = DeviceConfig.toDeviceConfigOrNull("spec:shape=Round,width=240,height=120,unit=px,dpi=480")
    assertNotNull(config)
    assertEquals(Orientation.landscape, config.orientation)
    assertEquals(Shape.Round, config.shape)

    config = DeviceConfig.toDeviceConfigOrNull("spec:shape=Round,width=invalid,height=1920,unit=px,dpi=invalid")
    assertNull(config)
  }

  @Test
  fun parseTestLegacy() {
    var config = DeviceConfig.toDeviceConfigOrNull("spec:Normal;120w;240h;px;480dpi")
    assertNotNull(config)
    assertEquals(120, config.width)
    assertEquals(240, config.height)
    assertEquals(DimUnit.px, config.dimUnit)
    assertEquals(480, config.dpi)
    assertEquals(Orientation.portrait, config.orientation)

    config = DeviceConfig.toDeviceConfigOrNull("spec:Round;240w;120h;px;480dpi")
    assertNotNull(config)
    assertEquals(Orientation.landscape, config.orientation)
    assertEquals(Shape.Round, config.shape)

    config = DeviceConfig.toDeviceConfigOrNull("spec:Round;invalid;1920;px;invalid")
    assertNull(config)
  }

  @Test
  fun modificationsTest() {
    val config = MutableDeviceConfig()
    assertEquals(1080, config.width)
    assertEquals(1920, config.height)

    config.dimUnit = DimUnit.dp
    assertEquals(360, config.width)
    assertEquals(640, config.height)

    // We change the dpi, which should result in different dimensions in pixels (half pixel density = half pixels on each dimension)
    config.dpi = config.dpi / 2
    config.dimUnit = DimUnit.px
    assertEquals(540, config.width)
    assertEquals(960, config.height)

    assertEquals(Orientation.portrait, config.orientation)

    val temp = config.width
    config.width = config.height
    config.height = temp
    assertEquals(Orientation.landscape, config.orientation)
  }
}