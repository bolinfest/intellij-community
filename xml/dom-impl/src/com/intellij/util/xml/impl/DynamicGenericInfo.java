/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.xml.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.JavaMethod;
import com.intellij.util.xml.reflect.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class DynamicGenericInfo extends DomGenericInfoEx {
  private static final Key<ConcurrentWeakHashMap<ChildrenDescriptionsHolder, ChildrenDescriptionsHolder>> HOLDERS_CACHE = Key.create("DOM_CHILDREN_HOLDERS_CACHE");
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("dynamicGenericInfo");
  private final StaticGenericInfo myStaticGenericInfo;
  @NotNull private final DomInvocationHandler myInvocationHandler;
  private volatile boolean myInitialized;
  private volatile ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> myAttributes;
  private volatile ChildrenDescriptionsHolder<FixedChildDescriptionImpl> myFixeds;
  private volatile ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> myCollections;
  private volatile CustomDomChildrenDescriptionImpl myCustomChildren;

  public DynamicGenericInfo(@NotNull final DomInvocationHandler handler, final StaticGenericInfo staticGenericInfo) {
    myInvocationHandler = handler;
    myStaticGenericInfo = staticGenericInfo;

    myAttributes = staticGenericInfo.getAttributes();
    myFixeds = staticGenericInfo.getFixed();
    myCollections = staticGenericInfo.getCollections();
  }

  public Invocation createInvocation(final JavaMethod method) {
    return myStaticGenericInfo.createInvocation(method);
  }

  public final boolean checkInitialized() {
    if (myInitialized) return true;
    myStaticGenericInfo.buildMethodMaps();

    final XmlElement element = myInvocationHandler.getXmlElement();
    if (element == null) return true;

    return ourGuard.doPreventingRecursion(element, false, new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        DomExtensionsRegistrarImpl registrar = runDomExtenders();

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (element) {
          if (!myInitialized) {
            if (registrar != null) {
              applyExtensions(registrar);
            }
            myInitialized = true;
          }
        }
        return Boolean.TRUE;
      }
    }) == Boolean.TRUE;
  }

  private void applyExtensions(DomExtensionsRegistrarImpl registrar) {
    XmlFile file = myInvocationHandler.getFile();

    final List<DomExtensionImpl> fixeds = registrar.getFixeds();
    final List<DomExtensionImpl> collections = registrar.getCollections();
    final List<DomExtensionImpl> attributes = registrar.getAttributes();
    if (!attributes.isEmpty()) {
      ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> newAttributes = new ChildrenDescriptionsHolder<AttributeChildDescriptionImpl>(myStaticGenericInfo.getAttributes());
      for (final DomExtensionImpl extension : attributes) {
        newAttributes.addDescription(extension.addAnnotations(new AttributeChildDescriptionImpl(extension.getXmlName(), extension.getType())));
      }
      myAttributes = internChildrenHolder(file, newAttributes);
    }

    if (!fixeds.isEmpty()) {
      ChildrenDescriptionsHolder<FixedChildDescriptionImpl> newFixeds = new ChildrenDescriptionsHolder<FixedChildDescriptionImpl>(myStaticGenericInfo.getFixed());
      for (final DomExtensionImpl extension : fixeds) {
        //noinspection unchecked
        newFixeds.addDescription(extension.addAnnotations(new FixedChildDescriptionImpl(extension.getXmlName(), extension.getType(), extension.getCount(), ArrayUtil.EMPTY_COLLECTION_ARRAY)));
      }
      myFixeds = internChildrenHolder(file, newFixeds);
    }
    if (!collections.isEmpty()) {
      ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> newCollections = new ChildrenDescriptionsHolder<CollectionChildDescriptionImpl>(myStaticGenericInfo.getCollections());
      for (final DomExtensionImpl extension : collections) {
        newCollections.addDescription(extension.addAnnotations(new CollectionChildDescriptionImpl(extension.getXmlName(), extension.getType(),
                                                                                                  Collections.<JavaMethod>emptyList()
        )));
      }
      myCollections = internChildrenHolder(file, newCollections);
    }

    final DomExtensionImpl extension = registrar.getCustomChildrenType();
    if (extension != null) {
      myCustomChildren = new CustomDomChildrenDescriptionImpl(null, extension.getType(), extension.getTagNameDescriptor());
    }
  }

  private static <T extends DomChildDescriptionImpl> ChildrenDescriptionsHolder<T> internChildrenHolder(XmlFile file, ChildrenDescriptionsHolder<T> holder) {
    ConcurrentWeakHashMap<ChildrenDescriptionsHolder, ChildrenDescriptionsHolder> cache = file.getUserData(HOLDERS_CACHE);
    if (cache == null) {
      file.putUserData(HOLDERS_CACHE, cache = new ConcurrentWeakHashMap<ChildrenDescriptionsHolder, ChildrenDescriptionsHolder>());
    }
    ChildrenDescriptionsHolder existing = cache.get(holder);
    if (existing != null) {
      //noinspection unchecked
      return existing;
    }
    cache.put(holder, holder);
    return holder;
  }

  @Nullable
  private DomExtensionsRegistrarImpl runDomExtenders() {
    DomExtensionsRegistrarImpl registrar = null;
    final DomElement domElement = myInvocationHandler.getProxy();
    final Project project = myInvocationHandler.getManager().getProject();
    for (final DomExtenderEP extenderEP : Extensions.getExtensions(DomExtenderEP.EP_NAME)) {
      registrar = extenderEP.extend(project, domElement, registrar);
    }

    final AbstractDomChildDescriptionImpl description = myInvocationHandler.getChildDescription();
    if (description != null) {
      final List<DomExtender> extenders = description.getUserData(DomExtensionImpl.DOM_EXTENDER_KEY);
      if (extenders != null) {
        if (registrar == null) registrar = new DomExtensionsRegistrarImpl();
        for (final DomExtender extender : extenders) {
          //noinspection unchecked
          extender.registerExtensions(domElement, registrar);
        }
      }
    }
    return registrar;
  }

  public XmlElement getNameElement(DomElement element) {
    return myStaticGenericInfo.getNameElement(element);
  }

  public GenericDomValue getNameDomElement(DomElement element) {
    return myStaticGenericInfo.getNameDomElement(element);
  }

  @Nullable
  public CustomDomChildrenDescriptionImpl getCustomNameChildrenDescription() {
    checkInitialized();
    if (myCustomChildren != null) return myCustomChildren;
    return myStaticGenericInfo.getCustomNameChildrenDescription();
  }

  public String getElementName(DomElement element) {
    return myStaticGenericInfo.getElementName(element);
  }

  @NotNull
  public List<AbstractDomChildDescriptionImpl> getChildrenDescriptions() {
    checkInitialized();
    final ArrayList<AbstractDomChildDescriptionImpl> list = new ArrayList<AbstractDomChildDescriptionImpl>();
    myAttributes.dumpDescriptions(list);
    myFixeds.dumpDescriptions(list);
    myCollections.dumpDescriptions(list);
    ContainerUtil.addIfNotNull(myStaticGenericInfo.getCustomNameChildrenDescription(), list);
    return list;
  }

  @NotNull
  public final List<FixedChildDescriptionImpl> getFixedChildrenDescriptions() {
    checkInitialized();
    return myFixeds.getDescriptions();
  }

  @NotNull
  public final List<CollectionChildDescriptionImpl> getCollectionChildrenDescriptions() {
    checkInitialized();
    return myCollections.getDescriptions();
  }

  public FixedChildDescriptionImpl getFixedChildDescription(String tagName) {
    checkInitialized();
    return myFixeds.findDescription(tagName);
  }

  public DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespace) {
    checkInitialized();
    return myFixeds.getDescription(tagName, namespace);
  }

  public CollectionChildDescriptionImpl getCollectionChildDescription(String tagName) {
    checkInitialized();
    return myCollections.findDescription(tagName);
  }

  public DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespace) {
    checkInitialized();
    return myCollections.getDescription(tagName, namespace);
  }

  public AttributeChildDescriptionImpl getAttributeChildDescription(String attributeName) {
    checkInitialized();
    return myAttributes.findDescription(attributeName);
  }


  public DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespace) {
    checkInitialized();
    return myAttributes.getDescription(attributeName, namespace);
  }

  public boolean isTagValueElement() {
    return myStaticGenericInfo.isTagValueElement();
  }

  @NotNull
  public List<AttributeChildDescriptionImpl> getAttributeChildrenDescriptions() {
    checkInitialized();
    return myAttributes.getDescriptions();
  }

  @Override
  public boolean processAttributeChildrenDescriptions(final Processor<AttributeChildDescriptionImpl> processor) {
    final Set<AttributeChildDescriptionImpl> visited = new THashSet<AttributeChildDescriptionImpl>();
    if (!myStaticGenericInfo.processAttributeChildrenDescriptions(new Processor<AttributeChildDescriptionImpl>() {
      public boolean process(AttributeChildDescriptionImpl attributeChildDescription) {
        visited.add(attributeChildDescription);
        return processor.process(attributeChildDescription);
      }
    })) {
      return false;
    }
    for (final AttributeChildDescriptionImpl description : getAttributeChildrenDescriptions()) {
      if (!visited.contains(description) && !processor.process(description)) {
        return false;
      }
    }
    return true;
  }

}
