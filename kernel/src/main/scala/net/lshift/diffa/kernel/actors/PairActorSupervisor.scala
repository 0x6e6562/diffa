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

package net.lshift.diffa.kernel.actors

import se.scalablesolutions.akka.actor.ActorRegistry
import se.scalablesolutions.akka.actor.Actor._
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.differencing.VersionPolicyManager
import net.lshift.diffa.kernel.participants.ParticipantFactory
import net.lshift.diffa.kernel.config.ConfigStore

class PairActorSupervisor(val policyManager:VersionPolicyManager,
                          val config:ConfigStore,
                          val participantFactory:ParticipantFactory) {

  private val log = LoggerFactory.getLogger(getClass)

  // Initialize actors for any persistent pairs
  config.listGroups.foreach(g => g.pairs.foreach(p => startActor(p)) )

  def startActor(pair:net.lshift.diffa.kernel.config.Pair) = {
    val actors = ActorRegistry.actorsFor(pair.key)
    actors.length match {
      case 0 => {
        policyManager.lookupPolicy(pair.versionPolicyName) match {
          case Some(p) => {
            val us = participantFactory.createUpstreamParticipant(pair.upstream.url)
            val ds = participantFactory.createDownstreamParticipant(pair.downstream.url)
            ActorRegistry.register(actorOf(new PairActor(pair.key, us, ds, p)).start)
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
}