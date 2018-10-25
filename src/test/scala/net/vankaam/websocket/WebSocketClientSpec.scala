package net.vankaam.websocket

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterEach}
import org.scalatest.tagobjects.Slow

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection._
import scala.concurrent.blocking
import scala.concurrent.duration._


class WebSocketClientSpec extends AsyncFlatSpec with BeforeAndAfterEach with LazyLogging {

  private val config = ConfigFactory.load
  private val uri = config.getString("test.websocketclientspec.uri")
  private val data = config.getString("test.websocketclientspec.data")

  private val wssLoginUri = config.getString("test.websocketclientspec.wssloginuri")
  private val wssUri = config.getString("test.websocketclientspec.wssuri")
  private val username = config.getString("test.websocketclientspec.username")
  private val password = config.getString("test.websocketclientspec.password")
  private val wssData = config.getString("test.websocketclientspec.wssData")
  private val cookieTimeout = 5 seconds

  /*
  "WebSocketClient" should "Retrieve and push data from a websocket" taggedAs Slow in async {

    val buffer = new mutable.ListBuffer[String]()
    val socket = new WebSocketClient(uri,data,buffer+=_,None)
    await(blocking {socket.open()})
    await(blocking{socket.poll(0,200)})
    await(blocking{socket.poll(200,200)})


    logger.info(s"Closing socket")
    socket.close()
    assert(buffer.size == 400)
  }
  */

  "WebSocketClient with Cookie" should "log in and use the cookie on the websocket" taggedAs Slow in async {
    val loginRequest = LoginRequest(username,password)
    val loginClient = new LoginCookieClient(wssLoginUri,cookieTimeout,loginRequest)

    val buffer = new mutable.ListBuffer[String]()
    val socket = new WebSocketClient(wssUri,wssData,buffer+=_,Some(loginClient),ConfigFactory.load(),this.getClass.getClassLoader)

    await(blocking{socket.open()})
    await(blocking{socket.poll(0,1)})
    await(blocking{socket.poll(1,1)})

    logger.info(s"Closing socket")
    socket.close()
    assert(buffer.size == 2)
  }

  it should "close the connection if all data has been recieved"  taggedAs Slow in async {
    val loginRequest = LoginRequest(username,password)
    val loginClient = new LoginCookieClient(wssLoginUri,cookieTimeout,loginRequest)

    val buffer = new mutable.ListBuffer[String]()
    val socket = new WebSocketClient(wssUri,wssData,buffer+=_,Some(loginClient),ConfigFactory.load(),this.getClass.getClassLoader)

    await(blocking {socket.open()})
    await(blocking{socket.poll(0,1)})
    await(blocking{socket.poll(1,200)})

    logger.info(s"Closing socket")
    socket.close()
    assert(buffer.size >= 2)
  }

  it should "throw an exception if an unknown entity has been recieved" taggedAs Slow in async {
    val loginRequest = LoginRequest(username,password)
    val loginClient = new LoginCookieClient(wssLoginUri,cookieTimeout,loginRequest)

    val buffer = new mutable.ListBuffer[String]()
    val socket = new WebSocketClient(wssUri,"idonotexist",buffer+=_,Some(loginClient),ConfigFactory.load(),this.getClass.getClassLoader)

    val r = await(blocking {socket.open()}.map(Right(_)).recover {case e => Left(e)})

    assert(r.isLeft)
  }

/*
  "WebSocketClient.open" should "return none when the server denies the request" in async {
    val loginRequest = LoginRequest(username,password)
    val loginClient = new LoginCookieClient(wssLoginUri,cookieTimeout,loginRequest)

    val buffer = new mutable.ListBuffer[String]()
    val socket = new WebSocketClient(wssUri,wssData,buffer+=_,Some(loginClient),ConfigFactory.load(),this.getClass.getClassLoader)


    val result = await(socket.open())
    assert(!result)
  }
*/

  /** TODO: This test assumes an error during the poll */
/*
  it should "throw an exception during polling if an error occurs during the poll" taggedAs Slow in async{
    val loginRequest = LoginRequest(username,password)
    val loginClient = new LoginCookieClient(wssLoginUri,loginRequest)

    val buffer = new mutable.ListBuffer[String]()
    val socket = new WebSocketClient(wssUri,"Cir.WatInsolventieViewPerson",buffer+=_,Some(loginClient))

    val r = await(blocking {socket.open()}.map(Right(_)).recover {case e => Left(e)})
    val f = await(blocking{socket.poll(0,5000)}.map(Right(_)).recover {case e => Left(e)})

    assert(f.isLeft)
  }
*/
}
