package eu.inn.facade.model

import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.http.{HttpRequest, HttpResponse}

trait FacadeMessage {
  def headers: Map[String, Seq[String]]
  def body: Value
}

case class FacadeRequest(uri: Uri, method: String, headers: Map[String, Seq[String]], body: Value) extends FacadeMessage {
  def toDynamicRequest: DynamicRequest = ???

  def correlationId: String = {
    val messageId = headers(Header.MESSAGE_ID)
    headers.getOrElse(Header.CORRELATION_ID, messageId).head
  }

  def contentType: Option[String] = {
    headers.get(Header.CONTENT_TYPE).flatMap(_.headOption)
  }
}

object FacadeRequest {
  def apply(uri: Uri, request: HttpRequest): FacadeRequest = ???
  def apply(request: DynamicRequest): FacadeRequest = ???
}


case class FacadeResponse(status: Int, headers: Map[String, Seq[String]], body: Value) extends FacadeMessage {
  def toDynamicResponse: Response[DynamicBody] = ???
  def toHttpResponse: HttpResponse = ???
}

object FacadeResponse {
  def apply(response: Response[DynamicBody]): FacadeResponse = ???
}

class FilterInterruptException(val response: FacadeResponse,
                               message: String,
                               cause: Throwable = null) extends Exception (message, cause)
