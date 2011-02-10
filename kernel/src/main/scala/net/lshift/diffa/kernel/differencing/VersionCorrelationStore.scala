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

package net.lshift.diffa.kernel.differencing

import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.participants.QueryConstraint
import org.joda.time.{DateTimeZone, DateTime}

/**
 * Store used for caching version correlation information between a pair of participants.
 */
trait VersionCorrelationStore {
  type UpstreamVersionHandler = Function4[VersionID, Map[String, String], DateTime, String, Unit]
  type DownstreamVersionHandler = Function5[VersionID, Map[String, String], DateTime, String, String, Unit]

  /**
   * Stores the details of an upstream version.
   */
  def storeUpstreamVersion(id:VersionID, attributes:Map[String,TypedAttribute], lastUpdated:DateTime, vsn:String):Correlation

  /**
   * Stores the details of a downstream version.
   */
  def storeDownstreamVersion(id:VersionID, attributes:Map[String,TypedAttribute], lastUpdated:DateTime, uvsn:String, dvsn:String):Correlation

  /**
   * Retrieves all of the unmatched version that have been stored.
   * @param usConstraints constraints on the upstream participant's entities
   * @param dsConstraints constraints on the downstream participant's entities
   */
  def unmatchedVersions(pairKey:String, usConstraints:Seq[QueryConstraint], dsConstraints:Seq[QueryConstraint]) : Seq[Correlation]

  /**
   * Retrieves the current pairing information for the given pairKey/id.
   */
  def retrieveCurrentCorrelation(id:VersionID):Option[Correlation]

  /**
   * Clears the upstream version for the given pairKey/id.
   */
  def clearUpstreamVersion(id:VersionID):Correlation

  /**
   * Clears the downstream version for the given pairKey/id.
   */
  def clearDownstreamVersion(id:VersionID):Correlation

  /**
   * Queries for all upstream versions for the given pair based on the given constraints.
   */
  def queryUpstreams(pairKey:String, constraints:Seq[QueryConstraint], handler:UpstreamVersionHandler):Unit = {
    queryUpstreams(pairKey, constraints).foreach(c => {
      handler(VersionID(c.pairing, c.id), c.upstreamAttributes.toMap, c.lastUpdate, c.upstreamVsn)
    })
  }

  /**
   * Queries for all downstream versions for the given pair based on the given constraints.
   */
  def queryDownstreams(pairKey:String, constraints:Seq[QueryConstraint], handler:DownstreamVersionHandler) : Unit = {
    queryDownstreams(pairKey, constraints).foreach(c => {
      handler(VersionID(c.pairing, c.id), c.downstreamAttributes.toMap, c.lastUpdate, c.downstreamUVsn, c.downstreamDVsn)
    })
  }

  /**
   * Queries for all upstream versions for the given pair based on the given constraints.
   */
  def queryUpstreams(pairKey:String, constraints:Seq[QueryConstraint]) : Seq[Correlation]

  /**
   * Queries for all downstream versions for the given pair based on the given constraints.
   */
  def queryDownstreams(pairKey:String, constraints:Seq[QueryConstraint]) : Seq[Correlation]
}

abstract class TypedAttribute { def value:String }
case class StringAttribute(value:String) extends TypedAttribute
case class DateAttribute(date:DateTime) extends TypedAttribute {
  def value = date.withZone(DateTimeZone.UTC).toString()
}
case class IntegerAttribute(int:Int) extends TypedAttribute {
  def value = int.toString
}
