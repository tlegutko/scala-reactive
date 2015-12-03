package reactive

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import reactive.Buyer.FindAndBid

import scala.concurrent.duration._


class BuyerSpec extends TestKit(ActorSystem("Reactive2")) with WordSpecLike with BeforeAndAfterAll with ImplicitSender {
  override def afterAll(): Unit = {
    system.terminate()
  }

  override def beforeAll(): Unit = {
    val auctionSearch = system.actorOf(Props[AuctionSearch], AuctionSearch.Name)
  }

  "A Buyer" must {
    "rebid when his financial situation allows it" in {
      val buyer = system.actorOf(Props(classOf[Buyer], BigDecimal(500)))
      val auction = system.actorOf(Props(classOf[Auction], self, BigDecimal(200)), "awesome_auction")
      import system.dispatcher
      system.scheduler.scheduleOnce(200 milliseconds) {
        buyer ! FindAndBid("auction", 200)
        auction ! Bid(250)

        expectMsg(BidAccepted(250))
        expectMsg(OutBid(251))
      }
    }
    "not rebid, when his financial situation disallows it" in {
      val buyer = system.actorOf(Props(classOf[Buyer], BigDecimal(300)))
      val auction = system.actorOf(Props(classOf[Auction], self, BigDecimal(200)), "awesome_auction2")
      import system.dispatcher
      system.scheduler.scheduleOnce(200 milliseconds) {
        buyer ! FindAndBid("auction2", 200)
        auction ! Bid(250)
        expectMsg(BidAccepted(250))
        expectNoMsg() // so this test actor is not outbid
      }
    }
  }

}
