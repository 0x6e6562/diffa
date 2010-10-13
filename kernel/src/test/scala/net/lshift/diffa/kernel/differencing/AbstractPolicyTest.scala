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

import org.easymock.EasyMock._
import org.easymock.{IAnswer, EasyMock}
import org.joda.time.DateTime
import net.lshift.diffa.kernel.util.EasyMockScalaUtils._
import org.junit.Test
import net.lshift.diffa.kernel.participants._
import org.apache.commons.codec.digest.DigestUtils
import net.lshift.diffa.kernel.util.Dates._
import net.lshift.diffa.kernel.util.DateUtils._
import net.lshift.diffa.kernel.events._

/**
 * Base class for the various policy tests.
 */
abstract class AbstractPolicyTest {
  // The policy instance under test
  protected def policy:VersionPolicy

  // A method for generating a downstream version based on an upstream version
  protected def downstreamVersionFor(v:String):String

  // The various mocks for listeners and participants
  val usMock = createStrictMock("us", classOf[UpstreamParticipant])
  val dsMock = createStrictMock("ds", classOf[DownstreamParticipant])
  val nullListener = new NullDifferencingListener
  
  val store = createStrictMock("versionStore", classOf[VersionCorrelationStore])
  EasyMock.checkOrder(store, false)   // Store doesn't care about order
  val listener = createStrictMock("listener", classOf[DifferencingListener])

  val abPair = "A-B"
  
  protected def replayAll = replay(usMock, dsMock, store, listener)
  protected def verifyAll = verify(usMock, dsMock, store, listener)

  // Make declaring of sequences of specific types clearer
  def DigestsFromParticipant[T](vals:T*) = Seq[T](vals:_*)
  def VersionsFromStore[T](vals:T*) = Seq[T](vals:_*)

  @Test
  def shouldOnlySyncTopLevelsWhenParticipantsAndStoresMatch {
    // Expect only a top-level sync between the pairs
    expectUpstreamSync(abPair, DateConstraint(START_2009, END_2010), YearGranularity,
      DigestsFromParticipant(
        VersionDigest("2009", START_2009, null, DigestUtils.md5Hex("vsn1")),
        VersionDigest("2010", START_2010, null, DigestUtils.md5Hex("vsn2"))),
      VersionsFromStore(
        UpstreamVersion(VersionID(abPair, "id1"), JUN_6_2009_1, JUN_6_2009_1, "vsn1"),
        UpstreamVersion(VersionID(abPair, "id2"), JUL_8_2010_1, JUL_8_2010_1, "vsn2")))
    expectDownstreamSync(abPair, DateConstraint(START_2009, END_2010), YearGranularity,
      DigestsFromParticipant(
        VersionDigest("2009", START_2009, null, DigestUtils.md5Hex(downstreamVersionFor("vsn1"))),
        VersionDigest("2010", START_2010, null, DigestUtils.md5Hex(downstreamVersionFor("vsn2")))),
      VersionsFromStore(
        DownstreamVersion(VersionID(abPair, "id1"), JUN_6_2009_1, JUN_6_2009_1, "vsn1", downstreamVersionFor("vsn1")),
        DownstreamVersion(VersionID(abPair, "id2"), JUL_8_2010_1, JUL_8_2010_1, "vsn2", downstreamVersionFor("vsn2"))))

    // We should still see an unmatched version check
    expect(store.unmatchedVersions(EasyMock.eq(abPair), EasyMock.eq(DateConstraint(START_2009, END_2010)))).
        andReturn(Seq())
    replayAll

    policy.difference(abPair, DateConstraint(START_2009, END_2010), usMock, dsMock, nullListener)
    verifyAll
  }

  @Test
  def shouldUpdateUpstreamVersionsWhenStoreIsOutOfDateWithUpstreamParticipant {
    val timestamp = new DateTime()
    // Expect only a top-level sync between the pairs
    expectUpstreamSync(abPair, DateConstraint(START_2009, END_2010), YearGranularity,
      DigestsFromParticipant(
        VersionDigest("2009", START_2009, START_2009, DigestUtils.md5Hex("vsn1")),
        VersionDigest("2010", START_2010, START_2010, DigestUtils.md5Hex("vsn2new" + "vsn4"))),
      VersionsFromStore(
        UpstreamVersion(VersionID(abPair, "id1"), JUN_6_2009_1, JUN_6_2009_1, "vsn1"),
        UpstreamVersion(VersionID(abPair, "id2"), JUL_8_2010_1, JUL_8_2010_1, "vsn2"),
        UpstreamVersion(VersionID(abPair, "id3"), JUL_8_2010_1, JUL_8_2010_1, "vsn3")))
    expectUpstreamSync(abPair, DateConstraint(START_2010, END_2010), MonthGranularity,
      DigestsFromParticipant(
        VersionDigest("2010-07", JUL_8_2010_1, JUL_8_2010_1, DigestUtils.md5Hex("vsn2new" + "vsn4"))),
      VersionsFromStore(
        UpstreamVersion(VersionID(abPair, "id2"), JUL_8_2010_1, JUL_8_2010_1, "vsn2"),
        UpstreamVersion(VersionID(abPair, "id3"), JUL_8_2010_1, JUL_8_2010_1, "vsn3")))
    expectUpstreamSync(abPair, DateConstraint(JUL_2010, END_JUL_2010), DayGranularity,
      DigestsFromParticipant(
        VersionDigest("2010-07-08", JUL_8_2010_1, JUL_8_2010_1, DigestUtils.md5Hex("vsn2new"  + "vsn4"))),
      VersionsFromStore(
        UpstreamVersion(VersionID(abPair, "id2"), JUL_8_2010_1, JUL_8_2010_1, "vsn2"),
        UpstreamVersion(VersionID(abPair, "id3"), JUL_8_2010_1, JUL_8_2010_1, "vsn3")))
    expectUpstreamSync(abPair, DateConstraint(JUL_8_2010, endOfDay(JUL_8_2010)), IndividualGranularity,
      DigestsFromParticipant(
        VersionDigest("id2", JUL_8_2010_1, JUL_8_2010_1, "vsn2new"),
        VersionDigest("id4", JUL_8_2010_1, JUL_8_2010_1, "vsn4")),
      VersionsFromStore(
        UpstreamVersion(VersionID(abPair, "id2"), JUL_8_2010_1, JUL_8_2010_1, "vsn2"),
        UpstreamVersion(VersionID(abPair, "id3"), JUL_8_2010_1, JUL_8_2010_1, "vsn3")))

    expectDownstreamSync(abPair, DateConstraint(START_2009, END_2010), YearGranularity,
      DigestsFromParticipant(
        VersionDigest("2009", START_2009, START_2009, DigestUtils.md5Hex(downstreamVersionFor("vsn1"))),
        VersionDigest("2010", START_2010, START_2010, DigestUtils.md5Hex(downstreamVersionFor("vsn2")))),
      VersionsFromStore(
        DownstreamVersion(VersionID(abPair, "id1"), JUN_6_2009_1, JUN_6_2009_1, "vsn1", downstreamVersionFor("vsn1")),
        DownstreamVersion(VersionID(abPair, "id2"), JUL_8_2010_1, JUL_8_2010_1, "vsn2", downstreamVersionFor("vsn2"))))

    // The policy should update the version for id2, remove id3 and add id4
    expect(store.storeUpstreamVersion(VersionID(abPair, "id2"), JUL_8_2010_1, JUL_8_2010_1, "vsn2new")).
      andReturn(Correlation(null, abPair, "id3", JUL_8_2010_1, JUL_8_2010_1, timestamp, "vsn2new", "vsn2", downstreamVersionFor("vsn2"), false))
    expect(store.clearUpstreamVersion(VersionID(abPair, "id3"))).
      andReturn(Correlation.asDeleted(abPair, "id3", new DateTime))
    expect(store.storeUpstreamVersion(VersionID(abPair, "id4"), JUL_8_2010_1, JUL_8_2010_1, "vsn4")).
      andReturn(Correlation(null, abPair, "id4", JUL_8_2010_1, JUL_8_2010_1, timestamp, downstreamVersionFor("vsn2"), null, null, false))

    // Don't report any unmatched versions
    expect(store.unmatchedVersions(EasyMock.eq(abPair), EasyMock.eq(DateConstraint(START_2009, END_2010)))).
        andReturn(Seq())
    replayAll

    policy.difference(abPair, DateConstraint(START_2009, END_2010), usMock, dsMock, nullListener)
    verifyAll
  }

  @Test
  def shouldReportMismatchesReportedByUnderlyingStore {
    val timestamp = new DateTime()
    // Expect only a top-level sync between the pairs
    expectUpstreamSync(abPair, DateConstraint(START_2009, END_2010), YearGranularity,
      DigestsFromParticipant(
        VersionDigest("2009", START_2009, START_2009, DigestUtils.md5Hex("vsn1")),
        VersionDigest("2010", START_2010, START_2010, DigestUtils.md5Hex("vsn2"))),
      VersionsFromStore(
        UpstreamVersion(VersionID(abPair, "id1"), JUN_6_2009_1, JUN_6_2009_1, "vsn1"),
        UpstreamVersion(VersionID(abPair, "id2"), JUL_8_2010_1, JUL_8_2010_1, "vsn2")))
    expectDownstreamSync(abPair, DateConstraint(START_2009, END_2010), YearGranularity,
      DigestsFromParticipant(
        VersionDigest("2009", START_2009, START_2009, DigestUtils.md5Hex(downstreamVersionFor("vsn1a"))),
        VersionDigest("2010", START_2010, START_2010, DigestUtils.md5Hex(downstreamVersionFor("vsn2a")))),
      VersionsFromStore(
        DownstreamVersion(VersionID(abPair, "id1"), JUN_6_2009_1, JUN_6_2009_1, "vsn1a", downstreamVersionFor("vsn1a")),
        DownstreamVersion(VersionID(abPair, "id2"), JUL_8_2010_1, JUL_8_2010_1, "vsn2a", downstreamVersionFor("vsn2a"))))

    // If the version check returns mismatches, we should see differences generated
    expect(store.unmatchedVersions(EasyMock.eq(abPair), EasyMock.eq(DateConstraint(START_2009, END_2010)))).
        andReturn(Seq(
          Correlation(null, abPair, "id1", JUN_6_2009_1, JUN_6_2009_1, timestamp, "vsn1", "vsn1a", "vsn3", false),
          Correlation(null, abPair, "id2", JUL_8_2010_1, JUL_8_2010_1, timestamp, "vsn2", "vsn2a", "vsn4", false)))
    listener.onMismatch(VersionID(abPair, "id1"), JUN_6_2009_1, "vsn1", "vsn1a"); expectLastCall
    listener.onMismatch(VersionID(abPair, "id2"), JUL_8_2010_1, "vsn2", "vsn2a"); expectLastCall

    replayAll

    policy.difference(abPair, DateConstraint(START_2009, END_2010), usMock, dsMock, listener)
    verifyAll
  }

  @Test
  def shouldStoreUpstreamChangesToCorrelationStoreAndNotifySessionManagerForQuasiLiveDate {
    val bizDate = JUL_8_2010_1
    val lastUpdate = Some(JUL_8_2010_2)
    storeUpstreamChanges(bizDate, lastUpdate)
  }

  @Test
  def shouldStoreUpstreamChangesToCorrelationStoreAndNotifySessionManagerWithoutLastUpdate {
    val bizDate = JUL_8_2010_1
    val lastUpdate = None
    storeUpstreamChanges(bizDate, lastUpdate)
  }

  /**
   * This is a utility function that allows a kind of virtual date mode for testing
   * historical submissions
   */
  def storeUpstreamChanges(bizDate:DateTime, lastUpdate:Option[DateTime]) {
    val timestamp = new DateTime
    val (update, observationDate, f) = lastUpdate match {
      case None     => (timestamp, null, () => store.storeUpstreamVersion(VersionID(abPair, "id1"), bizDate, new DateTime, "vsn1"))
      case Some(x)  => (x, x, () => store.storeUpstreamVersion(VersionID(abPair, "id1"), bizDate, x, "vsn1"))
    }
    expect(f()).andReturn(Correlation(null, abPair, "id1", bizDate, update, timestamp, "vsn1", null, null, false))
    listener.onMismatch(VersionID(abPair, "id1"), update, "vsn1", null); expectLastCall
    replayAll

    policy.onChange(UpstreamPairChangeEvent(VersionID(abPair, "id1"), bizDate, observationDate, "vsn1"))
    verifyAll
  }

  @Test
  def shouldStoreDownstreamChangesToCorrelationStoreAndNotifySessionManager {
    val timestamp = new DateTime()
    expect(store.storeDownstreamVersion(VersionID(abPair, "id1"), JUL_8_2010_1, JUL_8_2010_2, "vsn1", "vsn1")).
      andReturn(Correlation(null, abPair, "id1", JUL_8_2010_1, JUL_8_2010_2, timestamp, null, "vsn1", "vsn1", false))
    listener.onMismatch(VersionID(abPair, "id1"), JUL_8_2010_2, null, "vsn1"); expectLastCall
    replayAll

    policy.onChange(DownstreamPairChangeEvent(VersionID(abPair, "id1"), JUL_8_2010_1, JUL_8_2010_2, "vsn1"))
    verifyAll
  }

  @Test
  def shouldStoreDownstreamCorrelatedChangesToCorrelationStoreAndNotifySessionManager {
    val timestamp = new DateTime()
    expect(store.storeDownstreamVersion(VersionID(abPair, "id1"), JUL_8_2010_1, JUL_8_2010_2, "vsn1", "vsn2")).
      andReturn(Correlation(null, abPair, "id1", JUL_8_2010_1, JUL_8_2010_2, timestamp, null, "vsn1", "vsn1", false))
    listener.onMismatch(VersionID(abPair, "id1"), JUL_8_2010_2, null, "vsn1"); expectLastCall
    replayAll

    policy.onChange(DownstreamCorrelatedPairChangeEvent(VersionID(abPair, "id1"), JUL_8_2010_1, JUL_8_2010_2, "vsn1", "vsn2"))
    verifyAll
  }

  @Test
  def shouldRaiseMatchEventWhenDownstreamCausesMatchOfUpstream {
    val timestamp = new DateTime()
    expect(store.storeDownstreamVersion(VersionID(abPair, "id1"), JUL_8_2010_1, JUL_8_2010_2, "vsn1", "vsn2")).
      andReturn(Correlation(null, abPair, "id1", JUL_8_2010_1, JUL_8_2010_2, timestamp, "vsn1", "vsn1", "vsn2", true))
    listener.onMatch(VersionID(abPair, "id1"), "vsn1"); expectLastCall
    replayAll

    policy.onChange(DownstreamCorrelatedPairChangeEvent(VersionID(abPair, "id1"), JUL_8_2010_1, JUL_8_2010_2, "vsn1", "vsn2"))
    verifyAll
  }


  //
  // Standard Types
  //

  protected case class UpstreamVersion(id:VersionID, date:DateTime, lastUpdate:DateTime, vsn:String)
  protected case class UpstreamVersionAnswer(hs:Seq[UpstreamVersion]) extends IAnswer[Unit] {
    def answer {
      val args = EasyMock.getCurrentArguments
      val cb = args(2).asInstanceOf[Function4[VersionID, DateTime, DateTime, String, Unit]]

      hs.foreach { case UpstreamVersion(id, date, lastUpdate, vsn) =>
        cb(id, date, lastUpdate, vsn)
      }
    }
  }
  protected case class DownstreamVersion(id:VersionID, date:DateTime, lastUpdate:DateTime, usvn:String, dsvn:String)
  protected case class DownstreamVersionAnswer(hs:Seq[DownstreamVersion]) extends IAnswer[Unit] {
    def answer {
      val args = EasyMock.getCurrentArguments
      val cb = args(2).asInstanceOf[Function5[VersionID, DateTime, DateTime, String, String, Unit]]

      hs.foreach { case DownstreamVersion(id, date, lastUpdate, uvsn, dvsn) =>
        cb(id, date, lastUpdate, uvsn, dvsn)
      }
    }
  }

  protected def expectUpstreamSync(pair:String, dates:DateConstraint, gran:RangeGranularity, partResp:Seq[VersionDigest], storeResp:Seq[UpstreamVersion]) {
    expect(usMock.queryDigests(dates.start, dates.end, gran)).andReturn(partResp)
    store.queryUpstreams(EasyMock.eq(pair), EasyMock.eq(dates), anyUnitF4)
      expectLastCall[Unit].andAnswer(UpstreamVersionAnswer(storeResp))
  }
  protected def expectDownstreamSync(pair:String, dates:DateConstraint, gran:RangeGranularity, partResp:Seq[VersionDigest], storeResp:Seq[DownstreamVersion]) {
    expect(dsMock.queryDigests(dates.start, dates.end, gran)).andReturn(partResp)
    store.queryDownstreams(EasyMock.eq(pair), EasyMock.eq(dates), anyUnitF5)
      expectLastCall[Unit].andAnswer(DownstreamVersionAnswer(storeResp))
  }
}