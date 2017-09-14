package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Null, Obj}
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext, PreparedExpression}
import com.hypertino.facade.model._
import com.hypertino.facade.utils.{HrlTransformer, RequestUtils}
import com.hypertino.hyperbus.model.{DynamicRequest, HRL, Headers}

import scala.concurrent.{ExecutionContext, Future}

class ForwardRequestFilter(sourceHRL: HRL,
                           locationExpression: PreparedExpression,
                           queryExpressionMap: Map[String, PreparedExpression],
                           method: Option[PreparedExpression],
                           protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {

  override def apply(contextWithRequest: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    Future {
      val request = contextWithRequest.request
      val ctx = ExpressionEvaluatorContext(contextWithRequest, Obj.empty)
      val location = expressionEvaluator.evaluate(ctx, locationExpression).toString
      val query = queryExpressionMap.map { kv ⇒
        kv._1 → expressionEvaluator.evaluate(ctx, kv._2)
      }
      val destinationHRL = HRL(location, query)
      val destinationMethod = method.map(expressionEvaluator.evaluate(ctx, _).toString)

      // todo: should we preserve all query fields???
      val rewrittenUri = HrlTransformer.rewriteForwardWithPatterns(request.headers.hrl, sourceHRL, destinationHRL)
      contextWithRequest.copy(
        request = RequestUtils.copyWith(request, rewrittenUri, destinationMethod)
      )
    }
  }
}


