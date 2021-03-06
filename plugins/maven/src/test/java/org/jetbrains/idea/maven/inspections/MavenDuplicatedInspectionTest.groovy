/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.inspections

import org.jetbrains.idea.maven.dom.MavenDomTestCase
import org.jetbrains.idea.maven.dom.inspections.MavenDuplicateDependenciesInspection

/**
 * @author Sergey Evdokimov
 */
class MavenDuplicatedInspectionTest extends MavenDomTestCase {

  public void testDuplicatedInOneFile() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection)

    createProjectPom("""
  <groupId>mavenParent</groupId>
  <artifactId>childA</artifactId>
  <version>1.0</version>

  <dependencies>
    <<warning>dependency</warning>>
      <groupId>commons-collections</groupId>
      <artifactId>commons-collections</artifactId>
      <version>LATEST</version>
      <scope>provided</scope>
    </dependency>
    <<warning>dependency</warning>>
      <groupId>commons-collections</groupId>
      <artifactId>commons-collections</artifactId>
      <version>LATEST</version>
    </dependency>
  </dependencies>
""")

    checkHighlighting()
  }

  public void testDuplicatedInParent() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection)

    def m = createModulePom("child", """
  <groupId>mavenParent</groupId>
  <artifactId>child</artifactId>
  <version>1.0</version>

<parent>
  <groupId>mavenParent</groupId>
  <artifactId>parent</artifactId>
  <version>1.0</version>
</parent>

  <dependencies>
    <dependency>
      <groupId>commons-collections</groupId>
      <artifactId>commons-collections</artifactId>
      <version>LATEST</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>commons-io</groupId>
      <artifactId>commons-io</artifactId>
      <version>LATEST</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
""")

    createProjectPom("""
  <groupId>mavenParent</groupId>
  <artifactId>parent</artifactId>
  <version>1.0</version>
  <packaging>pom</packaging>

  <modules>
    <module>child</module>
  </modules>

  <dependencies>
    <dependency>
      <groupId>commons-collections</groupId>
      <artifactId>commons-collections</artifactId>
      <version>LATEST</version>
      <scope>provided</scope>
    </dependency>
    <<warning>dependency</warning>>
      <groupId>commons-io</groupId>
      <artifactId>commons-io</artifactId>
      <version>LATEST</version>
    </dependency>
  </dependencies>
""")

    importProject()

    checkHighlighting(myProjectPom, true, false, true)
  }

}
