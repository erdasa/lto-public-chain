package com.ltonetwork.api.http.requests

import cats.implicits._
import com.ltonetwork.account.{PrivateKeyAccount, PublicKeyAccount}
import com.ltonetwork.state.ByteStr
import com.ltonetwork.transaction.ValidationError.GenericError
import com.ltonetwork.transaction.anchor.AnchorTransaction
import com.ltonetwork.transaction.{Proofs, ValidationError}
import com.ltonetwork.utils.Time
import com.ltonetwork.wallet.Wallet
import play.api.libs.json.{Format, JsNumber, JsObject, Json, OWrites}

case class AnchorRequest(version: Option[Byte] = None,
                         timestamp: Option[Long] = None,
                         senderKeyType: Option[String] = None,
                         senderPublicKey: Option[String] = None,
                         fee: Long,
                         anchors: List[String],
                         sponsorKeyType: Option[String] = None,
                         sponsorPublicKey: Option[String] = None,
                         signature: Option[ByteStr] = None,
                         proofs: Option[Proofs] = None,
) extends TxRequest.For[AnchorTransaction] {

  protected def sign(tx: AnchorTransaction, signer: PrivateKeyAccount): AnchorTransaction = tx.signWith(signer)

  def toTxFrom(sender: PublicKeyAccount, sponsor: Option[PublicKeyAccount]): Either[ValidationError, AnchorTransaction] =
    for {
      validAnchors <- anchors.traverse(s => parseBase58(s, "invalid anchor", AnchorTransaction.MaxAnchorStringSize))
      validProofs  <- toProofs(signature, proofs)
      tx <- AnchorTransaction.create(
        version.getOrElse(AnchorTransaction.latestVersion),
        None,
        timestamp.getOrElse(defaultTimestamp),
        sender,
        fee,
        validAnchors,
        sponsor,
        validProofs
      )
    } yield tx
}

object AnchorRequest {
  implicit val jsonFormat: Format[AnchorRequest] = Format(
    Json.reads[AnchorRequest],
    Json.writes[AnchorRequest].transform((json: JsObject) => Json.obj("type" -> AnchorTransaction.typeId.toInt) ++ json)
  )
}
