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

import org.joda.time.DateTime
import java.security.MessageDigest
import collection.mutable.ListBuffer
import net.lshift.diffa.kernel.participants._
import net.lshift.diffa.kernel.events.VersionID
import org.apache.commons.codec.binary.Hex
import scala.collection.JavaConversions._
import net.jcip.annotations.NotThreadSafe
import org.slf4j.LoggerFactory

/**
 * Utility class for building version digests from a sequence of versions.
 */
class DigestBuilder(val functions:Map[String, CategoryFunction]) {

  val log = LoggerFactory.getLogger(getClass)

  val digestBuckets = new java.util.TreeMap[String,Bucket]()
  val versions = new ListBuffer[EntityVersion]

  /**
   * Adds a new version into the builder.
   */
  def add(id:VersionID, attributes:Map[String, String], lastUpdated:DateTime, vsn:String) : Unit
    = add(id.id, attributes, lastUpdated, vsn)

  def add(id:String, attributes:Map[String, String], lastUpdated:DateTime, vsn:String) {

    log.debug("Adding to bucket: " + id + ", " + attributes + ", " + lastUpdated + ", " + vsn)

    if (shouldBucket) {
      val partitions:Map[String, String] = bucketingFunctions.map { case (attrName, function) => attrName -> function.owningPartition(attributes(attrName)) }
      val label = partitions.values.reduceLeft(_ + "_" + _)

      val bucket = digestBuckets.get(label) match {
        case null => {
          val newBucket = new Bucket(label, partitions)
          digestBuckets.put(label, newBucket)
          newBucket
        }
        case b    => b
      }
      bucket.add(vsn)
    } else {
      versions += EntityVersion(id, AttributesUtil.toSeq(attributes), lastUpdated, vsn)
    }
  }

  /**
   * Retrieves the bucketed digests for all version objects that have been provided.
   */
  def digests:Seq[AggregateDigest] = {
    if (shouldBucket) {
      // Digest the buckets
      digestBuckets.values.map(b => b.toDigest).toList
    } else {
      Seq()
    }
  }

  private lazy val bucketingFunctions:Map[String, CategoryFunction] =
    functions.filter { case (attrName, function) => function.shouldBucket }
  private lazy val shouldBucket = bucketingFunctions.size > 0
}

class Bucket(val name: String, val attributes: Map[String, String]) {
  private val digestAlgorithm = "MD5"
  private val messageDigest = MessageDigest.getInstance(digestAlgorithm)
  private var digest:String = null

  @NotThreadSafe
  def add(vsn: String) = {
    if (digest != null) {
      throw new SealedBucketException(vsn, name)
    }
    val vsnBytes = vsn.getBytes("UTF-8")
    messageDigest.update(vsnBytes, 0, vsnBytes.length)
  }

  @NotThreadSafe
  def toDigest = {
    if (digest == null) {
      digest = new String(Hex.encodeHex(messageDigest.digest()))
    }
    AggregateDigest(AttributesUtil.toSeq(attributes), digest)
  }

}

class SealedBucketException(vsn:String, name:String) extends Exception(vsn + " -> " + name)
