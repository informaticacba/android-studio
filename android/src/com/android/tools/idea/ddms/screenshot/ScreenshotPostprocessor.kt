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
package com.android.tools.idea.ddms.screenshot

import com.android.annotations.concurrency.Slow
import java.awt.image.BufferedImage

/**
 * Used in conjunction with [ScreenshotViewer].
 */
interface ScreenshotPostprocessor {
  /**
   * Adds a device frame to a screenshot image.
   *
   * @param screenshotImage the screenshot image to process
   * @param framingOption determines the type of the frame to add to the image, or null to possibly
   *     adjust the screenshot without adding a frame
   * @return the framed image
   */
  @Slow
  fun addFrame(screenshotImage: ScreenshotImage, framingOption: FramingOption?): BufferedImage
}