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

package net.lshift.diffa.kernel.actors

import se.scalablesolutions.akka.actor.ActorRegistry
import se.scalablesolutions.akka.actor.Actor._
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.participants.ParticipantFactory
import net.lshift.diffa.kernel.config.ConfigStore
import net.lshift.diffa.kernel.events.PairChangeEvent
import net.lshift.diffa.kernel.differencing.{DifferencingListener, VersionPolicyManager}
import net.lshift.diffa.kernel.differencing.VersionCorrelationStoreFactory
import net.lshift.diffa.kernel.lifecycle.AgentLifecycleAware

case class PairActorSupervisor(policyManager:VersionPolicyManager,
                               config:ConfigStore,
                               participantFactory:ParticipantFactory,
                               stores:VersionCorrelationStoreFactory,
                               changeEventBusyTimeoutMillis:Long,
                               changeEventQuietTimeoutMillis:Long)
    extends ActivePairManager
    with PairPolicyClient

    with AgentLifecycleAware {

  private val log = LoggerFactory.getLogger(getClass)

  override def onAgentAssemblyCompleted = {
    // Initialize actors for any persistent pairs
    config.listGroups.foreach(g => g.pairs.foreach(p => startActor(p)) )
  }

  def startActor(pair:net.lshift.diffa.kernel.config.Pair) = {
    val actors = ActorRegistry.actorsFor(pair.key)
    actors.length match {
      case 0 => {
        policyManager.lookupPolicy(pair.versionPolicyName) match {
          case Some(p) => {
            val us = participantFactory.createUpstreamParticipant(pair.upstream)
            val ds = participantFactory.createDownstreamParticipant(pair.downstream)
            val pairActor = actorOf(new PairActor(pair.key, us, ds, p, stores(pair.key), changeEventBusyTimeoutMillis, changeEventQuietTimeoutMillis))
            ActorRegistry.register(pairActor.start)
            log.info("Started actor for key: " + pair.key)
          }
          case None    => log.error("Failed to find policy for name: " + pair.versionPolicyName)
        }

      }
      case 1    => log.warn("Attempting to re-spawn actor for key: " + pair.key)
      case x    => log.error("Too many actors for key: " + pair.key + "; actors = " + x)
    }
  }

  def stopActor(key:String) = {
    val actors = ActorRegistry.actorsFor(key)
    actors.length match {
      case 1 => {
        val actor = actors(0)
        ActorRegistry.unregister(actor)
        actor.stop
        log.info("Stopped actor for key: " + key)
      }
      case 0    => log.warn("Could not resolve actor for key: " + key)
      case x    => log.error("Too many actors for key: " + key + "; actors = " + x)
    }
  }

  def propagateChangeEvent(event:PairChangeEvent) = findActor(event.id.pairKey) ! ChangeMessage(event)

  def syncPair(pairKey:String, listener:DifferencingListener) = findActor(pairKey) ! DifferenceMessage(listener)

  def findActor(pairKey:String) = {
    val actors = ActorRegistry.actorsFor(pairKey)
    actors.length match {
      case 1 => actors(0)
      case 0 => {
        log.error("Could not resolve actor for key: " + pairKey)
        throw new RuntimeException("Unresolvable pair: " + pairKey)
      }
      case x => {
        log.error("Too many actors for key: " + pairKey + "; actors = " + x)
        throw new RuntimeException("Too many actors: " + pairKey)
      }
    }
  }
}