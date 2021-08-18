package zhttp.experiment

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.handler.codec.http._
import zhttp.experiment.HttpMessage.HResponse
import zhttp.experiment.internal.HttpMessageAssertions
import zhttp.http._
import zhttp.service.EventLoopGroup
import zio._
import zio.duration.durationInt
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect._
import zio.test._

/**
 * Be prepared for some real nasty runtime tests.
 */
object HttpEndpointSpec extends DefaultRunnableSpec with HttpMessageAssertions {
  private val env                                  = EventLoopGroup.auto(1)
  private val Ok: HResponse[Any, Nothing, Nothing] = HResponse()

  def spec =
    suite("HttpEndpoint")(
      EmptySpec,
      SucceedEmptySpec,
      SucceedOkSpec,
      SucceedFailSpec,
      FailCauseSpec,
      suite("request")(
        CompleteRequestSpec,
        BufferedRequestSpec,
        AnyRequestSpec,
      ),
      UnmatchedPathSpec,
      MatchedPathSpec,
      CombineSpec,
      EchoCompleteResponseSpec,
      EchoStreamingResponseSpec,
    ).provideCustomLayer(env) @@ timeout(10 seconds)

  /**
   * Spec for asserting AnyRequest fields and behaviour
   */
  def AnyRequestSpec = {
    suite("succeed(AnyRequest)")(
      testM("status is 200") {
        val res = HttpEndpoint.mount(Http.collect[AnyRequest](_ => Ok)).getResponse
        assertM(res)(isResponse(status(200)))
      },
      testM("status is 500") {
        val res = HttpEndpoint.mount(Http.collectM[AnyRequest](_ => ZIO.fail(new Error("SERVER ERROR")))).getResponse
        assertM(res)(isResponse(status(500)))
      },
      testM("status is 404") {
        val res = HttpEndpoint.mount(Http.empty.contramap[AnyRequest](i => i)).getResponse
        assertM(res)(isResponse(status(404)))
      },
      testM("status is 200") {
        val res = HttpEndpoint.mount(Http.collectM[AnyRequest](_ => UIO(Ok))).getResponse
        assertM(res)(isResponse(status(200)))
      },
    )
  }

  /**
   * Spec for asserting BufferedRequest fields and behaviour
   */

  def BufferedRequestSpec = {
    suite("succeed(Buffered)")(
      testM("status is 200") {
        val res = HttpEndpoint.mount(Http.collect[BufferedRequest[ByteBuf]](_ => Ok)).getResponse
        assertM(res)(isResponse(status(200)))
      },
      testM("status is 500") {
        val res = HttpEndpoint
          .mount(Http.collectM[BufferedRequest[ByteBuf]](_ => ZIO.fail(new Error("SERVER ERROR"))))
          .getResponse
        assertM(res)(isResponse(status(500)))
      },
      testM("status is 404") {
        val res = HttpEndpoint.mount(Http.empty.contramap[BufferedRequest[ByteBuf]](i => i)).getResponse
        assertM(res)(isResponse(status(404)))
      },
      testM("status is 200") {
        val res = HttpEndpoint.mount(Http.collectM[BufferedRequest[ByteBuf]](_ => UIO(Ok))).getResponse
        assertM(res)(isResponse(status(200)))
      },
    )
  }

  /**
   * Spec for asserting CompleteRequest fields and behaviour
   */

  def CompleteRequestSpec = {
    suite("succeed(CompleteRequest)")(
      testM("status is 200") {
        val res = HttpEndpoint.mount(Http.collect[CompleteRequest[ByteBuf]](_ => Ok)).getResponse
        assertM(res)(isResponse(status(200)))
      },
      testM("status is 500") {
        val res = HttpEndpoint
          .mount(Http.collectM[CompleteRequest[ByteBuf]](_ => ZIO.fail(new Error("SERVER ERROR"))))
          .getResponse
        assertM(res)(isResponse(status(500)))
      },
      testM("status is 404") {
        val res = HttpEndpoint.mount(Http.empty.contramap[CompleteRequest[ByteBuf]](i => i)).getResponse
        assertM(res)(isResponse(status(404)))
      },
    )
  }

  /**
   * Spec for asserting behaviour of an failing endpoint
   */
  def FailCauseSpec = {
    suite("fail(cause)")(
      testM("status is 500") {
        val res = HttpEndpoint.fail(new Error("SERVER_ERROR")).getResponse
        assertM(res)(isResponse(status(500)))
      },
      testM("content is SERVER_ERROR") {
        val res = HttpEndpoint.fail(new Error("SERVER_ERROR")).getResponse
        assertM(res)(isResponse(isContent(hasBody("SERVER_ERROR"))))
      },
      testM("headers are set") {
        val res = HttpEndpoint.fail(new Error("SERVER_ERROR")).getResponse
        assertM(res)(isResponse(header("content-length")))
      },
    )
  }

  /**
   * Spec for an Endpoint that succeed with a failing Http
   */
  def SucceedFailSpec = {

    suite("succeed(fail)")(
      testM("status is 500") {
        val res = HttpEndpoint.mount(Http.fail(new Error("SERVER_ERROR"))).getResponse
        assertM(res)(isResponse(status(500)))
      },
      testM("content is SERVER_ERROR") {
        val res = HttpEndpoint.mount(Http.fail(new Error("SERVER_ERROR"))).getResponse
        assertM(res)(isResponse(isContent(hasBody("SERVER_ERROR"))))
      },
      testM("headers are set") {
        val res = HttpEndpoint.mount(Http.fail(new Error("SERVER_ERROR"))).getResponse
        assertM(res)(isResponse(header("content-length")))
      },
    )
  }

  /**
   * Spec for an Endpoint that succeeds with a succeeding Http
   */
  def SucceedOkSpec = {
    suite("succeed(ok)")(
      testM("status is 200") {
        val res = HttpEndpoint.mount(Http.succeed(Ok)).getResponse
        assertM(res)(isResponse(status(200)))
      },
      suite("POST")(
        testM("status is 200") {
          val content = List("A", "B", "C")
          val res     = HttpEndpoint.mount(Http.succeed(Ok)).getResponse(method = HttpMethod.POST, content = content)
          assertM(res)(isResponse(status(200)))
        },
      ),
      testM("headers are empty") {
        val res = HttpEndpoint.mount(Http.succeed(Ok)).getResponse
        assertM(res)(isResponse(noHeader))
      },
      testM("headers are set") {
        val res = HttpEndpoint.mount(Http.succeed(HResponse(headers = List(Header("key", "value"))))).getResponse
        assertM(res)(isResponse(header("key", "value")))
      },
      testM("version is 1.1") {
        val res = HttpEndpoint.mount(Http.succeed(Ok)).getResponse
        assertM(res)(isResponse(version("HTTP/1.1")))
      },
      testM("version is 1.1") {
        val res = HttpEndpoint.mount(Http.succeed(Ok)).getResponse
        assertM(res)(isResponse(version("HTTP/1.1")))
      },
    )
  }

  /**
   * Spec for an Endpoint that succeeds with an empty Http
   */
  def SucceedEmptySpec = {
    suite("succeed(empty)")(
      testM("status is 404") {
        val res = HttpEndpoint.empty.getResponse
        assertM(res)(isResponse(status(404)))
      },
      testM("headers are empty") {
        val res = HttpEndpoint.empty.getResponse
        assertM(res)(isResponse(noHeader))
      },
      testM("version is 1.1") {
        val res = HttpEndpoint.empty.getResponse
        assertM(res)(isResponse(version("HTTP/1.1")))
      },
      testM("version is 1.1") {
        val res = HttpEndpoint.empty.getResponse
        assertM(res)(isResponse(version("HTTP/1.1")))
      },
    )
  }

  /**
   * Spec for an Endpoint that is empty
   */
  def EmptySpec = {
    suite("empty")(
      suite("GET")(
        testM("status is 404") {
          val res = HttpEndpoint.empty.getResponse
          assertM(res)(isResponse(status(404)))
        },
        testM("headers are empty") {
          val res = HttpEndpoint.empty.getResponse
          assertM(res)(isResponse(noHeader))
        },
        testM("version is 1.1") {
          val res = HttpEndpoint.empty.getResponse
          assertM(res)(isResponse(version("HTTP/1.1")))
        },
        testM("version is 1.1") {
          val res = HttpEndpoint.empty.getResponse
          assertM(res)(isResponse(version("HTTP/1.1")))
        },
      ),
      suite("POST")(
        testM("status is 404") {
          val res = HttpEndpoint.empty.getResponse(method = HttpMethod.POST, content = List("A", "B", "C"))
          assertM(res)(isResponse(status(404)))
        },
      ),
    )
  }

  /**
   * Spec for combining multiple Endpoints that succeed
   */
  def CombineSpec = {
    suite("orElse")(
      testM("status is 200") {
        val a   = HttpEndpoint.mount(Root / "a")(Http.succeed(HResponse(status = Status.OK)))
        val b   = HttpEndpoint.mount(Root / "b")(Http.succeed(HResponse(status = Status.CREATED)))
        val res = (a <> b).getResponse("/a")
        assertM(res)(isResponse(status(200)))
      },
      testM("matches first") {
        val a   = HttpEndpoint.mount(Root / "a")(Http.succeed(HResponse(status = Status.OK)))
        val b   = HttpEndpoint.mount(Root / "a")(Http.succeed(HResponse(status = Status.CREATED)))
        val res = (a <> b).getResponse("/a")
        assertM(res)(isResponse(status(200)))
      },
      testM("status is 404") {
        val a   = HttpEndpoint.mount(Root / "a")(Http.succeed(HResponse(status = Status.OK)))
        val b   = HttpEndpoint.mount(Root / "b")(Http.succeed(HResponse(status = Status.CREATED)))
        val res = (a <> b).getResponse("/c")
        assertM(res)(isResponse(status(404)))
      },
    )
  }

  /**
   * Spec to handle cases when no endpoint matches
   */
  def UnmatchedPathSpec = {
    suite("unmatched path /abc")(
      testM("type AnyRequest") {
        val res = HttpEndpoint.mount(Root / "abc")(Http.collect[AnyRequest](_ => Ok)).getResponse
        assertM(res)(isResponse(status(404)))
      },
      testM("type BufferedRequest") {
        val res = HttpEndpoint.mount(Root / "abc")(Http.collect[BufferedRequest[ByteBuf]](_ => Ok)).getResponse
        assertM(res)(isResponse(status(404)))
      },
      testM("type CompleteRequest") {
        val res = HttpEndpoint.mount(Root / "abc")(Http.collect[CompleteRequest[ByteBuf]](_ => Ok)).getResponse
        assertM(res)(isResponse(status(404)))
      },
      testM("type Any") {
        val res = HttpEndpoint.mount(Root / "abc")(Http.succeed(Ok)).getResponse
        assertM(res)(isResponse(status(404)))
      },
    )
  }

  /**
   * Spec to handle cases when endpoint matches
   */
  def MatchedPathSpec = {
    suite("matched path")(
      testM("exact match") {
        val res = HttpEndpoint.mount(Root / "abc")(Http.collect[AnyRequest](_ => Ok)).getResponse("/abc")
        assertM(res)(isResponse(status(200)))
      },
      testM("starts with match") {
        val res = HttpEndpoint.mount(Root / "abc")(Http.collect[AnyRequest](_ => Ok)).getResponse("/abc")
        assertM(res)(isResponse(status(200)))
      },
      testM("does not match") {
        val res = HttpEndpoint.mount(Root / "abc")(Http.collect[AnyRequest](_ => Ok)).getResponse("/abcd")
        assertM(res)(isResponse(status(404)))
      },
    )
  }

  def echoComplete(req: BufferedRequest[ByteBuf]): ZIO[Any, Nothing, HResponse[Any, Nothing, ByteBuf]] =
    for {
      content <- req.content.runCollect.map(chunk => Unpooled.copiedBuffer(chunk.toArray: _*))
    } yield HResponse(content = HContent.complete(content))

  def echoComplete(req: CompleteRequest[ByteBuf]): ZIO[Any, Nothing, HResponse[Any, Nothing, ByteBuf]] =
    UIO(HResponse(content = HContent.complete(req.content)))

  def EchoCompleteResponseSpec = {
    suite("CompleteResponse")(
      suite("CompleteRequest")(
        testM("status is 200") {
          val res = HttpEndpoint.mount(Http.collectM[CompleteRequest[ByteBuf]](echoComplete(_))).getResponse
          assertM(res)(isResponse(status(200)))
        },
        testM("content is 'ABCD'") {
          val content = HttpEndpoint.mount(Http.collectM[CompleteRequest[ByteBuf]](echoComplete(_))).getContent
          assertM(content)(equalTo("ABCD"))
        } @@ nonFlaky,
      ),
      suite("BufferedRequest")(
        testM("status is 200") {
          val res = HttpEndpoint.mount(Http.collectM[BufferedRequest[ByteBuf]](echoComplete(_))).getResponse
          assertM(res)(isResponse(status(200)))
        },
        testM("content is 'ABCD'") {
          val content = HttpEndpoint.mount(Http.collectM[BufferedRequest[ByteBuf]](echoComplete(_))).getContent
          assertM(content)(equalTo("ABCD"))
        } @@ nonFlaky,
      ) @@ timeout(10 seconds),
    )
  }

  def EchoStreamingResponseSpec = {
    val streamingResponse = HResponse(content =
      HContent.fromStream(
        ZStream
          .fromIterable(List("A", "B", "C", "D"))
          .map(text => Unpooled.copiedBuffer(text.getBytes)),
      ),
    )

    suite("StreamingResponse")(
      suite("CompleteRequest")(
        testM("status is 200") {
          val res = HttpEndpoint.mount(Http.collect[CompleteRequest[ByteBuf]](_ => streamingResponse)).getResponse
          assertM(res)(isResponse(status(200)))
        },
        testM("content is 'ABCD'") {
          val content = HttpEndpoint.mount(Http.collect[CompleteRequest[ByteBuf]](_ => streamingResponse)).getContent
          assertM(content)(equalTo("ABCD"))
        } @@ nonFlaky,
      ),
      suite("BufferedRequest")(
        testM("status is 200") {
          val res = HttpEndpoint.mount(Http.collect[BufferedRequest[ByteBuf]](_ => streamingResponse)).getResponse
          assertM(res)(isResponse(status(200)))
        },
        testM("content is 'ABCD'") {
          val content = HttpEndpoint.mount(Http.collect[BufferedRequest[ByteBuf]](_ => streamingResponse)).getContent
          assertM(content)(equalTo("ABCD"))
        } @@ nonFlaky,
      ),
    )
  }
}
