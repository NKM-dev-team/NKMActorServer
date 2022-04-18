package com.tosware.NKM.actors

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.persistence.journal.Tagged
import akka.persistence.{PersistentActor, RecoveryCompleted}
import com.tosware.NKM.NKMTimeouts
import com.tosware.NKM.actors.NKMData.GetHexMaps
import com.tosware.NKM.models.CommandResponse._
import com.tosware.NKM.models.game.{GameStartDependencies, HexMap, PickType, Player}
import com.tosware.NKM.models.lobby.LobbyState
import com.tosware.NKM.services.NKMDataService

import java.time.LocalDateTime
import scala.concurrent.Await

object Lobby {
  sealed trait Query

  case object GetState extends Query

  sealed trait Command

  case object StartGame extends Command

  case class Create(name: String, hostUserId: String) extends Command

  case class UserJoin(userId: String) extends Command

  case class UserLeave(userId: String) extends Command

  case class SetMapName(hexMapName: String) extends Command

  case class SetNumberOfBans(numberOfBans: Int) extends Command

  case class SetNumberOfCharactersPerPlayer(numberOfCharactersPerPlayer: Int) extends Command

  case class SetPickType(pickType: PickType) extends Command

  case class SetLobbyName(name: String) extends Command

  sealed trait Event {
    val id: String
  }

  case class CreateSuccess(id: String, name: String, hostUserId: String, creationDate: LocalDateTime) extends Event

  case class UserJoined(id: String, userId: String) extends Event

  case class UserLeft(id: String, userId: String) extends Event

  case class MapNameSet(id: String, hexMapName: String) extends Event

  case class NumberOfBansSet(id: String, numberOfBans: Int) extends Event

  case class NumberOfCharactersPerPlayerSet(id: String, numberOfCharactersPerPlayer: Int) extends Event

  case class PickTypeSet(id: String, pickType: PickType) extends Event

  case class LobbyNameSet(id: String, name: String) extends Event

  def props(id: String)(implicit NKMDataService: NKMDataService): Props = Props(new Lobby(id))
}

class Lobby(id: String)(implicit NKMDataService: NKMDataService)
  extends PersistentActor
    with ActorLogging
    with NKMTimeouts {

  import Lobby._

  override def persistenceId: String = s"lobby-$id"

  override def log = {
    akka.event.Logging(context.system, s"${this.getClass}($persistenceId)")
  }

  var lobbyState: LobbyState = LobbyState(id)
  val gameActor: ActorRef = context.system.actorOf(Game.props(id))
  val nkmData: ActorRef = context.system.actorOf(NKMData.props())

  def canStartGame(): Boolean =
    lobbyState.chosenHexMapName.nonEmpty && lobbyState.userIds.length > 1

  def create(name: String, hostUserId: String, creationDate: LocalDateTime): Unit =
    lobbyState = lobbyState.copy(name = Some(name), creationDate = Some(creationDate), hostUserId = Some(hostUserId), userIds = List(hostUserId))

  def joinLobby(userId: String): Unit =
    lobbyState = lobbyState.copy(userIds = lobbyState.userIds :+ userId)

  def leaveLobby(userId: String): Unit =
    lobbyState = lobbyState.copy(userIds = lobbyState.userIds.filterNot(_ == userId))

  def setMapName(hexMapName: String): Unit =
    lobbyState = lobbyState.copy(chosenHexMapName = Some(hexMapName))

  def setNumberOfBans(numberOfBans: Int): Unit =
    lobbyState = lobbyState.copy(numberOfBans = numberOfBans)

  def setNumberOfCharactersPerPlayer(numberOfCharacters: Int): Unit =
    lobbyState = lobbyState.copy(numberOfCharactersPerPlayer = numberOfCharacters)

  def setPickType(pickType: PickType): Unit =
    lobbyState = lobbyState.copy(pickType = pickType)

  def setLobbyName(name: String): Unit =
    lobbyState = lobbyState.copy(name = Some(name))

  def persistAndPublish[A](event: A)(handler: A => Unit): Unit = {
    context.system.eventStream.publish(event)
    persist(event)(handler)
  }

  def persistAndPublishWithTag[A](event: A, tag: String)(handler: Tagged => Unit): Unit = {
    context.system.eventStream.publish(event)
    persist(Tagged(event, Set(tag)))(handler)
  }

  override def receive: Receive = {
    case GetState =>
      log.debug("Received state request")
      sender() ! lobbyState
    case Create(name, hostUserId) =>
      log.debug(s"Received create request")
      if (lobbyState.created()) {
        sender() ! Failure("Lobby is already created")
      } else {
        val creationDate = LocalDateTime.now()
        val e = CreateSuccess(id, name, hostUserId, creationDate)
        persistAndPublishWithTag(e, "lobby") { _ =>
          create(name, hostUserId, creationDate)
          log.info(s"Created lobby $name for $hostUserId")
          sender() ! Success()
        }
      }
    case UserJoin(userId: String) =>
      log.debug(s"$userId tries to join lobby")
      if (!lobbyState.created()) {
        sender() ! Failure("Lobby is not created")
      } else if (lobbyState.userIds.contains(userId)) {
        sender() ! Failure(s"Lobby already contains $userId")
      } else {
        val e = UserJoined(id, userId)
        persistAndPublish(e) { _ =>
          joinLobby(userId)
          log.info(s"$userId joined lobby")
          sender() ! Success()
        }
      }
    case UserLeave(userId: String) =>
      log.debug(s"$userId tries to leave lobby")
      if (!lobbyState.created()) {
        sender() ! Failure("Lobby is not created")
      } else if (!lobbyState.userIds.contains(userId)) {
        sender() ! Failure(s"Lobby does not contain $userId")
      } else {
        val e = UserLeft(id, userId)
        persistAndPublish(e) { _ =>
          leaveLobby(userId)
          log.info(s"$userId left the lobby")
          sender() ! Success()
        }
      }
    case SetMapName(hexMapName: String) =>
      if (!lobbyState.created()) {
        sender() ! Failure("Lobby is not created")
      } else {
        val e = MapNameSet(id, hexMapName)
        persistAndPublish(e) { _ =>
          setMapName(hexMapName)
          log.info(s"Set map name: $hexMapName")
          sender() ! Success()
        }
      }
    case SetNumberOfBans(numberOfBans) =>
      if (!lobbyState.created()) {
        sender() ! Failure("Lobby is not created")
      } else {
        val e = NumberOfBansSet(id, numberOfBans)
        persistAndPublish(e) { _ =>
          setNumberOfBans(numberOfBans)
          log.info(s"Set number of bans: $numberOfBans")
          sender() ! Success()
        }
      }
    case SetNumberOfCharactersPerPlayer(numberOfCharactersPerPlayer) =>
      if (!lobbyState.created()) {
        sender() ! Failure("Lobby is not created")
      } else {
        val e = NumberOfCharactersPerPlayerSet(id, numberOfCharactersPerPlayer)
        persistAndPublish(e) { _ =>
          setNumberOfCharactersPerPlayer(numberOfCharactersPerPlayer)
          log.info(s"Set number of characters: $numberOfCharactersPerPlayer")
          sender() ! Success()
        }
      }
    case SetPickType(pickType) =>
      if (!lobbyState.created()) {
        sender() ! Failure("Lobby is not created")
      } else {
        val e = PickTypeSet(id, pickType)
        persistAndPublish(e) { _ =>
          setPickType(pickType)
          log.info(s"Set pick type: $pickType")
          sender() ! Success()
        }
      }
    case SetLobbyName(name) =>
      if (!lobbyState.created()) {
        sender() ! Failure("Lobby is not created")
      } else {
        val e = LobbyNameSet(id, name)
        persistAndPublish(e) { _ =>
          setLobbyName(name)
          log.info(s"Set lobby name: $name")
          sender() ! Success()
        }
      }
    case StartGame =>
      if (!canStartGame()) {
        sender() ! Failure("Cannot start the game")
      } else {
        val hexMaps = Await.result(nkmData ? GetHexMaps, atMost).asInstanceOf[List[HexMap]]
        log.info("Received game start request")
        val deps = GameStartDependencies(
          players = lobbyState.userIds.map(i => Player(i)),
          hexMap = hexMaps.filter(m => m.name == lobbyState.chosenHexMapName.get).head,
          pickType = lobbyState.pickType,
          numberOfBans = lobbyState.numberOfBans,
          numberOfCharactersPerPlayers = lobbyState.numberOfCharactersPerPlayer,
          NKMDataService.getCharactersMetadata
        )
        val r = Await.result(gameActor ? Game.StartGame(deps), atMost).asInstanceOf[CommandResponse]
        sender() ! r
      }

    case e => log.warning(s"Unknown message: $e")
  }

  override def receiveRecover: Receive = {
    case CreateSuccess(_, name, hostUserId, creationDate) =>
      create(name, hostUserId, creationDate)
      log.debug(s"Recovered create")
    case UserJoined(_, userId) =>
      joinLobby(userId)
      log.debug(s"Recovered user join")
    case UserLeft(_, userId) =>
      leaveLobby(userId)
      log.debug(s"Recovered user leave")
    case MapNameSet(_, hexMapName) =>
      setMapName(hexMapName)
      log.debug(s"Recovered setting hex map name")
    case NumberOfBansSet(_, numberOfBans) =>
      setNumberOfBans(numberOfBans)
      log.debug(s"Recovered setting number of bans")
    case NumberOfCharactersPerPlayerSet(_, numberOfCharactersPerPlayer) =>
      setNumberOfCharactersPerPlayer(numberOfCharactersPerPlayer)
      log.debug(s"Recovered setting number of characters")
    case PickTypeSet(_, pickType) =>
      setPickType(pickType)
      log.debug(s"Recovered setting pick type")
    case LobbyNameSet(_, name) =>
      setLobbyName(name)
      log.debug(s"Recovered setting name")
    case RecoveryCompleted =>
    case e => log.warning(s"Unknown message: $e")
  }

  override def receiveCommand: Receive = {
    case _ =>
  }
}