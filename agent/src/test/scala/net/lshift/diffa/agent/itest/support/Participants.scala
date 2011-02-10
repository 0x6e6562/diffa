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

package net.lshift.diffa.agent.itest.support

import net.lshift.diffa.kernel.protocol.ProtocolHandler
import net.lshift.diffa.kernel.participants.{DownstreamParticipant, UpstreamParticipant}
import net.lshift.diffa.participants.ParticipantRpcServer
import concurrent.SyncVar
import org.slf4j.LoggerFactory
import net.lshift.diffa.messaging.json.{DownstreamParticipantRestClient, UpstreamParticipantRestClient, DownstreamParticipantHandler, UpstreamParticipantHandler}
import net.lshift.diffa.messaging.amqp._

/**
 * Helper objects for creation of HTTP/AMQP RPC chain for remote-controlling participants
 */
trait Participants {

  val upstreamUrl: String

  val downstreamUrl: String

  val inboundUrl: String

  def startUpstreamServer(upstream: UpstreamParticipant): Unit

  def startDownstreamServer(downstream: DownstreamParticipant): Unit

  def upstreamClient: UpstreamParticipant

  def downstreamClient: DownstreamParticipant

}

class HttpParticipants(usPort: Int, dsPort: Int) extends Participants {

  val log = LoggerFactory.getLogger(getClass)

  val upstreamUrl = "http://localhost:" + usPort

  val downstreamUrl = "http://localhost:" + dsPort

  val inboundUrl = null

  def startUpstreamServer(upstream: UpstreamParticipant) =
    forkServer(usPort, new UpstreamParticipantHandler(upstream))

  def startDownstreamServer(downstream: DownstreamParticipant) =
    forkServer(dsPort, new DownstreamParticipantHandler(downstream))

  private def forkServer(port: Int, handler: ProtocolHandler) {
    val server = new ParticipantRpcServer(port, handler)
    val startupSync = new SyncVar[Boolean]
    new Thread {
      override def run = {
        try {
          server.start
        }
        catch {
          case x:Exception => {
            log.error("Cannot start server on port: " + port)
            throw x
          }
        }
        startupSync.set(true)
      }
    }.start

    startupSync.get(5000) match {
      case None => throw new Exception("Forked server on " + port + " failed to start")
      case _    =>
    }
  }

  lazy val upstreamClient = new UpstreamParticipantRestClient(upstreamUrl)

  lazy val downstreamClient = new DownstreamParticipantRestClient(downstreamUrl)
}

case class AmqpParticipants(connectorHolder: ConnectorHolder,
                            usQueue: String,
                            dsQueue: String,
                            inboundQueue: String) extends Participants {

  private val timeout = 10000

  private var usServer: Option[AmqpRpcServer] = None
  private var dsServer: Option[AmqpRpcServer] = None

  val upstreamUrl = AmqpQueueUrl(usQueue).toString
  val downstreamUrl = AmqpQueueUrl(dsQueue).toString
  val inboundUrl = AmqpQueueUrl(inboundQueue).toString

  def startUpstreamServer(upstream: UpstreamParticipant) = {
    val server = new AmqpRpcServer(connectorHolder.connector, usQueue, new UpstreamParticipantHandler(upstream))
    server.start()
    usServer = Some(server)
  }

  def startDownstreamServer(downstream: DownstreamParticipant) = {
    val server = new AmqpRpcServer(connectorHolder.connector, dsQueue, new DownstreamParticipantHandler(downstream))
    server.start()
    dsServer = Some(server)
  }

  lazy val upstreamClient =
    new UpstreamParticipantAmqpClient(connectorHolder.connector, usQueue, timeout)

  lazy val downstreamClient =
    new DownstreamParticipantAmqpClient(connectorHolder.connector, dsQueue, timeout)
}