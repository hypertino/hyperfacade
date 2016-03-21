package eu.inn.facade.filter.chain


import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}

trait FilterChain {
  def filterRequest(originalRequest: FacadeRequest, request: FacadeRequest)
                   (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val context = createRequestFilterContext(originalRequest)
    FutureUtils.chain(request, findRequestFilters(context, request).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  def filterResponse(originalRequest: FacadeRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {

    val context = createResponseFilterContext(originalRequest, response)
    FutureUtils.chain(response, findResponseFilters(context, response).map(f ⇒ f.apply(context, _ : FacadeResponse)))
  }

  def filterEvent(originalRequest: FacadeRequest, event: FacadeRequest)
                 (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val context = createEventFilterContext(originalRequest, event)
    FutureUtils.chain(event, findEventFilters(context, event).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  def createRequestFilterContext(request: FacadeRequest) = {
    RequestFilterContext(
      request.uri,
      request.method,
      request.headers,
      request.body
    )
  }

  def createResponseFilterContext(request: FacadeRequest, response: FacadeResponse) = {
    ResponseFilterContext(
      request.uri,
      request.method,
      request.headers,
      request.body,
      response.headers,
      response.body
    )
  }

  def createEventFilterContext(request: FacadeRequest, event: FacadeRequest) = {
    EventFilterContext(
      request.uri,
      request.headers,
      request.body,
      event.method,
      event.headers,
      event.body
    )
  }

  def findRequestFilters(context: RequestFilterContext, request: FacadeRequest): Seq[RequestFilter]
  def findResponseFilters(context: ResponseFilterContext, response: FacadeResponse): Seq[ResponseFilter]
  def findEventFilters(context: EventFilterContext, event: FacadeRequest): Seq[EventFilter]
}

object FilterChain {
  val empty = SimpleFilterChain(Seq.empty,Seq.empty,Seq.empty)
}
