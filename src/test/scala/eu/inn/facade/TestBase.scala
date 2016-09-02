package eu.inn.facade

import eu.inn.facade.workers.WsRestServiceApp
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import scaldi.Injectable

abstract class TestBase extends FreeSpec with Matchers with ScalaFutures with CleanRewriteIndex with Injectable with MockContext with BeforeAndAfterAll {

  def app: WsRestServiceApp
  override def afterAll(): Unit = {
    app.stopService(true)
  }
}