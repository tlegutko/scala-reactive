package reactive

sealed trait AuctionMessage

object AuctionMessage {

  final case class StartAuction(startingPrice: BigDecimal) extends AuctionMessage {
    require(startingPrice > 0)
  }

  final case class Bid(amount: BigDecimal) extends AuctionMessage {
    require(amount > 0)
  }

  final case class FindAndBid(name: String, amount: BigDecimal) extends AuctionMessage {
    require(amount > 0)
  }

  final case class OutBid(amount: BigDecimal) extends AuctionMessage {
    require(amount > 0)
  }

  final case class BidAccepted(amount: BigDecimal) extends AuctionMessage {
    require(amount > 0)
  }

  case object Relist

  case object ItemSold

}