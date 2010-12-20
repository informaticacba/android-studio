/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidCompletionWeighter extends CompletionWeigher {

  @Override
  public Comparable weigh(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    return doWeigh(element.getObject(), location.getProject());
  }

  static Comparable doWeigh(Object object, Project project) {
    if (project == null || !ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return 0;
    }
    if (object instanceof PsiClass) {
      String qName = ((PsiClass)object).getQualifiedName();
      if (qName != null && qName.startsWith("android.")) {
        return 1;
      }
    }
    return 0;
  }
}
