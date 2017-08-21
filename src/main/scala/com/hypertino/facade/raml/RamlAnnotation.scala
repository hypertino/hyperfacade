package com.hypertino.facade.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Text, Value}
import com.hypertino.facade.filter.parser.PreparedExpression
import org.raml.v2.api.model.v10.datamodel.{TypeInstance, TypeInstanceProperty}

trait RamlAnnotation {
  def name: String
  def predicate: Option[PreparedExpression]
}

// todo: make this injectable !!

object RamlAnnotation {
  val SET = "set"
  val REWRITE = "rewrite"
  val DENY = "deny"
  val REMOVE = "remove"
  val AUTHORIZE = "authorize"
  val FETCH = "fetch"

  import scala.collection.JavaConverters._

  private def recursiveProperty(instance: TypeInstance): Value = {
    if (instance.isScalar) {
      Text(instance.value().toString)
    } else {

      Obj(
        instance.properties().asScala.map { kv ⇒
          kv.name() → recursiveProperty(kv)
        }.toMap
      )
    }
  }

  private def recursiveProperty(instance: TypeInstanceProperty): Value = {
    if (instance.isArray) {
      Lst(
        instance.values().asScala.map(recursiveProperty)
      )
    } else {
      recursiveProperty(instance.value())
    }
  }

  def apply(name: String, properties: Seq[TypeInstanceProperty]): RamlAnnotation = {
    val propMap = properties.map(property ⇒ property.name() → recursiveProperty(property.value())).toMap
    def predicate = propMap.get("if").map(_.toString)
    def preparedExpression = predicate.map(PreparedExpression.apply)
    def locationExpression = PreparedExpression(propMap("location").toString)
    def queryExpressionMap = propMap.getOrElse("query", Null).toMap.map(kv ⇒ kv._1 → PreparedExpression(kv._2.toString)).toMap

    name match {
      case DENY ⇒
        DenyAnnotation(predicate = preparedExpression)
      case REMOVE ⇒
        RemoveAnnotation(predicate = preparedExpression)
      case SET ⇒
        SetAnnotation(predicate = preparedExpression, source = PreparedExpression(propMap("source").toString))
      case REWRITE ⇒
        RewriteAnnotation(predicate = preparedExpression, location = propMap("location").toString,
          query = propMap.getOrElse("query", Null)
        )
      case FETCH ⇒
        FetchAnnotation(predicate = preparedExpression,
            location = locationExpression,
            query = queryExpressionMap,
            mode = propMap.get("mode").map(_.toString).getOrElse("document"),
            onError = propMap.get("on_error").map(_.toString).getOrElse("fail"),
            defaultValue = propMap.get("default").map(o ⇒ PreparedExpression(o.toString))
          )
      case annotationName ⇒
        RegularAnnotation(annotationName, preparedExpression, (propMap - "if").map(kv ⇒ kv._1 → kv._2.toString))
    }
  }
}

case class RewriteAnnotation(name: String = RamlAnnotation.REWRITE,
                             predicate: Option[PreparedExpression],
                             location: String,
                             query: Value) extends RamlAnnotation

// todo: add and implement stages for all field annotations!

case class DenyAnnotation(name: String = RamlAnnotation.DENY,
                         predicate: Option[PreparedExpression]) extends RamlAnnotation

case class RemoveAnnotation(name: String = RamlAnnotation.REMOVE,
                            predicate: Option[PreparedExpression]) extends RamlAnnotation

case class SetAnnotation(name: String = RamlAnnotation.SET,
                         predicate: Option[PreparedExpression],
                         source: PreparedExpression
                        ) extends RamlAnnotation


case class FetchAnnotation(name: String = RamlAnnotation.FETCH,
                           predicate: Option[PreparedExpression],
                           location: PreparedExpression,
                           query: Map[String, PreparedExpression],
                           mode: String, //todo: this shouldbe enum
                           onError: String, //todo: this shouldbe enum
                           defaultValue: Option[PreparedExpression]
                          ) extends RamlAnnotation

case class AuthorizeAnnotation(name: String = RamlAnnotation.AUTHORIZE,
                               predicate: Option[PreparedExpression]) extends RamlAnnotation

case class RegularAnnotation(name: String, predicate: Option[PreparedExpression],
                             properties: Map[String, String] = Map.empty) extends RamlAnnotation