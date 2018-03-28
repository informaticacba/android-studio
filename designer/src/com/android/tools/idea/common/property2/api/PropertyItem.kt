/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.property2.api

import javax.swing.Icon

/**
 * Defines basic information about a property.
 */
interface PropertyItem {
  /**
   * The namespace of the property e.g. "http://schemas.android.com/apk/res/android"
   */
  val namespace: String

  /**
   * Optional icon to indicate a namespace
   */
  val namespaceIcon: Icon?
    get() = null

  /**
   * The name of the property e.g. "gravity"
   */
  val name: String

  /**
   * The property value
   */
  var value: String?

  /**
   * If [value] is a reference then resolve the reference, otherwise this is the same as [value].
   */
  val resolvedValue: String?
    get() = value

  /**
   * Whether the original [value] is a reference value
   */
  val isReference: Boolean

  /**
   * The tooltip for this property name
   */
  val tooltipForName: String
    get() = ""

  /**
   * The tooltip for the value of this property
   */
  val tooltipForValue: String
    get() = ""

  /**
   * A validation method.
   *
   * @return an error message, or an empty string if there is no error.
   */
  fun validate(editedValue: String): String = ""

  /**
   * The matching design property, i.e. tools attribute
   */
  val designProperty: PropertyItem
    get() = throw IllegalStateException()
}
