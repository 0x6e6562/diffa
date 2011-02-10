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

package net.lshift.diffa.kernel.util

import org.hibernate.{Session, SessionFactory}

/**
 * Hibernate session convenience class/object
 */
class SessionHelper(val sessionFactory:SessionFactory) {

  def withSession[T](f:Function1[Session, T]):T = {
    val session = sessionFactory.openSession
    try {
      val result = f(session)
      session.flush

      result
    } finally {
      session.close
    }
  }

}

object SessionHelper {

  implicit def sessionFactoryToSessionHelper(sessionFactory:SessionFactory) =
    new SessionHelper(sessionFactory)
}