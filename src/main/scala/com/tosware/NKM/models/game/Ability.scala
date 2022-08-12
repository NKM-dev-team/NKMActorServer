package com.tosware.NKM.models.game

import com.tosware.NKM.models.CommandResponse.{CommandResponse, Failure, Success}
import com.tosware.NKM.models.game.Ability._
import com.tosware.NKM.models.game.NKMCharacter.CharacterId
import com.tosware.NKM.models.game.hex.HexUtils._
import com.tosware.NKM.models.game.hex.{HexCell, HexCoordinates}
import enumeratum._

object Ability {
  type AbilityId = String
  type AbilityMetadataId = String
  type UseCheck = (Boolean, String)
}

sealed trait AbilityType extends EnumEntry
object AbilityType extends Enum[AbilityType] {
  val values = findValues

  case object Passive extends AbilityType
  case object Normal extends AbilityType
  case object Ultimate extends AbilityType
}

case class AbilityMetadata
(
  name: String,
  abilityType: AbilityType,
  description: String,
  cooldown: Int = 0,
  range: Int = 0,
  alternateName: String = "",
) {
  val id: AbilityMetadataId = name
}

case class AbilityState
(
  parentCharacterId: CharacterId,
  cooldown: Int = 0,
)


case class UseData(data: String = "")

trait BasicAttackOverride {
  def basicAttackCells(implicit gameState: GameState): Set[HexCoordinates]
  def basicAttackTargets(implicit gameState: GameState): Set[HexCoordinates]
  def basicAttack(targetCharacterId: CharacterId)(implicit gameState: GameState): GameState
}

trait Usable[T] { this: Ability =>
  def use(target: T, useData: UseData = UseData())(implicit gameState: GameState): GameState
  def useChecks(implicit target: T, useData: UseData, gameState: GameState): Set[UseCheck] =
    Set(
      UseCheck.NotOnCooldown,
      UseCheck.ParentCharacterOnMap,
    )

  final def canBeUsed(implicit target: T, useData: UseData, gameState: GameState): CommandResponse = {
    val failures = useChecks.filter(_._1 == false)
    if(failures.isEmpty) Success()
    else Failure(failures.map(_._2).mkString("\n"))
  }
}

trait UsableOnCoordinates extends Usable[HexCoordinates] { this: Ability =>
  override def useChecks(implicit target: HexCoordinates, useData: UseData, gameState: GameState): Set[UseCheck] = {
    super.useChecks ++
    Set(
      UseCheck.TargetCoordsInRange,
    )
  }
}

trait UsableOnCharacter extends Usable[CharacterId] { this: Ability =>
  override def useChecks(implicit target: CharacterId, useData: UseData, gameState: GameState): Set[UseCheck] = {
    super.useChecks ++
    Set(
      UseCheck.TargetCharacterInRange,
    )
  }
}

trait Ability {
  val id: AbilityId = java.util.UUID.randomUUID.toString
  val metadata: AbilityMetadata
  val state: AbilityState
  def rangeCellCoords(implicit gameState: GameState): Set[HexCoordinates]
  def targetsInRange(implicit gameState: GameState): Set[HexCoordinates] = Set.empty
  def parentCharacter(implicit gameState: GameState): NKMCharacter =
    gameState.characters.find(_.state.abilities.map(_.id).contains(id)).get
  def parentCell(implicit gameState: GameState): Option[HexCell] =
    parentCharacter.parentCell

  object UseCheck {
    def NotOnCooldown: UseCheck =
      (state.cooldown <= 0) -> "Ability is on cooldown."
    def ParentCharacterOnMap(implicit gameState: GameState): UseCheck =
      parentCharacter.isOnMap -> "Parent character is not on map."
    def TargetCharacterInRange(implicit target: CharacterId, useData: UseData, gameState: GameState): UseCheck =
      targetsInRange.toCells.exists(_.characterId.contains(target)) -> "Target character is not in range."
    def TargetIsEnemy(implicit target: CharacterId, useData: UseData, gameState: GameState): UseCheck =
      gameState.characterById(target).get.isEnemyFor(parentCharacter.id) -> "Target character is not an enemy."
    def TargetIsFriend(implicit target: CharacterId, useData: UseData, gameState: GameState): UseCheck =
      gameState.characterById(target).get.isFriendFor(parentCharacter.id) -> "Target character is not a friend."
    def TargetCoordsInRange(implicit target: HexCoordinates, useData: UseData, gameState: GameState): UseCheck =
      Seq(target).toCells.nonEmpty -> "Target character is not in range."
    def TargetIsFriendlySpawn(implicit target: HexCoordinates, useData: UseData, gameState: GameState): UseCheck =
      gameState.hexMap.get.getSpawnPointsFor(parentCharacter.owner.id).toCoords.contains(target) -> "Target is not a friendly spawn."
    def TargetIsFreeToStand(implicit target: HexCoordinates, useData: UseData, gameState: GameState): UseCheck = {
      Seq(target).toCells.headOption.fold(false)(_.isFreeToStand) -> "Target is not free to stand."
    }
  }
}