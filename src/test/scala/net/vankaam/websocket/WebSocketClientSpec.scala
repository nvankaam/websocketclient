package net.vankaam.websocket

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterEach}
import org.scalatest.tagobjects.Slow

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable._
import scala.concurrent.blocking


class WebSocketClientSpec extends AsyncFlatSpec with BeforeAndAfterEach with LazyLogging {

  private val config = ConfigFactory.load
  private val uri = config.getString("test.websocketclientspec.uri")
  private val data = config.getString("test.websocketclientspec.data")

  "WebSocketClient" should "Retrieve and push data from a websocket" taggedAs(Slow) in async {

    val buffer = new ListBuffer[String]()
    val socket = new WebSocketClient(uri,data,buffer+=_,None)
    await(blocking {socket.open()})
    await(blocking{socket.poll(0,200)})
    await(blocking{socket.poll(200,200)})


    logger.info(s"Closing socket")
    socket.close()
    assert(buffer.size == 400)
  }

  //TODO: Add unit (not integration) tests for the entire behavior
}
