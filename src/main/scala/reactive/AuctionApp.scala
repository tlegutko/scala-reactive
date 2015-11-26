package reactive

import akka.actor.{Props, ActorSystem}

import scala.concurrent.Await
import scala.concurrent.duration._

object AuctionApp extends App {
  val system = ActorSystem("Reactive2")

  val auctionList = List("czadowy_komputer")//, "krzeslo_mistrzow", "krzywy_stol", "zamkniete_drzwi")

  val auctionSearch = system.actorOf(Props[AuctionSearch], AuctionSearch.Name)
  val seller1 = system.actorOf(Seller.props(auctionList), "seller1")


    import system.dispatcher

    system.scheduler.scheduleOnce(1 second) {
      val buyer1 = system.actorOf(Props(classOf[Buyer], BigDecimal(2000)), "buyer1")
      buyer1 ! AuctionMessage.FindAndBid("komputer", BigDecimal(500))
    }
  ////    buyer1 ! AuctionMessage.FindAndBid("krzeslo", BigDecimal(400))
  ////    buyer1 ! AuctionMessage.FindAndBid("stol", BigDecimal(400))

  Await.result(system.whenTerminated, Duration.Inf)
}
