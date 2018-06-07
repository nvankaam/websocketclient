package net.vankaam.websocket

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterEach}
import org.scalatest.tagobjects.Slow

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection._
import scala.concurrent.blocking


class WebSocketClientSpec extends AsyncFlatSpec with BeforeAndAfterEach with LazyLogging {

  private val config = ConfigFactory.load
  private val uri = config.getString("test.websocketclientspec.uri")
  private val data = config.getString("test.websocketclientspec.data")

  private val wssLoginUri = config.getString("test.websocketclientspec.wssloginuri")
  private val wssUri = config.getString("test.websocketclientspec.wssuri")
  private val username = config.getString("test.websocketclientspec.username")
  private val password = config.getString("test.websocketclientspec.password")
  private val wssData = config.getString("test.websocketclientspec.wssData")

  "WebSocketClient" should "Retrieve and push data from a websocket" taggedAs(Slow) in async {

    val buffer = new mutable.ListBuffer[String]()
    val socket = new WebSocketClient(uri,data,buffer+=_,None)
    await(blocking {socket.open()})
    await(blocking{socket.poll(0,200)})
    await(blocking{socket.poll(200,200)})


    logger.info(s"Closing socket")
    socket.close()
    assert(buffer.size == 400)
  }

  "WebSocketClient with Cookie" should "log in and use the cookie on the websocket" taggedAs(Slow) in async {
    val loginClient = new LoginCookieClient()
    val loginRequest = LoginRequest(username,password)
    val cookieFactory = () => loginClient.GetCookieHeader(wssLoginUri,loginRequest)

    val buffer = new mutable.ListBuffer[String]()
    val socket = new WebSocketClient(wssUri,wssData,buffer+=_,Some(cookieFactory))

    await(blocking {socket.open()})
    await(blocking{socket.poll(0,2)})
    await(blocking{socket.poll(2,2)})

    logger.info(s"Closing socket")
    socket.close()
    assert(buffer.size == 4)
  }

  //TODO: Add unit (not integration) tests for the entire behavior
}
