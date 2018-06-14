package net.vankaam.websocket

import akka.actor.ActorSystem

object ActorSystemManager {
  implicit val actorSystem = ActorSystem("WebSocketClient")
}
