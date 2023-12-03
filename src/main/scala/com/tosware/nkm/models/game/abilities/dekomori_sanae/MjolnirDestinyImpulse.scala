package com.tosware.nkm.models.game.abilities.dekomori_sanae

import com.tosware.nkm.*
import com.tosware.nkm.models.game.ability.*
import com.tosware.nkm.models.game.event.{GameEvent, GameEventListener}
import com.tosware.nkm.models.game.hex.HexCoordinates
import com.tosware.nkm.models.game.*

import scala.util.Random

object MjolnirDestinyImpulse extends NkmConf.AutoExtract {
  val metadata: AbilityMetadata =
    AbilityMetadata(
      name = "Mjolnir Destiny Impulse",
      abilityType = AbilityType.Ultimate,
      description =
        """Hit with a hammer, dealing {damage} physical damage to hit enemies.
          |This ability can be used again this turn every time it caused to kill at least one enemy.
          |
          |Range: circular, {range}
          |Radius: circular, {radius}""".stripMargin,
    )
}

case class MjolnirDestinyImpulse(abilityId: AbilityId, parentCharacterId: CharacterId)
    extends Ability(abilityId)
    with UsableOnCoordinates
    with GameEventListener {
  override val metadata: AbilityMetadata = MjolnirDestinyImpulse.metadata

  override def rangeCellCoords(implicit gameState: GameState): Set[HexCoordinates] =
    parentCell.get.coordinates.getCircle(metadata.variables("range")).whereExists

  override def targetsInRange(implicit gameState: GameState): Set[HexCoordinates] =
    rangeCellCoords

  override def use(target: HexCoordinates, useData: UseData)(implicit
      random: Random,
      gameState: GameState,
  ): GameState = {
    val targets = target.getCircle(metadata.variables("radius")).whereSeenEnemiesOfC(parentCharacterId).characters.map(_.id)
    val damage = Damage(DamageType.Physical, metadata.variables("damage"))
    val ngs = targets.foldLeft(gameState)((acc, cid) => hitAndDamageCharacter(cid, damage)(random, acc))

    val refreshAbility =
      ngs
        .newGameEventsSince(gameState)
        .ofType[GameEvent.CharacterDied]
        .nonEmpty

    ngs.setAbilityEnabled(id, newEnabled = refreshAbility)
  }

  override def useChecks(implicit target: HexCoordinates, useData: UseData, gameState: GameState): Set[UseCheck] =
    if (state.isEnabled)
      super.useChecks - UseCheck.Base.IsNotOnCooldown - UseCheck.Base.CanBeUsedByParent
    else
      super.useChecks

  override def onEvent(e: GameEvent.GameEvent)(implicit random: Random, gameState: GameState): GameState = e match {
    case GameEvent.TurnFinished(_, _, _, _, _) =>
      val characterIdThatTookAction = gameState.gameLog.characterThatTookActionInTurn(e.turn.number).get
      if (characterIdThatTookAction != parentCharacterId) return gameState
      gameState.setAbilityEnabled(id, newEnabled = false)
    case _ => gameState
  }
}
