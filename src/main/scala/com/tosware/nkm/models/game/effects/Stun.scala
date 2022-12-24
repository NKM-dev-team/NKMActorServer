package com.tosware.nkm.models.game.effects

import com.tosware.nkm.models.game.CharacterEffect.CharacterEffectId
import com.tosware.nkm.models.game.{CharacterEffect, CharacterEffectMetadata, CharacterEffectName, CharacterEffectType}

object Stun {
  val metadata: CharacterEffectMetadata =
    CharacterEffectMetadata(
      name = CharacterEffectName.Stun,
      initialEffectType = CharacterEffectType.Negative,
      description = "This character cannot take action.",
      isCc = true,
    )
}

case class Stun(effectId: CharacterEffectId, initialCooldown: Int) extends CharacterEffect(effectId) {
  val metadata: CharacterEffectMetadata = Stun.metadata
}
