package reactive

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import reactive.AuctionMessage.ItemSold
import reactive.Seller.Initialize

object Seller {
  def props(auctionTitles: List[String]): Props = Props(new Seller(auctionTitles))

  final case class StartAuction(auction: ActorRef)

  case object Initialize

  val MaxPrice = 300
}

class Seller(auctionTitles: List[String]) extends Actor {
  val rand = scala.util.Random
  self ! Initialize

  def uninitialized = LoggingReceive {
    case Initialize =>
      auctionTitles.map(title =>
        context.actorOf(Props(classOf[Auction], self.path, BigDecimal(rand.nextInt(Seller.MaxPrice))), title))
      context become active
    case _ => // ignore
  }

  def active = LoggingReceive {
    case ItemSold => println(s"${self.path.name} sold ${sender().path.name}!")
    case _ => // ignore
  }

  override def receive = uninitialized
}
