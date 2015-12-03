package reactive

import akka.actor.Actor

object AuctionPublisher {
  val Name = "auctionPublisher"
}

class AuctionPublisher extends Actor {
  override def receive: Receive = {
    case AuctionBidAccepted(amount, name, path) => println(s"AUCTION_PUBLISHER: AuctionBid ${amount}, ${name}, ${path}")
    case AuctionFinished(amount, name, path) => println(s"AUCTION_PUBLISHER: AuctionFinished ${amount}, ${name}, ${path}")
    case _ => println("dupa")
  }
}
