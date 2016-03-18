package eu.inn.facade.model

import eu.inn.binders.core.{ImplicitDeserializer, ImplicitSerializer}
import eu.inn.binders.dynamic.{Null, Value}
import eu.inn.binders.json._
import eu.inn.binders.naming.{CamelCaseToDashCaseConverter, DashCaseToCamelCaseConverter}
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.serialization.{ResponseHeader, StringDeserializer}
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.can.websocket.frame.{Frame, TextFrame}
import spray.http.HttpCharsets._
import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http._

trait FacadeMessage {
  def headers: Map[String, Seq[String]]
  def body: Value
  def toFrame: Frame
}

class UriSpecificSerializer extends ImplicitSerializer[Uri, JsonSerializer[_]] {
  override def write(serializer: JsonSerializer[_], value: Uri) = serializer.writeString(value.pattern.specific)
}

class UriSpecificDeserializer extends ImplicitDeserializer[Uri, JsonDeserializer[_]] {
  override def read(deserializer: JsonDeserializer[_]): Uri = Uri(deserializer.readString())
}

case class FacadeRequest(uri: Uri, method: String, headers: Map[String, Seq[String]], body: Value) extends FacadeMessage {
  def toDynamicRequest: DynamicRequest = {
    DynamicRequest(uri, DynamicBody(body),
      new HeadersBuilder(headers)
      .withMethod(method)
      .result()
    )
  }

  def toFrame: Frame = {
    implicit val uriSpecificSerializer = new UriSpecificSerializer
    TextFrame(this.toJson)
  }

  /*def correlationId: String = {
    val messageId = headers(Header.MESSAGE_ID)
    headers.getOrElse(Header.CORRELATION_ID, messageId).head
  }*/

  def clientCorrelationId: Option[String] = {
    val messageId = headers.getOrElse(FacadeHeaders.CLIENT_MESSAGE_ID, Seq.empty)
    headers.getOrElse(FacadeHeaders.CLIENT_CORRELATION_ID, messageId).headOption
  }

  def contentType: Option[String] = {
    headers.get(Header.CONTENT_TYPE).flatMap(_.headOption)
  }
}

// todo: converters make explicit

object FacadeRequest {
  val dashCaseToCamelCase = new DashCaseToCamelCaseConverter

  def apply(request: HttpRequest, remoteAddress: String): FacadeRequest = {
    val headers: Map[String,Seq[String]] = request.headers.groupBy(_.name).map { kv ⇒
      dashCaseToCamelCase.convert(kv._1) → kv._2.map(_.value)
    }

    FacadeRequest(Uri(request.uri.toString),
      request.method.name,
      headers + (dashCaseToCamelCase.convert("X-Forwarded-For") → Seq(remoteAddress)),
      if (request.entity.nonEmpty){
        StringDeserializer.dynamicBody(Some(request.entity.asString)).content
      }
      else {
        Null
      }
    )
  }

  def apply(request: DynamicRequest): FacadeRequest = {
    FacadeRequest(
      request.uri,
      request.method,
      request.headers.filterNot(_ == Header.METHOD),
      request.body.content
    )
  }

  def apply(frame: Frame): FacadeRequest = {
    implicit val uriSpecificDeserializer = new UriSpecificDeserializer
    frame.payload.utf8String.parseJson[FacadeRequest]
  }
}


case class FacadeResponse(status: Int, headers: Map[String, Seq[String]], body: Value) extends FacadeMessage {
  def toDynamicResponse: Response[DynamicBody] = {
    StandardResponse(
      ResponseHeader(status, headers),
      DynamicBody(body)
    ).asInstanceOf[Response[DynamicBody]]
  }

  def toHttpResponse: HttpResponse = {
    val statusCode = StatusCode.int2StatusCode(status)
    val contentType = contentTypeToSpray(headers.get(Header.CONTENT_TYPE).flatMap(_.headOption))
    val jsonBody = body.toJson
    HttpResponse(statusCode, HttpEntity(contentType, jsonBody), headers.flatMap{ case (name, values) ⇒
      values.map { value ⇒
        RawHeader(FacadeResponse.camelCaseToDashCase.convert(name), value)
      }
    }.toList)
  }

  def toFrame: Frame = {
    TextFrame(this.toJson)
  }

  private def contentTypeToSpray(contentType: Option[String]): spray.http.ContentType = {
    contentType match {
      case None ⇒ `application/json`
      case Some(dynamicContentType) ⇒
        val indexOfSlash = dynamicContentType.indexOf('/')
        val (mainType, subType) = indexOfSlash match {
          case -1 ⇒
            (dynamicContentType, "")
          case index ⇒
            val mainType = dynamicContentType.substring(0, indexOfSlash)
            val subType = dynamicContentType.substring(indexOfSlash + 1)
            (mainType, subType)
        }
        // todo: why we need to register??? replace with header?
        val mediaType = MediaTypes.register(MediaType.custom(mainType, subType, compressible = true, binary = false))
        spray.http.ContentType(mediaType, `UTF-8`)
    }
  }
}

object FacadeResponse {
  val camelCaseToDashCase = new CamelCaseToDashCaseConverter
  def apply(response: Response[DynamicBody]): FacadeResponse = {
    FacadeResponse(response.status, response.headers, response.body.content)
  }
  def apply(frame: Frame): FacadeResponse = {
    frame.payload.utf8String.parseJson[FacadeResponse]
  }
}

class FilterInterruptException(val response: FacadeResponse,
                               message: String,
                               cause: Throwable = null) extends Exception (message, cause)

class FilterRestartException(val request: FacadeRequest,
                               message: String,
                               cause: Throwable = null) extends Exception (message, cause)

class RestartLimitReachedException(num: Int, max: Int) extends Exception (s"Maximum ($max) restart limits exceeded ($num)")