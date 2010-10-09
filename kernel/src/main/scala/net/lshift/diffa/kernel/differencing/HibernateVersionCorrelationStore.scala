/**
 * Copyright (C) 2010 LShift Ltd.
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

package net.lshift.diffa.kernel.differencing

import java.lang.String
import org.joda.time.DateTime
import net.lshift.diffa.kernel.util.HibernateQueryUtils
import net.lshift.diffa.kernel.util.SessionHelper._ // for 'SessionFactory.withSession'
import org.hibernate.criterion.{Restrictions, Order}
import org.hibernate.{Query, Session, SessionFactory}
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.differencing._
import scala.collection.JavaConversions._ // for implicit conversions Java collections <--> Scala collections

/**
 * Hibernate backed implementation of the Version Correlation store.
 */
class HibernateVersionCorrelationStore(val sessionFactory:SessionFactory)
    extends VersionCorrelationStore
    with HibernateQueryUtils {

  def storeUpstreamVersion(id:VersionID, date: DateTime, lastUpdated: DateTime, vsn: String) = {
    val timestamp = new DateTime()
    sessionFactory.withSession(s => {
      val saveable = queryCurrentCorrelation(s, id) match {

        case None => Correlation(null, id.pairKey, id.id, date, lastUpdated, timestamp, vsn, null, null, false)
        case Some(c:Correlation) => {
          c.upstreamVsn = vsn
          updateMatchedState(c)
          c
        }
      }

      s.save(saveable)
      saveable
    })
  }
  
  def storeDownstreamVersion(id:VersionID, date: DateTime, lastUpdated: DateTime, uvsn: String, dvsn: String) = {
    val timestamp = new DateTime()
    sessionFactory.withSession(s => {
      val saveable = queryCurrentCorrelation(s, id) match {
        case None => Correlation(null, id.pairKey, id.id, date, lastUpdated, timestamp, null, uvsn, dvsn, false)
        case Some(c:Correlation) => {
          c.downstreamUVsn = uvsn
          c.downstreamDVsn = dvsn
          updateMatchedState(c)
          c
        }
      }

      s.save(saveable)
      saveable
    })
  }

  def unmatchedVersions(pairKey:String, dateRange:DateConstraint) = {
    sessionFactory.withSession(s => {
      val criteria = criteriaForDateRange(s, pairKey, dateRange)
      criteria.add(Restrictions.eq("isMatched", false))

      criteria.list.map { i => i.asInstanceOf[Correlation] }
    })
  }

  def retrieveCurrentCorrelation(id:VersionID) =
    sessionFactory.withSession(s => queryCurrentCorrelation(s, id))

  def clearUpstreamVersion(id:VersionID) = {
    val timestamp = new DateTime()
    sessionFactory.withSession(s => {
      queryCurrentCorrelation(s, id) match {
        case None => {
          // Generate a new matched correlation detail
          Correlation.asDeleted(id.pairKey, id.id, timestamp)
        }
        case Some(c:Correlation) => {
          c.upstreamVsn = null
          if (c.downstreamUVsn == null && c.downstreamDVsn == null) {
            // No versions at all. We can remove the entity
            s.delete(c)

            // Generate a new matched correlation detail
            Correlation.asDeleted(c.pairing, c.id, timestamp)
          } else {
            updateMatchedState(c)
            s.save(c)

            c
          }
        }
      }
    })
  }

  def clearDownstreamVersion(id:VersionID) = {
    val timestamp = new DateTime()
    sessionFactory.withSession(s => {
      queryCurrentCorrelation(s, id) match {
        case None => {
          // Generate a new matched correlation detail
          Correlation.asDeleted(id.pairKey, id.id, timestamp)
        }
        case Some(c:Correlation) => {
          c.downstreamUVsn = null
          c.downstreamDVsn = null
          if (c.upstreamVsn == null) {
            // No versions at all. We can remove the entity
            s.delete(c)

            // Generate a new matched correlation detail
            Correlation.asDeleted(c.pairing, c.id, timestamp)
          } else {
            updateMatchedState(c)
            s.save(c)

            c
          }
        }
      }
    })
  }

  def queryUpstreams(pairKey:String, dateRange:DateConstraint, handler:UpstreamVersionHandler) = {
    sessionFactory.withSession(s => {
      val criteria = criteriaForDateRange(s, pairKey, dateRange)
      criteria.add(Restrictions.isNotNull("upstreamVsn"))
      criteria.list.map(item => item.asInstanceOf[Correlation]).foreach(c => {
        handler(VersionID(c.pairing, c.id), c.date, c.lastUpdate, c.upstreamVsn)
      })
    })
  }

  def queryDownstreams(pairKey:String, dateRange:DateConstraint, handler:DownstreamVersionHandler) = {
    sessionFactory.withSession(s => {
      val criteria = criteriaForDateRange(s, pairKey, dateRange)
      criteria.add(Restrictions.or(Restrictions.isNotNull("downstreamUVsn"), Restrictions.isNotNull("downstreamDVsn")))
      criteria.list.map(item => item.asInstanceOf[Correlation]).foreach(c => {
        handler(VersionID(c.pairing, c.id), c.date, c.lastUpdate, c.downstreamUVsn, c.downstreamDVsn)
      })
    })
  }

  private def queryCurrentCorrelation(s:Session, id:VersionID):Option[Correlation] =
    singleQueryOpt(s, "currentCorrelation", Map("key" -> id.pairKey, "id" -> id.id))
  private def updateMatchedState(c:Correlation) = {
    c.isMatched = (c.upstreamVsn == c.downstreamUVsn)
    c
  }
  private def criteriaForDateRange(s:Session, pairKey:String, dateRange:DateConstraint) = {
    val criteria = s.createCriteria(classOf[Correlation])
    criteria.add(Restrictions.eq("pairing", pairKey))
    if (dateRange.start != null) {
      criteria.add(Restrictions.ge("date", dateRange.start))
    }
    if (dateRange.end != null) {
      criteria.add(Restrictions.le("date", dateRange.end))
    }
    criteria.addOrder(Order.asc("id"));

    criteria
  }
}