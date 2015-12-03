package reactive

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._

object AuctionApp extends App {
  val time = FiniteDuration(System.currentTimeMillis(), MILLISECONDS)

  val config = ConfigFactory.load()
//  val appConfig = ConfigFactory.load(config.getConfig("auctionPublisher")).withFallback(config)
//  val system = ActorSystem("Reactive2", appConfig)

  val serversystem = ActorSystem("ReactiveServer", config.getConfig("serverapp").withFallback(config))
  val system = ActorSystem("Reactive5", config.getConfig("clientapp").withFallback(config))
//  val system = ActorSystem("Reactive2")

  val auctionList = List("czadowy_komputer") //, "krzeslo_mistrzow", "krzywy_stol", "zamkniete_drzwi")

  val auctionSearch = system.actorOf(Props[AuctionSearch], AuctionSearch.Name)
  val seller1 = system.actorOf(Seller.props(auctionList), "seller1")
  val notifier = system.actorOf(Props[Notifier], Notifier.Name)
  val publisher = serversystem.actorOf(Props[AuctionPublisher], AuctionPublisher.Name)

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
