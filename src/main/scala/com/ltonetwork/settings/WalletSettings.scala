package com.ltonetwork.settings

import com.ltonetwork.state.ByteStr

import java.io.File

case class WalletSettings(
    file: Option[File],
    password: String,
    seed: Option[ByteStr],
    seedPhrase: Option[String],
    accountSeed: Option[ByteStr]
) {
  require(List(seed, seedPhrase, accountSeed).count(_.nonEmpty) <= 1, "Can only have one: seed, seedPhrase, accountSeed")
  private lazy val seedFromPhrase: Option[ByteStr] = seedPhrase.map(s => ByteStr(s.getBytes()))
  def miningWalletSeed: Option[ByteStr]            = seed.orElse(seedFromPhrase)
}
object WalletSettings {
  val zeros = Array(0: Byte, 0: Byte, 0: Byte, 0: Byte)

}
