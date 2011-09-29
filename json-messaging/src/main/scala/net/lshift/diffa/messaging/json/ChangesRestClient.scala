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

package net.lshift.diffa.messaging.json

import net.lshift.diffa.kernel.client.ChangesClient
import net.lshift.diffa.kernel.events.{UpstreamChangeEvent, DownstreamChangeEvent, DownstreamCorrelatedChangeEvent, ChangeEvent}
import JSONEncodingUtils._
import net.lshift.diffa.kernel.frontend.wire.WireEvent._

/**
 * JSON-over-REST client for the changes endpoint.
 */
class ChangesRestClient(serverRootUrl:String, domain:String, endpoint:String, username:String = "guest", password:String = "guest")
    extends AbstractRestClient(serverRootUrl, "rest/" + domain + "/changes/", username, password)
        with ChangesClient {

  def onChangeEvent(evt:ChangeEvent) {
    val wire = evt match {
      case us:UpstreamChangeEvent => toWire(us)
      case ds:DownstreamChangeEvent => toWire(ds)
      case dsc:DownstreamCorrelatedChangeEvent => toWire(dsc)
    }
    submit(endpoint, serializeEvent(wire))
  }
}