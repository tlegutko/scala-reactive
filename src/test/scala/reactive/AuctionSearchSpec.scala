package reactive

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class AuctionSearchSpec extends TestKit(ActorSystem("Reactive2")) with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  override def afterAll(): Unit = {
    system.terminate()
  }

  "AuctionSearch" must {
    "allow for registration" in {
      val auctionSearch = system.actorOf(Props[AuctionSearch])
      auctionSearch ! AuctionSearch.GetAuctions("whatever")
      expectMsg(AuctionSearch.AuctionList(List.empty))

      auctionSearch ! AuctionSearch.Register
      auctionSearch ! AuctionSearch.GetAuctions(self.path.name)
      expectMsg(AuctionSearch.AuctionList(List(self.path)))

      val secondActor = TestProbe()
      secondActor.send(auctionSearch, AuctionSearch.Register)
      auctionSearch ! AuctionSearch.GetAuctions("test")
      expectMsg(AuctionSearch.AuctionList(List(secondActor.ref.path, self.path)))
    }

    "filter registered auctions by name" in {
      val auctionSearch = system.actorOf(Props[AuctionSearch])
      auctionSearch ! AuctionSearch.Register
      auctionSearch ! AuctionSearch.GetAuctions(self.path.name)
      expectMsg(AuctionSearch.AuctionList(List(self.path)))
      auctionSearch ! AuctionSearch.GetAuctions("not_my_name")
      expectMsg(AuctionSearch.AuctionList(List.empty))
    }
  }

}
