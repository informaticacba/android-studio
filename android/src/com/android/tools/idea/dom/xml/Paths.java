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
package com.android.tools.idea.dom.xml;

import com.intellij.util.xml.DefinesXml;
import java.util.List;
import org.jetbrains.android.dom.AndroidDomElement;

@DefinesXml
public interface Paths extends AndroidDomElement {
  List<Path> getFilesPaths();
  List<Path> getCachePaths();
  List<Path> getExternalPaths();
  List<Path> getExternalFilesPaths();
  List<Path> getExternalMediaPaths();
  List<Path> getExternalCachePaths();
}
