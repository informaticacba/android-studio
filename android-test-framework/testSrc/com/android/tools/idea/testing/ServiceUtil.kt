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
@file:JvmName("ServiceUtil")
package com.android.tools.idea.testing

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService

/**
 * Registers a service implementation, possibly replacing an existent one. The lifetime of
 * the service implementation is controlled by [parentDisposable].
 */
fun <T : Any> ComponentManager.registerServiceInstance(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) {
  if (getService(serviceInterface) == null) {
    registerServiceInstance(serviceInterface, instance)
    // TODO: Replace the line above by the line below when https://youtrack.jetbrains.com/issue/IDEA-279127 is fixed.
    //Disposer.register(parentDisposable) { (picoContainer as MutablePicoContainer).unregisterComponent(serviceInterface.name) }
    return
  }

  replaceService(serviceInterface, instance, parentDisposable)
}


