package net.vankaam.websocket

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.tagobjects.Slow
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll}

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * TODO: Implementation
  */
class LoginClientSpec extends AsyncFlatSpec with BeforeAndAfterAll with LazyLogging {
  val configuredTimeout = ConfigFactory.load().getInt("websocketclient.loginpatience")
  val timeout = PatienceConfiguration.Timeout(Duration(configuredTimeout,SECONDS))
  implicit val system: ActorSystem = ActorSystem.create("WebSocketClient")



  private val config = ConfigFactory.load
  private val uri = config.getString("test.loginclientspec.uri")
  private val userName = config.getString("test.loginclientspec.username")
  private val password = config.getString("test.loginclientspec.password")

  "LoginClient" should "be able to retrieve a cookie from a webrequest" taggedAs Slow in async {
    //Arrange
    val client = new LoginCookieClient(uri,LoginRequest(userName,password))
    val cookie = await(client.GetLoginCookie)

    assert(cookie != null)
  }

  it should "throw an exception if the login is incorrect" taggedAs Slow in async {
    val client = new LoginCookieClient(uri,LoginRequest(userName,userName))
    val f = client.GetLoginCookie
    ScalaFutures.whenReady(f.failed,timeout) { e => assert(e.getMessage.contains("Message"))   }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}

