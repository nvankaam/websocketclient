package net.vankaam.websocket

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.tagobjects.Slow
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterEach}

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * TODO: Implementation
  */
class LoginClientSpec extends AsyncFlatSpec with BeforeAndAfterEach with LazyLogging {
  val timeout = PatienceConfiguration.Timeout(30.seconds)

  private val config = ConfigFactory.load
  private val uri = config.getString("test.loginclientspec.uri")
  private val userName = config.getString("test.loginclientspec.username")
  private val password = config.getString("test.loginclientspec.password")

  "LoginClient" should "be able to retrieve a cookie from a webrequest" taggedAs(Slow) in async {
    //Arrange
    val client = new LoginCookieClient()
    val cookie = await(client.GetLoginCookie(uri,LoginRequest(userName,password)))

    assert(cookie != null)
  }

  it should "throw an exception if the login is incorrect" taggedAs(Slow) in async {
    val client = new LoginCookieClient()
    val f = client.GetLoginCookie(uri,LoginRequest(userName,userName))
    ScalaFutures.whenReady(f.failed,timeout) { e => assert(e.getMessage.contains("Message"))   }
  }
}

