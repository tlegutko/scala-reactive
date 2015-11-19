package reactive

import akka.actor._
import akka.testkit._
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest._

class SellerSpec extends TestKit(ActorSystem("Reactive2")) with WordSpecLike with BeforeAndAfterAll with ImplicitSender {
  override def afterAll(): Unit = {
    system.shutdown()
  }
  "A Seller" must {
    "create auction from list of titles" in {
      val seller = system.actorOf(Seller.props(List("first")), "seller")
      Await.result(system.actorSelection("akka://Reactive2/user/seller/first").resolveOne(1 second), 1 second)
    }
  }
}
