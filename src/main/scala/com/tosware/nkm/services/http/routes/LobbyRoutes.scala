package com.tosware.nkm.services.http.routes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import com.tosware.nkm.NkmDependencies
import com.tosware.nkm.models.lobby.ws._
import com.tosware.nkm.services.LobbyService
import com.tosware.nkm.services.http.directives.{JwtDirective, JwtSecretKey}


class LobbyRoutes(deps: NkmDependencies) extends JwtDirective
  with SprayJsonSupport
{
  val jwtSecretKey: JwtSecretKey = deps.jwtSecretKey
  val lobbyService: LobbyService = deps.lobbyService

  val lobbyGetRoutes = concat(
    path(LobbyRoute.Lobbies.value) {
      val lobbies = lobbyService.getAllLobbies()
      complete(lobbies)
    },
    path(LobbyRoute.Lobby.value / Segment) { (lobbyId: String) =>
      val lobby = lobbyService.getLobbyState(lobbyId)
      complete(lobby)
    },
  )
}