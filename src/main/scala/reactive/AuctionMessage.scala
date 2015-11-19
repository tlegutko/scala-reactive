package reactive

object AuctionMessage {

  final case class StartAuction(startingPrice: BigDecimal) {
    require(startingPrice > 0)
  }

  final case class Bid(amount: BigDecimal) {
    require(amount > 0)
  }

  final case class FindAndBid(name: String, amount: BigDecimal) {
    require(amount > 0)
  }

  case object Relist

  case object ItemSold

}
