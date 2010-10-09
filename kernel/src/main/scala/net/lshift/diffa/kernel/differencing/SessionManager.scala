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

import net.lshift.diffa.kernel.events.VersionID
import org.joda.time.DateTime
import reflect.BeanProperty

/**
 * A SessionManager provides a stateful view of the differencing of pairs, and provides mechanisms for polling
 * for changes in the difference states of those pairs.
 */
trait SessionManager {

  def defaultDateBounds() = {
    val now = new DateTime()
    (now.minusYears(1), now.plusYears(1))
  }

  /**
   * Calls start/3 with default time bounds
   */
  def start(scope:SessionScope) : String = {
    val (from, until) = defaultDateBounds()
    start(scope, from, until)
  }

  /**
   * Calls start/4 with default time bounds
   */
  def start(scope: SessionScope, listener: DifferencingListener) : Unit = {
    val (from, until) = defaultDateBounds()
    start(scope, from, until, listener)
  }


  /**
   *  Requests a new session be created for the given pair. The created session will remain active whilstever
   * regular polling occurs on the retrieve* methods.
   * @param scope The requested scope for the session, detailing the pairs that are to be inspected.
   * @param start The lower time bound of the session
   * @param end The upper time bound of the session
   * @return the id of the session
   */
  def start(scope:SessionScope, start:DateTime, end:DateTime):String

  /**
   * Requests a new session be created for the given pair. Any generated events should be provided to the listener.
   * In contrast to start/3, this does not return a sessionId because the state of the session is tied to
   * the listener reference that has been passed in.
   * The created session will remain active until <code>end</code> is called with the listener reference.
   * @param scope The requested scope for the session, detailing the pairs that are to be inspected.
   * @param start The lower time bound of the session
   * @param end The upper time bound of the session
   */
  def start(scope:SessionScope, start:DateTime, end:DateTime, listener:DifferencingListener): Unit

  /**
   * Retrieves a version for the given session.
   */
  def retrieveSessionVersion(sessionID:String):String

  /**
   * Retrieves all events known to this session. Will only include unmatched events.
   * @throws InvalidSessionIDException if the requested session does not exist or has expired.
   */
  def retrieveAllEvents(sessionID:String):Seq[SessionEvent]

  /**
   * Retrieves all events that have occurred within a session since the provided sequence id.
   * @param sessionID the session
   * @param evtSeqId the last known sequence id. All events occurring after (not including) this event will be returned.
   * @throws InvalidSessionIDException if the requested session does not exist or has expired.
   * @throws SequenceOutOfDateException if the provided sequence id is too old, and necessary sync information cannot be
   *    provided. A client will need to recover by calling retrieveAllEvents and re-process all events.
   * @throws InvalidSequenceNumberException if the provided sequence id is unknown to the session.
   */
  def retrieveEventsSince(sessionID:String, evtSeqId:String):Seq[SessionEvent]

  /**
   * Retrieves any additional information that the session manager knows about an event (eg, mismatched hashes,
   * differing content bodies). This information may be retrieved by the manager on demand from remote sources, so
   * should generally only be called on explicit user request.
   */
  def retrieveEventDetail(sessionID:String, evtSeqId:String) : String

  /**
   * Indicates that the given session is no longer required.
   */
  def end(sessionID:String)

  /**
   * Indicates that the given session is no longer required.
   */
  def end(pair: String, listener: DifferencingListener)

  /**
   * Informs the session manager that a pair has been updated.
   */
  def onUpdatePair(pairKey:String)
}

class InvalidSessionIDException extends Exception
class InvalidSequenceNumberException extends Exception
class SequenceOutOfDateException extends Exception

case class SessionEvent(
  @BeanProperty var seqId:String,
  @BeanProperty var objId:VersionID,
  @BeanProperty var detectedAt:DateTime,
  @BeanProperty var state:MatchState,
  @BeanProperty var upstreamVsn:String,
  @BeanProperty var downstreamVsn:String) {

 def this() = this(null, null, null, MatchState.UNMATCHED, null, null)
    
}
