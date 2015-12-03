package reactive

import akka.actor.{ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration._

object AuctionApp extends App {
  val time = FiniteDuration(System.currentTimeMillis(), MILLISECONDS)

  val system = ActorSystem("Reactive2")

  val auctionList = List("czadowy_komputer") //, "krzeslo_mistrzow", "krzywy_stol", "zamkniete_drzwi")

  val auctionSearch = system.actorOf(Props[AuctionSearch], AuctionSearch.Name)
  val seller1 = system.actorOf(Seller.props(auctionList), "seller1")


  import system.dispatcher

  val rand = scala.util.Random
  system.scheduler.scheduleOnce(3 second) {
    val buyer1 = system.actorOf(Props(classOf[Buyer], 2000), "buyer1")
    buyer1 ! Buyer.FindAndBid("komputer", rand.nextInt(2000))
    val timeDiff = FiniteDuration(System.currentTimeMillis(), MILLISECONDS) - time
    println(s"${timeDiff}")
  }
  ////    buyer1 ! AuctionMessage.FindAndBid("krzeslo", BigDecimal(400))
  ////    buyer1 ! AuctionMessage.FindAndBid("stol", BigDecimal(400))
  Await.result(system.whenTerminated, Duration.Inf)
}
