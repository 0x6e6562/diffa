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

import org.easymock.EasyMock._
import net.lshift.diffa.kernel.events.VersionID
import org.junit.{Before, Test}
import org.junit.Assert._
import org.joda.time.DateTime
import net.lshift.diffa.kernel.participants._
import net.lshift.diffa.kernel.matching.{MatchingStatusListener, EventMatcher, MatchingManager}
import net.lshift.diffa.kernel.actors.PairPolicyClient
import org.easymock.EasyMock
import net.lshift.diffa.kernel.config.{GroupContainer, Endpoint, ConfigStore}
import net.lshift.diffa.kernel.config.{Pair => DiffaPair}

/**
 * Test cases for the default session manager.
 */
class StubParticipantProtocolFactory extends ParticipantProtocolFactory {

  def createDownstreamParticipant(address: String, protocol: String) = {
    new DownstreamParticipant() {
      def generateVersion(entityBody:String) = null
      def invoke(actionId:String, entityId:String)  = null
      def queryAggregateDigests(bucketing:Map[String, CategoryFunction], constraints:Seq[QueryConstraint]) = null
      def queryEntityVersions(constraints:Seq[QueryConstraint]) = null
      def retrieveContent(id:String) = null
      def close() = ()
    }
  }
  def createUpstreamParticipant(address:String, protocol:String) = {
    new UpstreamParticipant() {
      def queryAggregateDigests(bucketing:Map[String, CategoryFunction], constraints:Seq[QueryConstraint]) = null
      def queryEntityVersions(constraints:Seq[QueryConstraint]) = null
      def invoke(actionId:String, entityId:String)  = null
      def retrieveContent(id:String) = null
      def close() = ()
    }
  }
  def supportsAddress(address:String, protocol:String) = true
}

class DefaultSessionManagerTest {
  val cacheProvider = new LocalSessionCacheProvider

  val listener1 = createStrictMock("listener1", classOf[DifferencingListener])
  val listener2 = createStrictMock("listener2", classOf[DifferencingListener])

  val matcher = createStrictMock("matcher", classOf[EventMatcher])

  val configStore = createStrictMock("configStore", classOf[ConfigStore])
  val matchingManager = createStrictMock("matchingManager", classOf[MatchingManager])
  matchingManager.addListener(anyObject.asInstanceOf[MatchingStatusListener]); expectLastCall.once
  replay(matchingManager)

  // TODO The versionPolicyManager and participantFactory should probably go into some
  // kind of test factory - i.e. look out for copy and paste

  val versionPolicy = createStrictMock("vp", classOf[VersionPolicy])


  val versionPolicyManager = new VersionPolicyManager()
  versionPolicyManager.registerPolicy("policy", versionPolicy)
  private val protocol1 = new StubParticipantProtocolFactory()
  private val protocol2 = new StubParticipantProtocolFactory()

  val participantFactory = new ParticipantFactory()
  participantFactory.registerFactory(protocol1)
  participantFactory.registerFactory(protocol2)

  val pairPolicyClient = createStrictMock("pairPolicyClient", classOf[PairPolicyClient])
  EasyMock.checkOrder(pairPolicyClient, false)

  val manager = new DefaultSessionManager(configStore, cacheProvider, matchingManager, versionPolicyManager, pairPolicyClient, participantFactory)
  verify(matchingManager); reset(matchingManager)    // The matching manager will have been called on session manager startup

  @Before
  def setupStubs = {
    // TODO consider using a stub factory to build stateful objects
    // that always just get copy and pasted
    // i.e. only stub out the behavior that actually care about and want to test
    // so in this example, everything above this comment should be expect() calls
    // and everything below should be stub() calls on a factory
    val u = Endpoint("1","http://foo.com", "application/json", "changes", "application/json", true)
    val d = Endpoint("2","http://bar.com", "application/json", "changes", "application/json", true)

    participantFactory.createUpstreamParticipant(u)
    participantFactory.createDownstreamParticipant(d)

    val pair1 = DiffaPair(key = "pair1", versionPolicyName = "policy", upstream = u, downstream = d)
    val pair2 = DiffaPair(key = "pair2", versionPolicyName = "policy", upstream = u, downstream = d)
        
    expect(configStore.getPair("pair")).andStubReturn(pair1)
    expect(configStore.getPair("pair2")).andStubReturn(pair2)
    expect(configStore.listGroups).andStubReturn(Seq(GroupContainer(null, Array(pair1, pair2))))
    expect(matchingManager.getMatcher("pair")).andStubReturn(Some(matcher))
    expect(matcher.isVersionIDActive(VersionID("pair", "id"))).andStubReturn(true)
    expect(matcher.isVersionIDActive(VersionID("pair", "id2"))).andStubReturn(false)

    replay(configStore, matchingManager, matcher)
  }

  def expectDifferenceForPair(pairs:String*)  = {
    pairs.foreach(pairKey => {
      val pairKeyEq = EasyMock.eq(pairKey)
      val diffListenerEq = isA(classOf[DifferencingListener])
      expect(pairPolicyClient.difference(pairKeyEq, diffListenerEq)).atLeastOnce
    })

    replay(pairPolicyClient)
  }
  def expectSyncAndDifferenceForPair(pairs:String*)  = {
    pairs.foreach(pairKey => {
      val pairKeyEq = EasyMock.eq(pairKey)
      val diffListenerEq = isA(classOf[DifferencingListener])

      expect(pairPolicyClient.difference(pairKeyEq, diffListenerEq)).atLeastOnce
    })
    pairs.foreach(pairKey => {
      val pairKeyEq = EasyMock.eq(pairKey)
      val diffListenerEq = isA(classOf[DifferencingListener])
      val pairSyncListenerEq = isA(classOf[PairSyncListener])

      expect(pairPolicyClient.scanPair(pairKeyEq, diffListenerEq, pairSyncListenerEq)).atLeastOnce
    })

    replay(pairPolicyClient)
  }

  @Test
  def shouldTriggerScanOnRequest = {
    expectSyncAndDifferenceForPair("pair1","pair2")
    replayAll

    manager.runScanForAllPairings

    verifyAll
  }

  @Test
  def shouldAlwaysInformMatchEvents {

    expect(listener1.onMatch(VersionID("pair", "id"), "vsn"))
    replayAll

    expectDifferenceForPair("pair")

    val session = manager.start(SessionScope.forPairs("pair"), listener1)
    manager.onMatch(VersionID("pair", "id"), "vsn")

    verifyAll
  }

  @Test
  def shouldNotTriggerMismatchEventsWhenIdIsActive {
    val timestamp = new DateTime()
    // Replay to get blank stubs
    replayAll

    expectDifferenceForPair("pair")

    val session = manager.start(SessionScope.forPairs("pair"), listener1)
    
    manager.onMismatch(VersionID("pair", "id"), timestamp, "uvsn", "dvsn")
    verifyAll
  }

  @Test
  def shouldTriggerMismatchEventsWhenIdIsInactive {
    val timestamp = new DateTime()
    expect(listener1.onMismatch(VersionID("pair", "id2"), timestamp, "uvsn", "dvsn"))
    replayAll

    expectDifferenceForPair("pair")

    val session = manager.start(SessionScope.forPairs("pair"), listener1)

    manager.onMismatch(VersionID("pair", "id2"), timestamp, "uvsn", "dvsn")
    verifyAll
  }

  // TODO Refactor this to deal with end() call in the manager
  @Test
  def shouldNoLongerInformRemovedListenerOfMatchEvents {
    val timestamp = new DateTime()
    replayAll

    expectDifferenceForPair("pair")

    val session = manager.start(SessionScope.forPairs("pair"), listener1)
    manager.end("pair", listener1)
    manager.onMatch(VersionID("pair", "id"), "vsn")
    manager.onMismatch(VersionID("pair", "id"), timestamp, "uvsn", "dvsn")
    verifyAll
  }

  @Test
  def shouldNotInformListenerOfEventsOnOtherPairs {
    val timestamp = new DateTime()
    replayAll

    expectDifferenceForPair("pair2")

    val session = manager.start(SessionScope.forPairs("pair2"), listener1)
    manager.onMatch(VersionID("pair", "id"), "vsn")
    manager.onMismatch(VersionID("pair", "id"), timestamp, "uvsn", "dvsn")
    verifyAll
  }

  @Test
  def shouldHandleExpiryOfAnEventThatIsNotCurrentlyPending {
    // This will frequently occur when a repair action occurs. A miscellaneous downstream will be seen, which the
    // correlation store will immediately match, but the EventMatcher will see as expiring.

    replayAll

    expectDifferenceForPair("pair")

    val session = manager.start(SessionScope.forPairs("pair"), listener1)
    manager.onDownstreamExpired(VersionID("pair", "unknownid"), "dvsn")
  }

  @Test
  def shouldTrackStateOfPairsForExplicitScopeSession {
    // Create a session that contains our given pair
    replayAll
    expectSyncAndDifferenceForPair("pair")
    val sessionId = manager.start(SessionScope.forPairs("pair"), listener1)

    // Initial state of all pairs should be "unknown"
    assertEquals(Map("pair" -> PairScanState.UNKNOWN), manager.retrievePairSyncStates(sessionId))

    // Start the initial sync
    manager.runSync(sessionId)

    // Query for the states associated. We should get back an entry for pair in "synchronising", since the stubs
    // don't notify of completion
    assertEquals(Map("pair" -> PairScanState.SYNCHRONIZING), manager.retrievePairSyncStates(sessionId))

    // Notify that the pair is now in Synchronised state
    manager.pairSyncStateChanged("pair", PairScanState.UP_TO_DATE)
    assertEquals(Map("pair" -> PairScanState.UP_TO_DATE), manager.retrievePairSyncStates(sessionId))

    // Start a sync. We should enter the synchronising state again
    manager.runSync(sessionId)
    assertEquals(Map("pair" -> PairScanState.SYNCHRONIZING), manager.retrievePairSyncStates(sessionId))

    // Notify that the pair is now in Failed state
    manager.pairSyncStateChanged("pair", PairScanState.FAILED)
    assertEquals(Map("pair" -> PairScanState.FAILED), manager.retrievePairSyncStates(sessionId))
  }

  @Test
  def shouldTrackStateOfPairsForImplicitScopeSession {
    // Create a session that contains all pairs
    replayAll
    expectSyncAndDifferenceForPair("pair1", "pair2")
    val sessionId = manager.start(SessionScope.all, listener1)

    // Query for the states associated. We should get back an entry for pair in "unknown"
    assertEquals(Map("pair1" -> PairScanState.UNKNOWN, "pair2" -> PairScanState.UNKNOWN),
      manager.retrievePairSyncStates(sessionId))

    assertEquals(Map("pair1" -> PairScanState.UNKNOWN, "pair2" -> PairScanState.UNKNOWN),
      manager.retrieveAllPairScanStates)

    // Notify that the pair1 is now in Synchronised state
    manager.pairSyncStateChanged("pair1", PairScanState.UP_TO_DATE)
    assertEquals(Map("pair1" -> PairScanState.UP_TO_DATE, "pair2" -> PairScanState.UNKNOWN),
      manager.retrievePairSyncStates(sessionId))

    assertEquals(Map("pair1" -> PairScanState.UP_TO_DATE, "pair2" -> PairScanState.UNKNOWN),
      manager.retrieveAllPairScanStates)

    // Notify that the pair2 is now in Failed state
    manager.pairSyncStateChanged("pair2", PairScanState.FAILED)
    assertEquals(Map("pair1" -> PairScanState.UP_TO_DATE, "pair2" -> PairScanState.FAILED),
      manager.retrievePairSyncStates(sessionId))

    assertEquals(Map("pair1" -> PairScanState.UP_TO_DATE, "pair2" -> PairScanState.FAILED),
      manager.retrieveAllPairScanStates)

    // Start a sync. We should enter the synchronising state again
    manager.runSync(sessionId)
    assertEquals(Map("pair1" -> PairScanState.SYNCHRONIZING, "pair2" -> PairScanState.SYNCHRONIZING),
      manager.retrievePairSyncStates(sessionId))

    assertEquals(Map("pair1" -> PairScanState.SYNCHRONIZING, "pair2" -> PairScanState.SYNCHRONIZING),
      manager.retrieveAllPairScanStates)
  }

  @Test
  def shouldReportUnknownSyncStateForRemovedPairs {
    // Create a session that contains our given pair
    replayAll
    expectDifferenceForPair("pair")
    val sessionId = manager.start(SessionScope.forPairs("pair"), listener1)

    // If we delete the pair, then the sync state should return the pair with an Unknown status
    manager.onDeletePair("pair")
    assertEquals(Map("pair" -> PairScanState.UNKNOWN), manager.retrievePairSyncStates(sessionId))
  }

  private def replayAll = replay(listener1, listener2)
  private def verifyAll = verify(listener1, listener2)
}