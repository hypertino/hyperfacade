package eu.inn.facade.model

import eu.inn.binders.value.Value
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

case class ResponseFilterContext(
                                 uri: Uri,
                                 method: String,
                                 requestHeaders: Map[String, Seq[String]],
                                 requestBody: Value,
                                 responseHeaders: Map[String, Seq[String]],
                                 responseBody: Value
                               )

trait ResponseFilter extends Filter {
  def apply(context: ResponseFilterContext, response: FacadeResponse)
           (implicit ec: ExecutionContext): Future[FacadeResponse]
}
