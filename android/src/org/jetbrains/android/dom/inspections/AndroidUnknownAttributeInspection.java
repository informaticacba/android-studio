// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.dom.inspections;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.databinding.DataBindingModuleComponent;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.dom.AndroidAnyAttributeDescriptor;
import org.jetbrains.android.dom.AndroidDomExtender;
import org.jetbrains.android.dom.AndroidXmlTagDescriptor;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class AndroidUnknownAttributeInspection extends LocalInspectionTool {
  private static volatile Set<ResourceFolderType> ourSupportedResourceTypes;

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.inspections.group.name");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return AndroidBundle.message("android.inspections.unknown.attribute.name");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "AndroidUnknownAttribute";
  }

  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof XmlFile)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    if (!AndroidDomExtender.areExtensionsKnown()) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    if (isMyFile(facet, (XmlFile)file)) {
      Module module = facet.getModule();
      // Support attributes defined by @BindingAdapter annotations.
      DataBindingModuleComponent dataBindingComponent = module.getService(DataBindingModuleComponent.class);
      Set<String> bindingAdapterAttributes = dataBindingComponent != null
                                             ? dataBindingComponent.getBindingAdapterAttributes()
                                             : Collections.emptySet();
      MyVisitor visitor = new MyVisitor(manager, bindingAdapterAttributes, isOnTheFly);
      file.accept(visitor);
      return visitor.myResult.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }

  static boolean isMyFile(@NotNull AndroidFacet facet, XmlFile file) {
    ResourceFolderType resourceType = ModuleResourceManagers.getInstance(facet).getLocalResourceManager().getFileResourceFolderType(file);
    if (resourceType != null) {
      if (ourSupportedResourceTypes == null) {
        ourSupportedResourceTypes = EnumSet.complementOf(EnumSet.of(ResourceFolderType.INTERPOLATOR, ResourceFolderType.VALUES));
      }
      // Raw resource files should accept any tag values
      if (!ourSupportedResourceTypes.contains(resourceType) || ResourceFolderType.RAW == resourceType) {
        return false;
      }
      if (ResourceFolderType.XML == resourceType) {
        final XmlTag rootTag = file.getRootTag();
        return rootTag != null && AndroidXmlResourcesUtil.isSupportedRootTag(facet, rootTag.getName());
      }
      return true;
    }
    return ManifestDomFileDescription.isManifestFile(file, facet);
  }

  private static final class MyVisitor extends XmlRecursiveElementVisitor {
    private final InspectionManager myInspectionManager;
    private final Set<String> myBindingAdapterAttributes;
    private final boolean myOnTheFly;
    final List<ProblemDescriptor> myResult = new ArrayList<>();

    private MyVisitor(InspectionManager inspectionManager, Set<String> bindingAdapterAttributes, boolean onTheFly) {
      myInspectionManager = inspectionManager;
      myBindingAdapterAttributes = bindingAdapterAttributes;
      myOnTheFly = onTheFly;
    }

    @Override
    public void visitXmlAttribute(XmlAttribute attribute) {
      if (!"xmlns".equals(attribute.getNamespacePrefix())) {
        String namespace = attribute.getNamespace();

        if (SdkConstants.ANDROID_URI.equals(namespace) || namespace.isEmpty()) {
          final XmlTag tag = attribute.getParent();

          if (tag != null &&
              tag.getDescriptor() instanceof AndroidXmlTagDescriptor &&
              attribute.getDescriptor() instanceof AndroidAnyAttributeDescriptor) {

            if (myBindingAdapterAttributes.contains(attribute.getName())) {
              // Attribute is defined by @BindingAdapter annotation.
              return;
            }

            final ASTNode node = attribute.getNode();
            assert node != null;
            ASTNode nameNode = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node);
            final PsiElement nameElement = nameNode != null ? nameNode.getPsi() : null;
            if (nameElement != null) {
              myResult.add(myInspectionManager.createProblemDescriptor(nameElement, AndroidBundle
                .message("android.inspections.unknown.attribute.message", attribute.getName()), myOnTheFly, LocalQuickFix.EMPTY_ARRAY,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
          }
        }
      }
    }
  }
}
