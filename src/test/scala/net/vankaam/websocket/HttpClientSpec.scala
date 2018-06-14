package net.vankaam.websocket

import org.scalatest.AsyncFlatSpec
import scala.async.Async.{async,await}

case class Data(name: String)
case class Response(data:String)

class HttpClientSpec extends AsyncFlatSpec {
  "HttpClient.post" should "Post data to an url and await the result" in async {
    val client = new HttpClient()
    val data = Data("dataname")
    val result:Response = await(client.post[Data,Response]("http://httpbin.org/post",data))

    assert(result.data == "{\"name\":\"dataname\"}")
  }
}
