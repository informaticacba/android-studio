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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.naveditor.model.isAction
import org.jetbrains.android.dom.navigation.NavigationSchema

/**
 * Creates [SceneComponent]s from [NlComponent]s for the navigation editor.
 */
val NAV_DECORATOR = NavigationDecorator()
val ACTIVITY_DECORATOR = ActivityDecorator()
val FRAGMENT_DECORATOR = FragmentDecorator()
val ACTION_DECORATOR = ActionDecorator()

class NavSceneDecoratorFactory : SceneDecoratorFactory() {

  override fun get(component: NlComponent): SceneDecorator {
    if (component.isAction) {
      return ACTION_DECORATOR
    }
    return when (component.destinationType) {
      NavigationSchema.DestinationType.NAVIGATION -> NAV_DECORATOR
      NavigationSchema.DestinationType.ACTIVITY -> ACTIVITY_DECORATOR
      NavigationSchema.DestinationType.FRAGMENT -> FRAGMENT_DECORATOR
      else -> SceneDecoratorFactory.BASIC_DECORATOR
    }
  }
}