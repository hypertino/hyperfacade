/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.{DefaultValueSerializerFactory, Null, Value}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.raml.{RamlConfiguration, RamlFieldAnnotation}
import com.hypertino.facade.utils.{SelectField, SelectFields}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.HRL
import com.hypertino.inflector.naming.{CamelCaseToSnakeCaseConverter, PlainConverter, SnakeCaseToCamelCaseConverter}
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class FetchFieldAnnotation(
                                 @fieldName("if") predicate: Option[PreparedExpression],
                                 location: PreparedExpression,
                                 query: Map[String, PreparedExpression],
                                 expects: String = FetchFilter.EXPECTS_DOCUMENT,   //todo: this should be enum
                                 onError: String = FetchFilter.ON_ERROR_FAIL,      //todo: this should be enum
                                 defaultStatuses: Set[Int] = Set(404),
                                 default: Option[PreparedExpression] = None,
                                 stages: Set[FieldFilterStage] = Set(FieldFilterStageResponse, FieldFilterStageEvent),
                                 selector: Option[PreparedExpression] = None,
                                 always: Boolean = false
                          ) extends RamlFieldAnnotation with FetchAnnotationBase {
  def name: String = "fetch"
}


class FetchFieldFilter(protected val annotation: FetchFieldAnnotation,
                       protected val hyperbus: Hyperbus,
                       protected val expressionEvaluator: ExpressionEvaluator,
                       protected implicit val injector: Injector,
                       protected implicit val scheduler: Scheduler) extends FieldFilter with FetchFilterBase with Injectable with StrictLogging{

  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(context: FieldFilterContext): Task[Option[Value]] = {
    if (annotation.always) {
      fetchAndReturnField(context)
    } else {
      context.requestContext.request.headers.hrl.query.dynamic.fields match {
        case Null ⇒
          Task.now(None)

        case fields: Value ⇒
          if (fieldsSelected(fields, context)) {
            fetchAndReturnField(context)
          }
          else {
            Task.now(None)
          }
      }
    }
  }

  protected def fieldsSelected(fields: Value, context: FieldFilterContext) = Try(SelectFields(fields.toString)) match {
    case Success(selectFields) ⇒
      recursiveMatch(context.fieldPath, selectFields)

    case Failure(e) ⇒
      logger.error(s"Can't parse 'fields' parameter", e)
      false
  }

  @tailrec private def recursiveMatch(fieldPath: Seq[String], selectFields: Map[String, SelectField]): Boolean = {
    if (fieldPath.nonEmpty) {
      selectFields.get(fieldPath.head) match {
        case Some(f) ⇒ recursiveMatch(fieldPath.tail, f.children)
        case None ⇒ false
      }
    }
    else {
      true
    }
  }

  protected def fetchAndReturnField(context: FieldFilterContext): Task[Option[Value]] = {
    try {
      val location = expressionEvaluator.evaluate(context.expressionEvaluatorContext, annotation.location).toString
      val query = annotation.query.map { kv ⇒
        kv._1 → expressionEvaluator.evaluate(context.expressionEvaluatorContext, kv._2)
      }
      val hrl = HRL(location, query)
      //val hrl = ramlConfiguration.resourceHRL(HRL.fromURL(location), Method.GET).getOrElse(hrlOriginal)

      implicit val mcx = context.requestContext
      ask(hrl, context.expressionEvaluatorContext).
        onErrorRecoverWith {
          case NonFatal(e) ⇒
            handleError(context.fieldPath.mkString("."), context.expressionEvaluatorContext, e)
        }
    } catch {
      case NonFatal(e) ⇒
        handleError(context.fieldPath.mkString("."), context.expressionEvaluatorContext, e)
    }
  }
}

class FetchFieldFilterFactory(hyperbus: Hyperbus,
                              protected val predicateEvaluator: ExpressionEvaluator,
                              protected implicit val injector: Injector,
                              protected implicit val scheduler: Scheduler) extends RamlFieldFilterFactory {
  def createFieldFilter(fieldName: String, typeName: String, annotation: RamlFieldAnnotation): FieldFilter = {
    new FetchFieldFilter(annotation.asInstanceOf[FetchFieldAnnotation], hyperbus, predicateEvaluator, injector, scheduler)
  }

  override def createRamlAnnotation(name: String, value: Value): RamlFieldAnnotation = {
    value.to[FetchFieldAnnotation]
  }
}