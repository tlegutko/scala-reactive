package reactive

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.LoggingReceive
import reactive.AuctionMessage.{ItemSold, Bid}

object Auction {
  def props(price: BigDecimal): Props = Props(new Auction(price))

}

class Auction(var startingPrice: BigDecimal) extends Actor {

  var bidCounter = 0

  def sold(finalPrice: BigDecimal): Receive = LoggingReceive {
    case _ => context stop self
  }

  def ignored: Receive = LoggingReceive {
    ???
  }

  override def receive = created

  def created: Receive = LoggingReceive {
    case Bid(amount) if amount > startingPrice => {
      bidCounter += 1
      context become activated(amount)
    }
    case Bid(amount) => {
      Console println "bid too low"
    }
    case _ => Console println "unsupported message"
  }

  def activated(currentPrice: BigDecimal): Receive = LoggingReceive {
    case Bid(amount) if amount > currentPrice => {
      bidCounter += 1
      if (bidCounter < 2) {
        println(s"${self.path.name} bid $amount accepted")
        context become activated(amount)
      }
      else {
        println(s"${self.path.name} started at $startingPrice and was sold at $amount!")
        sender ! ItemSold
        context become sold(amount)
      }
    }
    case Bid(amount) =>
      println(s"${self.path.name} bid $amount too low, current price is $currentPrice")
  }

}

object AuctionApp extends App {
  val system = ActorSystem("Reactive2")

  val auction1 = system.actorOf(Auction.props(40), "auction1")
  val auction2 = system.actorOf(Auction.props(10), "auction2")
  val auction3 = system.actorOf(Auction.props(1), "auction3")
  val auction4 = system.actorOf(Auction.props(100), "auction4")

  val auctionList = List(auction1, auction2, auction3, auction4)
  //  auction1 ! Bid(20)
  //  auction1 ! Bid(303)
  //  auction1 ! Bid(302)
  //  auction1 ! Bid(305)
  //  auction1 ! Bid(306)
  //  auction1 ! Bid(306)

  val buyer1 = system.actorOf(Buyer.props(auctionList), "buyer1")
  val buyer2 = system.actorOf(Buyer.props(auctionList), "buyer2")
  val buyer3 = system.actorOf(Buyer.props(auctionList), "buyer")

  val buyersList = List(buyer1, buyer2, buyer3)

  val rand = scala.util.Random

  for (i <- 1 to 5) {
    for (buyer <- buyersList) buyer ! Bid(rand.nextInt(i * 200))
  }

  system.awaitTermination()
}