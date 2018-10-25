package net.vankaam.websocket

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.joda.time.Duration
import org.scalatest.AsyncFlatSpec

import scala.async.Async.{async, await}



case class MapForm(Id:Long, _enm:String, _kf:String)

class LoginHttpClientSpec  extends AsyncFlatSpec {
  val configuredTimeout = ConfigFactory.load().getInt("websocketclient.loginpatience")
  implicit val system: ActorSystem = ActorSystem.create("WebSocketClient")
  private val config = ConfigFactory.load


  val loginHttpClientConfig = LoginHttpClientConfig(
    cookieTimeout=Duration.millis(configuredTimeout*1000),
    cookieRequestTimeout=Duration.millis(configuredTimeout*1000),
    loginUri=config.getString("test.loginclientspec.uri"),
      loginUser=config.getString("test.loginclientspec.username"),
        loginPassword=config.getString("test.loginclientspec.password")
  )

  "LoginHttpClient.PostWitLogin" should "login and post data" in async {
    val client = new LoginHttpClient(ConfigFactory.load(),loginHttpClientConfig,this.getClass.getClassLoader)


    val data1 = "<fes:Filter xmlns:fes=\"http://www.opengis.net/fes/2.0\">\n\t<fes:PropertyIsGreaterThan>\n         <fes:ValueReference>Id</fes:ValueReference>\n        <fes:Literal>0</fes:Literal>\n    </fes:PropertyIsGreaterThan>\n</fes:Filter>"
    val data2 = "<fes:Filter xmlns:fes=\"http://www.opengis.net/fes/2.0\">\n\t<fes:PropertyIsGreaterThan>\n         <fes:ValueReference>Id</fes:ValueReference>\n        <fes:Literal>10</fes:Literal>\n    </fes:PropertyIsGreaterThan>\n</fes:Filter>"

    val result1 = await(client.postRawXmlWithLogin[Array[MapForm]]("https://localhost/WebApi/Entities/getByFilter/Geo.MapForm/0/1")(data1)(loginHttpClientConfig.cookieTimeout))
    val result2 = await(client.postRawXmlWithLogin[Array[MapForm]]("https://localhost/WebApi/Entities/getByFilter/Geo.MapForm/0/1")(data2)(loginHttpClientConfig.cookieTimeout))

    assert(result1.right.get.length == 1)
    assert(result2.right.get.length == 1)
    assert(result1 != result2)
  }


}
