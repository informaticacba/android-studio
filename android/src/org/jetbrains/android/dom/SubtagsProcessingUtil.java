/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.dom;

import com.google.common.collect.Multimap;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.dom.layout.LayoutElement;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.navigation.NavElement;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.PreferenceElement;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.MigrateToAndroidxUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Predicate;

import static com.android.SdkConstants.CLASS_PREFERENCE_GROUP;
import static com.android.SdkConstants.CLASS_PREFERENCE_GROUP_ANDROIDX;

/**
 * Utility functions for enumerating available children tag types in the context of a given XML tag.
 *
 * Entry point is {@link #processSubtags(AndroidFacet, AndroidDomElement, SubtagProcessor)}, look for a
 * Javadoc there.
 */
public class SubtagsProcessingUtil {
  private SubtagsProcessingUtil() {
  }

  /**
   * Checks if the given {@code psiClass} is a preference group and should have subtags in XML.
   *  @param psiClass class to check
   * @param preferenceClassMap class map obtained from a {@link com.android.tools.idea.psi.TagToClassMapper} used to find PSI classes.
   */
  private static boolean isPreferenceGroup(@NotNull PsiClass psiClass, @NotNull Map<String, PsiClass> preferenceClassMap) {
    Project project = psiClass.getProject();
    PsiManager psiManager = PsiManager.getInstance(project);

    PsiClass frameworkClass = preferenceClassMap.get(CLASS_PREFERENCE_GROUP);
    if (frameworkClass != null) {
      if (psiManager.areElementsEquivalent(frameworkClass, psiClass) || psiClass.isInheritor(frameworkClass, true)) {
        return true;
      }
    }

    PsiClass libClass = preferenceClassMap.get(MigrateToAndroidxUtil.getNameInProject(CLASS_PREFERENCE_GROUP_ANDROIDX, project));
    if (libClass != null) {
      if (psiManager.areElementsEquivalent(libClass, psiClass) || psiClass.isInheritor(libClass, true)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Provides information about available tags for resources in xml/ folder
   */
  public static void registerXmlResourcesSubtags(@NotNull AndroidFacet facet,
                                                 @NotNull XmlTag tag,
                                                 @NotNull SubtagProcessor subtagProcessor) {
    final String tagName = tag.getName();

    switch (tagName) {
      case "searchable":
        subtagProcessor.processSubtag("actionkey", XmlResourceElement.class);
        break;

      // for keyboard api
      case "Keyboard":
        subtagProcessor.processSubtag("Row", XmlResourceElement.class);
        break;
      case "Row":
        subtagProcessor.processSubtag("Key", XmlResourceElement.class);
        break;

      // for device-admin api
      case "device-admin":
        subtagProcessor.processSubtag("uses-policies", XmlResourceElement.class);
        break;
      case "uses-policies":
        subtagProcessor.processSubtag("limit-password", XmlResourceElement.class);
        subtagProcessor.processSubtag("watch-login", XmlResourceElement.class);
        subtagProcessor.processSubtag("reset-password", XmlResourceElement.class);
        subtagProcessor.processSubtag("force-lock", XmlResourceElement.class);
        subtagProcessor.processSubtag("wipe-data", XmlResourceElement.class);
        subtagProcessor.processSubtag("set-global-proxy", XmlResourceElement.class);
        subtagProcessor.processSubtag("expire-password", XmlResourceElement.class);
        subtagProcessor.processSubtag("encrypted-storage", XmlResourceElement.class);
        subtagProcessor.processSubtag("disable-camera", XmlResourceElement.class);
        subtagProcessor.processSubtag("disable-keyguard-features", XmlResourceElement.class);
        break;

      // DevicePolicyManager API
      case "preference-headers":
        subtagProcessor.processSubtag("header", PreferenceElement.class);
        break;
    }

    // for preferences
    Map<String, PsiClass> prefClassMap;
    if (AndroidXmlResourcesUtil.isAndroidXPreferenceFile(tag, facet)) {
      prefClassMap = AttributeProcessingUtil.getAndroidXPreferencesClassMap(facet);
    } else {
      prefClassMap = AttributeProcessingUtil.getFrameworkPreferencesClassMap(facet);
    }
    PsiClass psiClass = prefClassMap.get(tagName);

    if (psiClass != null && isPreferenceGroup(psiClass, prefClassMap)) {
      registerClassNameSubtags(tag, prefClassMap, PreferenceElement.class, subtagProcessor);
    }
  }

  private static void registerClassNameSubtags(XmlTag tag,
                                               Map<String, PsiClass> classMap,
                                               Type type,
                                               SubtagProcessor subtagProcessor) {
    final Set<String> allAllowedTags = new HashSet<>();
    final Map<String, String> class2Name = new HashMap<>();

    for (Map.Entry<String, PsiClass> entry : classMap.entrySet()) {
      final String tagName = entry.getKey();
      final PsiClass aClass = entry.getValue();

      if (!AndroidUtils.isAbstract(aClass)) {
        allAllowedTags.add(tagName);
        final String qName = aClass.getQualifiedName();
        final String prevTagName = class2Name.get(qName);

        if (prevTagName == null || tagName.indexOf('.') == -1) {
          class2Name.put(qName, tagName);
        }
      }
    }
    registerSubtags(tag, allAllowedTags, class2Name.values(), type, subtagProcessor);
  }

  private static void registerSubtags(@NotNull XmlTag tag,
                                      @NotNull Collection<String> allowedTags,
                                      @NotNull Collection<String> tagsToComplete,
                                      @NotNull Type type,
                                      @NotNull SubtagProcessor subtagProcessor) {
    for (String tagName : tagsToComplete) {
      subtagProcessor.processSubtag(tagName, type);
    }
    registerExistingSubtags(tag, allowedTags::contains, type, subtagProcessor);
  }

  /**
   * Enumerate children types that are valid inside a given XML tag, represented by {@link AndroidDomElement}.
   * Proceeds by dispatching on element type by instanceof checks, "returns" information about available tags
   * via {@code subtagCallback}.
   */
  public static void processSubtags(@NotNull AndroidFacet facet,
                                    @NotNull AndroidDomElement element,
                                    @NotNull SubtagProcessor subtagProcessor) {
    if (element instanceof LayoutElement) {
      registerClassNameSubtags(element.getXmlTag(), AttributeProcessingUtil.getViewClassMap(facet), LayoutViewElement.class,
                               subtagProcessor);
    }
    else if (element instanceof XmlResourceElement) {
      XmlTag tag = element.getXmlTag();
      if (tag != null) {
        registerXmlResourcesSubtags(facet, tag, subtagProcessor);
      }
    }
    else if (element instanceof NavElement) {
      try {
        NavigationSchema.createIfNecessary(facet.getModule());
      }
      catch (ClassNotFoundException e) {
        // We must not have added the nav library dependency yet, but encountered a nav file. Ignore for now.
        return;
      }
      NavigationSchema schema = NavigationSchema.get(facet.getModule());
      Multimap<Class<? extends AndroidDomElement>, String> subtags = schema.getDestinationSubtags(element.getXmlTag().getName());
      for (Class<? extends AndroidDomElement> c : subtags.keys()) {
        registerSubtags(element.getXmlTag(), subtags.get(c), subtags.get(c), c, subtagProcessor);
      }
    }
  }

  /**
   * Enumerate types of XML tags that are already are children of a tag, via {@code subtagProcessor}
   */
  private static void registerExistingSubtags(@NotNull XmlTag tag,
                                              @NotNull Predicate<String> filter,
                                              @NotNull Type type,
                                              @NotNull SubtagProcessor subtagProcessor) {
    XmlTag[] subtags = tag.getSubTags();
    for (XmlTag subtag : subtags) {
      String localName = subtag.getLocalName();
      if (filter.test(localName)) {
        // Skip child tag for which code completion is invoked at the moment
        if (!localName.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
          subtagProcessor.processSubtag(new XmlName(localName), type);
        }
      }
    }
  }

  public interface SubtagProcessor {
    void processSubtag(@NotNull XmlName xmlName, @NotNull Type type);

    default void processSubtag(@NotNull String xmlName, @NotNull Type type) {
      processSubtag(new XmlName(xmlName), type);
    }
  }
}
