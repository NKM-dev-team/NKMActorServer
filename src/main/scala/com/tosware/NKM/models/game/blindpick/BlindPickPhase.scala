package com.tosware.NKM.models.game.blindpick

import enumeratum._

sealed trait BlindPickPhase extends EnumEntry

object BlindPickPhase extends Enum[BlindPickPhase] {
  val values = findValues

  case object Picking extends BlindPickPhase
  case object Finished extends BlindPickPhase
}





