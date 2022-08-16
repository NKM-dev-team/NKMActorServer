package com.tosware.NKM.models.game

import com.softwaremill.quicklens._
import com.tosware.NKM.actors.Game.GameId
import com.tosware.NKM.models.Damage
import com.tosware.NKM.models.game.Ability.AbilityId
import com.tosware.NKM.models.game.CharacterEffect.CharacterEffectId
import com.tosware.NKM.models.game.CharacterMetadata.CharacterMetadataId
import com.tosware.NKM.models.game.GameEvent._
import com.tosware.NKM.models.game.NKMCharacter.CharacterId
import com.tosware.NKM.models.game.Player.PlayerId
import com.tosware.NKM.models.game.blindpick._
import com.tosware.NKM.models.game.draftpick._
import com.tosware.NKM.models.game.hex.{HexCell, HexCoordinates, HexMap, NKMUtils}

import scala.util.Random

object GameEvent {
  abstract class GameEvent()(implicit val phase: Phase, val turn: Turn, val causedById: String)
  trait ContainsCharacterId {
    val characterId: CharacterId
  }
  trait ContainsAbilityId {
    val abilityId: AbilityId
  }

  case class ClockUpdated(newClock: Clock)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
  case class CharacterPlaced(characterId: CharacterId, target: HexCoordinates)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsCharacterId
  case class EffectAppliedOnCell(effectId: String, target: HexCoordinates)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
  case class EffectAppliedOnCharacter(effectId: CharacterEffectId, characterId: CharacterId)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
  case class EffectRemovedFromCharacter(effectId: CharacterEffectId)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
  case class AbilityHitCharacter(abilityId: AbilityId, targetCharacterId: CharacterId)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsAbilityId
  case class AbilityUsedWithoutTarget(abilityId: AbilityId)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsAbilityId
  case class AbilityUsedOnCoordinates(abilityId: AbilityId, target: HexCoordinates)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsAbilityId
  case class AbilityUsedOnCharacter(abilityId: AbilityId, targetCharacterId: CharacterId)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsAbilityId
  case class CharacterBasicMoved(characterId: CharacterId, path: Seq[HexCoordinates])(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsCharacterId
  case class CharacterBasicAttacked(characterId: CharacterId, targetCharacterId: CharacterId)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsCharacterId
  case class CharacterTeleported(characterId: CharacterId, target: HexCoordinates)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsCharacterId
  case class CharacterDamaged(characterId: CharacterId, damage: Damage)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsCharacterId
  case class CharacterHealed(characterId: CharacterId, amount: Int)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsCharacterId
  case class CharacterHpSet(characterId: CharacterId, amount: Int)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsCharacterId
  case class CharacterDied(characterId: CharacterId)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsCharacterId
  case class CharacterRemovedFromMap(characterId: CharacterId)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsCharacterId
  case class CharacterTookAction(characterId: CharacterId)(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
    with ContainsCharacterId
  case class TurnFinished()(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
  case class PhaseFinished()(implicit phase: Phase, turn: Turn, causedById: String) extends GameEvent
}
case class GameLog(events: Seq[GameEvent])

case class GameState(
                      id: GameId,
                      charactersMetadata: Set[CharacterMetadata],
                      gameStatus: GameStatus,
                      pickType: PickType,
                      numberOfBans: Int,
                      numberOfCharactersPerPlayers: Int,
                      draftPickState: Option[DraftPickState],
                      blindPickState: Option[BlindPickState],
                      hexMap: Option[HexMap],
                      players: Seq[Player],
                      characters: Set[NKMCharacter],
                      phase: Phase,
                      turn: Turn,
                      characterIdsOutsideMap: Set[CharacterId],
                      characterIdsThatTookActionThisPhase: Set[CharacterId],
                      characterTakingActionThisTurn: Option[CharacterId],
                      playerIdsThatPlacedCharacters: Set[PlayerId],
                      clockConfig: ClockConfig,
                      clock: Clock,
                      gameLog: GameLog,
                    ) {
  private implicit val p: Phase = phase
  private implicit val t: Turn = turn

  def host: Player = players.find(_.isHost).get

  def currentPlayerNumber: Int = turn.number % players.size

  def playerNumber(playerId: PlayerId): Int = players.indexWhere(_.id == playerId)

  def playerById(playerId: PlayerId): Option[Player] = players.find(_.id == playerId)

  def isInChampionSelect: Boolean = Seq(GameStatus.CharacterPick, GameStatus.CharacterPicked).contains(gameStatus)

  def currentPlayer: Player = players(currentPlayerNumber)

  def currentPlayerTime: Long = clock.playerTimes(currentPlayer.id)

  def abilities: Set[Ability] = characters.flatMap(_.state.abilities)

  def triggerAbilities: Set[Ability with GameEventListener] = abilities.collect {case a: GameEventListener => a}

  def characterById(characterId: CharacterId): Option[NKMCharacter] = characters.find(_.id == characterId)

  def abilityById(abilityId: AbilityId): Option[Ability] = abilities.find(_.id == abilityId)

  def characterByEffectId(characterEffectId: CharacterEffectId): NKMCharacter =
    characters.find(_.state.effects.exists(_.id == characterEffectId)).get

  def characterPickFinished: Boolean = {
    val draftPickFinished = draftPickState.fold(false)(_.pickPhase == DraftPickPhase.Finished)
    val blindPickFinished = blindPickState.fold(false)(_.pickPhase == BlindPickPhase.Finished)
    draftPickFinished || blindPickFinished
  }

  def placingCharactersFinished: Boolean = playerIdsThatPlacedCharacters.size == players.size

  def timeoutNumber: Int = gameStatus match {
    case GameStatus.NotStarted => 0
    case GameStatus.CharacterPick | GameStatus.CharacterPicked =>
      pickType match {
        case PickType.AllRandom => 0
        case PickType.DraftPick => draftPickState.fold(0)(_.pickNumber)
        case PickType.BlindPick => blindPickState.fold(0)(_.pickNumber)
      }
    case GameStatus.CharacterPlacing | GameStatus.Running | GameStatus.Finished =>
      turn.number
  }

  private def executeEventTriggers(e: GameEvent): GameState =
    triggerAbilities.foldLeft(this)((acc, ability) => ability.onEvent(e)(acc))

  private def logEvent(e: GameEvent): GameState =
    copy(gameLog = gameLog.modify(_.events).using(es => es :+ e))
      .executeEventTriggers(e)

  private def updateClock(newClock: Clock)(implicit causedById: String): GameState =
    copy(clock = newClock).logEvent(ClockUpdated(newClock))

  private def updateGameStatus(newGameStatus: GameStatus): GameState =
    copy(gameStatus = newGameStatus)

  private def pickTime: Long = pickType match {
    case PickType.AllRandom => clockConfig.timeAfterPickMillis
    case PickType.DraftPick => clockConfig.maxBanTimeMillis
    case PickType.BlindPick => clockConfig.maxPickTimeMillis
  }

  def initializeCharacterPick(): GameState =
    updateClock(clock.setPickTime(pickTime))(id)

  def startGame(g: GameStartDependencies): GameState = {
    copy(
      charactersMetadata = g.charactersMetadata,
      players = g.players,
      hexMap = Some(g.hexMap),
      pickType = g.pickType,
      numberOfBans = g.numberOfBansPerPlayer,
      numberOfCharactersPerPlayers = g.numberOfCharactersPerPlayer,
      gameStatus = if (g.pickType == PickType.AllRandom) GameStatus.CharacterPicked else GameStatus.CharacterPick,
      draftPickState = if (g.pickType == PickType.DraftPick) Some(DraftPickState.empty(DraftPickConfig.generate(g))) else None,
      blindPickState = if (g.pickType == PickType.BlindPick) Some(BlindPickState.empty(BlindPickConfig.generate(g))) else None,
      clockConfig = g.clockConfig,
      clock = Clock.fromConfig(g.clockConfig, playerOrder = g.players.map(_.name)),
    ).initializeCharacterPick()
  }

  def placeCharactersRandomlyIfAllRandom()(implicit random: Random): GameState = {
    if (pickType == PickType.AllRandom) {
      // TODO: place characters as now they are outside of the map
      // TODO: move character assignment to start game
      assignCharactersToPlayers().copy(
        playerIdsThatPlacedCharacters = players.map(_.id).toSet,
      ).updateGameStatus(GameStatus.Running)
    } else this
  }

  def checkVictoryStatus(): GameState = {
    def filterPendingPlayers: Player => Boolean = _.victoryStatus == VictoryStatus.Pending

    if (gameStatus == GameStatus.CharacterPick && players.count(_.victoryStatus == VictoryStatus.Lost) > 0) {
      this.modify(_.players.eachWhere(filterPendingPlayers).victoryStatus)
        .setTo(VictoryStatus.Drawn)
        .updateGameStatus(GameStatus.Finished)
    } else if (players.count(_.victoryStatus == VictoryStatus.Pending) == 1) {
      this.modify(_.players.eachWhere(filterPendingPlayers).victoryStatus)
        .setTo(VictoryStatus.Won)
        .updateGameStatus(GameStatus.Finished)
    } else this
  }

  def generateCharacter(characterMetadataId: CharacterMetadataId)(implicit random: Random): NKMCharacter = {
    val characterId = NKMUtils.randomUUID
    val metadata = charactersMetadata.find(_.id == characterMetadataId).get
    NKMCharacter.fromMetadata(characterId, metadata)
  }

  def assignCharactersToPlayers()(implicit random: Random): GameState = {
    val characterSelection: Map[PlayerId, Iterable[CharacterMetadataId]] = pickType match {
      case PickType.AllRandom =>
        val pickedCharacters = random
          .shuffle(charactersMetadata.map(_.id))
          .grouped(numberOfCharactersPerPlayers)
          .take(players.length)
        players.map(_.id).zip(pickedCharacters).toMap
      case PickType.DraftPick =>
        draftPickState.get.characterSelection
      case PickType.BlindPick =>
        blindPickState.get.characterSelection
    }

    val playersWithCharacters =
      players.map(p => {
        val generatedCharacters = characterSelection(p.id).map(c => generateCharacter(c)).toSet
        (p, generatedCharacters)
      })
    val playersWithAssignedCharacters = playersWithCharacters.map{case (p, cs) => p.copy(characterIds = cs.map(_.id))}
    val characters = playersWithCharacters.flatMap(_._2).toSet

    copy(
      players = playersWithAssignedCharacters,
      characters = characters,
      characterIdsOutsideMap = characters.map(c => c.id),
    )
  }

  def checkIfCharacterPickFinished()(implicit random: Random): GameState =
    if(characterPickFinished) {
      updateGameStatus(GameStatus.CharacterPicked)
        .updateClock(clock.setPickTime(clockConfig.timeAfterPickMillis))(id)
        .assignCharactersToPlayers()
    } else this

  def startPlacingCharacters()(implicit random: Random): GameState =
    updateGameStatus(GameStatus.CharacterPlacing).placeCharactersRandomlyIfAllRandom()

  def decreasePickTime(timeMillis: Long): GameState =
    updateClock(clock.decreasePickTime(timeMillis))(id)

  def decreaseTime(playerId: PlayerId, timeMillis: Long): GameState =
    updateClock(clock.decreaseTime(playerId, timeMillis))(playerId)

  def increaseTime(playerId: PlayerId, timeMillis: Long): GameState =
    updateClock(clock.increaseTime(playerId, timeMillis))(playerId)

  def pause(): GameState =
    updateClock(clock.pause())(id)

  def unpause(): GameState =
    updateClock(clock.unpause())(id)

  def surrender(playerIds: PlayerId*): GameState = {
    def filterPlayers: Player => Boolean = p => playerIds.contains(p.name)

    this.modify(_.players.eachWhere(filterPlayers).victoryStatus).setTo(VictoryStatus.Lost).checkVictoryStatus()
  }

  def ban(playerId: PlayerId, characterIds: Set[CharacterMetadataId]): GameState =
    copy(draftPickState = draftPickState.map(_.ban(playerId, characterIds)))

  def finishBanningPhase(): GameState =
    copy(
      draftPickState = draftPickState.map(_.finishBanning()),
    ).updateClock(clock.setPickTime(clockConfig.maxPickTimeMillis))(id)

  def pick(playerId: PlayerId, characterId: CharacterMetadataId)(implicit random: Random): GameState =
    copy(
      draftPickState = draftPickState.map(_.pick(playerId, characterId)),
    ).updateClock(clock.setPickTime(clockConfig.maxPickTimeMillis))(id)
      .checkIfCharacterPickFinished()

  def draftPickTimeout(): GameState =
    surrender(draftPickState.get.currentPlayerPicking.get)

  def blindPick(playerId: PlayerId, characterIds: Set[CharacterMetadataId])(implicit random: Random): GameState =
    copy(blindPickState = blindPickState.map(_.pick(playerId, characterIds)))
      .checkIfCharacterPickFinished()

  def blindPickTimeout(): GameState =
    surrender(blindPickState.get.pickingPlayers: _*)

  def checkIfPlacingCharactersFinished(): GameState =
    if(placingCharactersFinished) updateGameStatus(GameStatus.Running) else this

  def placeCharacters(playerId: PlayerId, coordinatesToCharacterIdMap: Map[HexCoordinates, CharacterId]): GameState =
    coordinatesToCharacterIdMap.foldLeft(this){case (acc, (coordinate, characterId)) => acc.placeCharacter(coordinate, characterId)(playerId)}
      .copy(playerIdsThatPlacedCharacters = playerIdsThatPlacedCharacters + playerId)
      .checkIfPlacingCharactersFinished()

  def placeCharacter(targetCellCoordinates: HexCoordinates, characterId: CharacterId)(implicit causedBy: String): GameState =
    updateHexCell(targetCellCoordinates)(_.copy(characterId = Some(characterId)))
    .modify(_.characterIdsOutsideMap).using(_.filter(_ != characterId))
      .logEvent(CharacterPlaced(characterId, targetCellCoordinates))

  def basicMoveCharacter(playerId: PlayerId, path: Seq[HexCoordinates], characterId: CharacterId): GameState = {
    implicit val causedBy: CharacterId = characterId
    val newGameState = takeActionWithCharacter(characterId)
    // case if character dies on the way? make a test of this and create a new functions with while(onMap)
    path.tail.foldLeft(newGameState)((acc, coordinate) => acc.teleportCharacter(coordinate, characterId)(playerId))
      .logEvent(CharacterBasicMoved(characterId, path))
  }

  def teleportCharacter(targetCellCoordinates: HexCoordinates, characterId: CharacterId)(implicit causedBy: String): GameState = {
    val parentCellOpt = characterById(characterId).get.parentCell(this)

    parentCellOpt.fold(this)(c => updateHexCell(c.coordinates)(_.copy(characterId = None)))
    .updateHexCell(targetCellCoordinates)(_.copy(characterId = Some(characterId)))
    .logEvent(CharacterTeleported(characterId, targetCellCoordinates))
  }


  def basicAttack(attackingCharacterId: CharacterId, targetCharacterId: CharacterId): GameState = {
    implicit val causedBy: CharacterId = attackingCharacterId
    val newGameState = takeActionWithCharacter(attackingCharacterId)
    val attackingCharacter = characterById(attackingCharacterId).get
    attackingCharacter.basicAttack(targetCharacterId)(newGameState)
      .logEvent(CharacterBasicAttacked(attackingCharacterId, targetCharacterId))
  }

  private def updateCharacter(characterId: CharacterId)(updateFunction: NKMCharacter => NKMCharacter): GameState =
    this.modify(_.characters.each).using {
      case character if character.id == characterId => updateFunction(character)
      case character => character
    }

  private def updateHexCell(targetCoords: HexCoordinates)(updateFunction: HexCell => HexCell): GameState =
    this.modify(_.hexMap.each.cells.each).using {
      case cell if cell.coordinates == targetCoords => updateFunction(cell)
      case cell => cell
    }

  def updateAbility(abilityId: AbilityId, newAbility: Ability): GameState =
    this.modify(_.characters.each.state.abilities.each).using {
      case ability if ability.id == abilityId => newAbility
      case ability => ability
    }

  def damageCharacter(characterId: CharacterId, damage: Damage)(implicit causedBy: String): GameState =
    updateCharacter(characterId)(_.receiveDamage(damage))
      .logEvent(CharacterDamaged(characterId, damage))
      .removeFromMapIfDead(characterId)

  def heal(characterId: CharacterId, amount: Int)(implicit causedBy: String): GameState =
    updateCharacter(characterId)(_.heal(amount))
      .logEvent(CharacterHealed(characterId, amount))

  def setHp(characterId: CharacterId, amount: Int)(implicit causedBy: String): GameState =
    updateCharacter(characterId)(_.modify(_.state.healthPoints).setTo(amount))
      .logEvent(CharacterHpSet(characterId, amount))

  def removeFromMapIfDead(characterId: CharacterId)(implicit causedBy: String): GameState =
    if(characterById(characterId).get.isDead) {
      logEvent(CharacterDied(characterId))
        .removeCharacterFromMap(characterId)
    } else this

  def addEffect(characterId: CharacterId, characterEffect: CharacterEffect)(implicit causedById: String): GameState =
    updateCharacter(characterId)(_.addEffect(characterEffect))
      .logEvent(EffectAppliedOnCharacter(characterEffect.id, characterId))

  def removeEffects(characterEffectIds: Seq[CharacterEffectId])(implicit causedById: String): GameState =
    characterEffectIds.foldLeft(this){case (acc, eid) => acc.removeEffect(eid)}

  def removeEffect(characterEffectId: CharacterEffectId)(implicit causedById: String): GameState = {
    val character = characterByEffectId(characterEffectId)
    updateCharacter(character.id)(_.removeEffect(characterEffectId))
      .logEvent(EffectRemovedFromCharacter(characterEffectId))
  }

  def removeCharacterFromMap(characterId: CharacterId)(implicit causedById: String): GameState = {
    val parentCellOpt = characterById(characterId).get.parentCell(this)

    parentCellOpt.fold(this)(c => updateHexCell(c.coordinates)(_.copy(characterId = None)))
      .modify(_.characterIdsOutsideMap).setTo(characterIdsOutsideMap + characterId)
      .logEvent(CharacterRemovedFromMap(characterId))
  }

  def takeActionWithCharacter(characterId: CharacterId): GameState = {
    implicit val causedById: String = characterId

    this.modify(_.characterTakingActionThisTurn)
      .setTo(Some(characterId))
      .logEvent(CharacterTookAction(characterId))
  }

  def abilityHitCharacter(abilityId: AbilityId, targetCharacter: CharacterId): GameState = {
    implicit val causedById: String = abilityId
    logEvent(AbilityHitCharacter(abilityId: AbilityId, targetCharacter: CharacterId))
  }

  def useAbilityWithoutTarget(abilityId: AbilityId): GameState = {
    implicit val causedById: String = abilityId
    val ability = abilityById(abilityId).get.asInstanceOf[Ability with UsableWithoutTarget]
    val parentCharacter = ability.parentCharacter(this)

    val newGameState = takeActionWithCharacter(parentCharacter.id)
    ability.use()(newGameState)
      .logEvent(AbilityUsedWithoutTarget(abilityId))
  }

  def useAbilityOnCoordinates(abilityId: AbilityId, target: HexCoordinates, useData: UseData = UseData()): GameState = {
    implicit val causedById: String = abilityId
    val ability = abilityById(abilityId).get.asInstanceOf[Ability with UsableOnCoordinates]
    val parentCharacter = ability.parentCharacter(this)

    val newGameState = takeActionWithCharacter(parentCharacter.id)
    ability.use(target, useData)(newGameState)
      .logEvent(AbilityUsedOnCoordinates(abilityId, target))
  }

  def useAbilityOnCharacter(abilityId: AbilityId, target: CharacterId, useData: UseData = UseData()): GameState = {
    implicit val causedById: String = abilityId
    val ability = abilityById(abilityId).get.asInstanceOf[Ability with UsableOnCharacter]
    val parentCharacter = ability.parentCharacter(this)

    val newGameState = takeActionWithCharacter(parentCharacter.id)
    ability.use(target, useData)(newGameState)
      .logEvent(AbilityUsedOnCharacter(abilityId, target))
  }

  def incrementTurn(): GameState =
    this.modify(_.turn).using(oldTurn => Turn(oldTurn.number + 1))

  def endTurn(): GameState = {
    implicit val causedById: String = id

    this.modify(_.characterIdsThatTookActionThisPhase).using(c => c + characterTakingActionThisTurn.get)
      .modify(_.characterTakingActionThisTurn).setTo(None)
      .incrementTurn()
      .finishPhaseIfEveryCharacterTookAction()
      .logEvent(TurnFinished())
  }

  def passTurn(characterId: CharacterId): GameState =
    takeActionWithCharacter(characterId).endTurn()

  def refreshCharacterTakenActions(): GameState =
    this.modify(_.characterIdsThatTookActionThisPhase).setTo(Set.empty)

  def incrementPhase(by: Int = 1): GameState =
    this.modify(_.phase).using(oldPhase => Phase(oldPhase.number + by))

  def finishPhase(): GameState = {
    implicit val causedById: String = id

    refreshCharacterTakenActions()
      .incrementPhase()
      .logEvent(PhaseFinished())
  }

  def finishPhaseIfEveryCharacterTookAction(): GameState =
    if(characterIdsThatTookActionThisPhase == characters.map(_.id))
      this.finishPhase()
    else this

  def toView(forPlayer: Option[PlayerId]): GameStateView =
    GameStateView(
      id = id,
      charactersMetadata = charactersMetadata,
      gameStatus = gameStatus,
      pickType = pickType,
      numberOfBans = numberOfBans,
      numberOfCharactersPerPlayers = numberOfCharactersPerPlayers,
      draftPickState = draftPickState.map(_.toView(forPlayer)),
      blindPickState = blindPickState.map(_.toView(forPlayer)),
      hexMap = hexMap,
      players = players,
      characters = characters.map(_.toView),
      phase = phase,
      turn = turn,
      characterIdsOutsideMap = characterIdsOutsideMap,
      characterIdsThatTookActionThisPhase = characterIdsThatTookActionThisPhase,
      characterTakingActionThisTurn = characterTakingActionThisTurn,
      playerIdsThatPlacedCharacters = playerIdsThatPlacedCharacters,
      clockConfig = clockConfig,
      clock = clock,
      currentPlayerId = currentPlayer.id,

    )

  def shortInfo: String =
    s"""
      | id: $id
      | hexMap: ${hexMap.fold("None")(_.toString)}
      | characterIdsOutsideMap: $characterIdsOutsideMap
      | characterIdsThatTookActionThisPhase: $characterIdsThatTookActionThisPhase
      | characterTakingActionThisTurn: $characterTakingActionThisTurn
      | phase: $phase
      | turn: $turn
      | players: $players
      | gameStatus: $gameStatus
      | currentPlayerId: ${if(players.nonEmpty) currentPlayer.id else "None"}
      |""".stripMargin
}

object GameState {
  def empty(id: String): GameState = {
    val defaultPickType = PickType.AllRandom
    val defaultClockConfig = ClockConfig.defaultForPickType(defaultPickType)

    GameState(
      id = id,
      charactersMetadata = Set(),
      numberOfBans = 0,
      numberOfCharactersPerPlayers = 1,
      draftPickState = None,
      blindPickState = None,
      hexMap = None,
      players = Seq(),
      characters = Set(),
      phase = Phase(0),
      turn = Turn(0),
      gameStatus = GameStatus.NotStarted,
      pickType = defaultPickType,
      characterIdsOutsideMap = Set(),
      characterIdsThatTookActionThisPhase = Set(),
      characterTakingActionThisTurn = None,
      playerIdsThatPlacedCharacters = Set(),
      clockConfig = defaultClockConfig,
      clock = Clock.fromConfig(defaultClockConfig, Seq()),
      gameLog = GameLog(Seq.empty),
    )
  }
}

case class GameStateView(
                          id: GameId,
                          charactersMetadata: Set[CharacterMetadata],
                          gameStatus: GameStatus,
                          pickType: PickType,
                          numberOfBans: Int,
                          numberOfCharactersPerPlayers: Int,
                          draftPickState: Option[DraftPickStateView],
                          blindPickState: Option[BlindPickStateView],
                          hexMap: Option[HexMap],
                          players: Seq[Player],
                          characters: Set[NKMCharacterView],
                          phase: Phase,
                          turn: Turn,
                          characterIdsOutsideMap: Set[CharacterId],
                          characterIdsThatTookActionThisPhase: Set[CharacterId],
                          characterTakingActionThisTurn: Option[CharacterId],
                          playerIdsThatPlacedCharacters: Set[PlayerId],
                          clockConfig: ClockConfig,
                          clock: Clock,

                          currentPlayerId: PlayerId,
                        )
