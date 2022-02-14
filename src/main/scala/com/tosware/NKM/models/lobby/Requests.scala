package com.tosware.NKM.models.lobby

import com.tosware.NKM.models.game.PickType

trait LobbyRequest
final case class AuthRequest(token: String) extends LobbyRequest
final case class GetLobbyRequest(lobbyId: String) extends LobbyRequest
final case class LobbyCreationRequest(name: String) extends LobbyRequest
final case class LobbyJoinRequest(lobbyId: String) extends LobbyRequest
final case class LobbyLeaveRequest(lobbyId: String) extends LobbyRequest
case class SetHexMapNameRequest(lobbyId: String, hexMapName: String) extends LobbyRequest
case class SetNumberOfBansRequest(lobbyId: String, numberOfBans: Int) extends LobbyRequest
case class SetNumberOfCharactersPerPlayerRequest(lobbyId: String, charactersPerPlayer: Int) extends LobbyRequest
case class SetPickTypeRequest(lobbyId: String, pickType: PickType) extends LobbyRequest
final case class StartGameRequest(lobbyId: String) extends LobbyRequest
