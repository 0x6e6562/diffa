/**
 * Copyright (C) 2010-2011 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.kernel.config.system

import org.junit.Assert._
import collection.JavaConversions._
import net.lshift.diffa.kernel.util.SessionHelper._
import org.junit.{Before, Test}
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.config.{PairCache, User, Domain, HibernateDomainConfigStoreTest}
import net.sf.ehcache.CacheManager

class HibernateSystemConfigStoreTest {

  private val sf = HibernateDomainConfigStoreTest.domainConfigStore.sessionFactory
  private val pairCache = new PairCache(new CacheManager())
  private val systemConfigStore:SystemConfigStore = new HibernateSystemConfigStore(sf,pairCache)

  val domainName = "domain"
  val domain = Domain(name=domainName)

  val TEST_USER = User(name = "foo", email = "foo@bar.com", passwordEnc = "84983c60f7daadc1cb8698621f802c0d9f9a3c3c295c810748fb048115c186ec", superuser = false)
  val TEST_SUPERUSER = User(name = "fooroot", email = "root@bar.com", passwordEnc = "fcde2b2edba56bf408601fb721fe9b5c338d10ee429ea04fae5511b68fbf8fb9", superuser = true)

  @Before
  def setup() {
    try {
      systemConfigStore.deleteDomain(domainName)
    }
    catch {
      case e:MissingObjectException => // ignore any missing domains, since the objective of the call was to
                                       // delete one if it exists
    }

    systemConfigStore.createOrUpdateDomain(domain)

    // Deleting every user isn't something that the API exposes
    sf.withSession( s => s.createCriteria(classOf[User]).list.foreach(s.delete(_)))
  }

  @Test
  def shouldBeAbleToSetSystemProperty() {
    systemConfigStore.setSystemConfigOption("foo", "bar")
    assertEquals("bar", systemConfigStore.maybeSystemConfigOption("foo").get)
  }

  @Test(expected = classOf[MissingObjectException])
  def shouldGetExceptionWhenRetrievingMissingUser() {
    systemConfigStore.getUser("unknownuser")
  }

  @Test
  def shouldBeAbleToAddUserAndSeeItByGet() {
    systemConfigStore.createOrUpdateUser(TEST_USER)
    val user = systemConfigStore.getUser(TEST_USER.name)
    assertUserEquals(TEST_USER, user)
  }

  @Test
  def shouldBeAbleToAddUserAndSeeItInList() {
    systemConfigStore.createOrUpdateUser(TEST_USER)
    val result = systemConfigStore.listUsers
    assertEquals(1, result.length)
    assertUserEquals(TEST_USER, result(0))
  }

  @Test(expected = classOf[MissingObjectException])
  def shouldGetExceptionWhenRetrievingDeletedUser() {
    systemConfigStore.createOrUpdateUser(TEST_USER)
    systemConfigStore.deleteUser(TEST_USER.name)
    systemConfigStore.getUser(TEST_USER.name)
  }

  @Test
  def shouldNotSeeDeletedUsersInList() {
    systemConfigStore.createOrUpdateUser(TEST_USER)
    systemConfigStore.deleteUser(TEST_USER.name)
    val result = systemConfigStore.listUsers
    assertEquals(0, result.length)
  }

  @Test
  def shouldBeAbleToUpdateUser() {
    val updatedUser = User(name = TEST_USER.name, email = "somethingelse@bar.com",
      passwordEnc = TEST_SUPERUSER.passwordEnc, superuser = true)

    systemConfigStore.createOrUpdateUser(TEST_USER)
    systemConfigStore.createOrUpdateUser(updatedUser)

    val user = systemConfigStore.getUser(TEST_USER.name)
    assertUserEquals(updatedUser, user)
  }

  def assertUserEquals(expected:User, actual:User) {
    assertEquals(expected.name, actual.name)
    assertEquals(expected.email, actual.email)
    assertEquals(expected.passwordEnc, actual.passwordEnc)
    assertEquals(expected.superuser, actual.superuser)
  }
}