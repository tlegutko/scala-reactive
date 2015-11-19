package reactive

import akka.actor.{ActorSystem, Props}
import scala.concurrent.duration._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class AuctionSpec extends TestKit(ActorSystem("Reactive2")) with WordSpecLike with BeforeAndAfterAll with ImplicitSender {
  override def afterAll(): Unit = {
    system.shutdown()
  }

  "An Auction" must {
    "register in auctionSearch on creation" in {
      val auctionSearch = system.actorOf(Props[AuctionSearch], AuctionSearch.Name)
      val auction = system.actorOf(Props(classOf[Auction], self, BigDecimal(20)), "awesome_auction")
      import system.dispatcher
      system.scheduler.scheduleOnce(200 milliseconds) {
        auctionSearch ! AuctionSearch.GetAuctions("auction")
      }
      expectMsg(AuctionSearch.AuctionList(List(auction)))
    }

    "send notifications when sold" in {
      val seller = TestProbe()
      val auction2 = system.actorOf(Props(classOf[Auction], seller.testActor, BigDecimal(20)), "auction2")

      import system.dispatcher
      system.scheduler.scheduleOnce(200 milliseconds) {
        auction2 ! AuctionMessage.Bid(30)

      }
      expectMsg(AuctionMessage.BidAccepted(30))
      seller.expectMsg(4 seconds, AuctionMessage.ItemSold)
      expectMsg(AuctionMessage.ItemSold)
    }

    "notify buyer when his offer is outbid" in {
      val seller = TestProbe()
      val auction3 = system.actorOf(Props(classOf[Auction], seller.testActor, BigDecimal(20)), "auction3")

      import system.dispatcher
      system.scheduler.scheduleOnce(200 milliseconds) {
        auction3 ! AuctionMessage.Bid(30)
        auction3 ! AuctionMessage.Bid(35)
        expectMsg(AuctionMessage.OutBid(35))
      }
    }
  }
}
