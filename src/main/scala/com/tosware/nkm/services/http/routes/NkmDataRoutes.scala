package com.tosware.nkm.services.http.routes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import com.tosware.nkm.NkmDependencies
import com.tosware.nkm.services.NkmDataService
import com.tosware.nkm.services.http.directives.{JwtDirective, JwtSecretKey}

class NkmDataRoutes(deps: NkmDependencies) extends JwtDirective
  with SprayJsonSupport
{
  val jwtSecretKey: JwtSecretKey = deps.jwtSecretKey
  val nkmDataService: NkmDataService = deps.nkmDataService

  val nkmDataGetRoutes = concat(
    path("maps") {
      complete(nkmDataService.getHexMaps)
    },
    path("characters") {
      complete(nkmDataService.getCharacterMetadatas)
    },
    path("abilities") {
      complete(nkmDataService.getAbilityMetadatas)
    },
    path("character_effects") {
      complete(nkmDataService.getCharacterEffectMetadatas)
    },
  )
}