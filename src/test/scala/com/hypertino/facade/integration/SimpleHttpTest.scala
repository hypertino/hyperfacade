package com.hypertino.facade.integration

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicRequestObservableMeta, EmptyBody, Method, NoContent, Ok}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.execution.Ack.Continue

import scala.io.Source
import scala.util.Success

class SimpleHttpTest extends IntegrationTestBase("inproc-test.conf") {

  "Integration. HTTP" - {
    "get resource" in {
      register {
        hyperbus.commands[DynamicRequest](
          DynamicRequest.requestMeta,
          DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
        ).subscribe { implicit request =>
          request.reply(Success {
            Ok(DynamicBody(Obj.from("integerField" → 100500, "textField" → "Yey")))
          })
          Continue
        }
      }

      Source.fromURL("http://localhost:54321/simple-resource", "UTF-8").mkString shouldBe """{"integerField":100500,"textField":"Yey"}"""
    }

    "get resource with pattern" in {
      register {
        hyperbus.commands[DynamicRequest](
          DynamicRequest.requestMeta,
          DynamicRequestObservableMeta(RequestMatcher("hb://test-service/{id}", Method.GET, None))
        ).subscribe { implicit command =>
          command.reply(Success {
            Ok(DynamicBody(Obj.from("integerField" → command.request.headers.hrl.query.id.toLong, "textField" → "Yey")))
          })
          Continue
        }
      }

      Source.fromURL("http://localhost:54321/simple-resource/100500", "UTF-8").mkString shouldBe """{"integerField":100500,"textField":"Yey"}"""
    }
  }
}