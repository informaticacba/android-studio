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
package com.android.tools.idea.gradle.project.facet.ndk

import org.jdom.Element
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.ex.JpsElementTypeWithDummyProperties
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer

/** Type to differentiate a native content root from Java/Kotlin content root. */
object NativeSourceRootType : JpsElementTypeWithDummyProperties(), JpsModuleSourceRootType<JpsDummyElement>

internal object NativeSourceRootTypeSerializer : JpsModuleSourceRootPropertiesSerializer<JpsDummyElement>(
  NativeSourceRootType, "native-Source-root") {
  override fun loadProperties(sourceRootTag: Element): JpsDummyElement {
    return JpsElementFactory.getInstance().createDummyElement()
  }

  override fun saveProperties(properties: JpsDummyElement, sourceRootTag: Element) {
  }
}
