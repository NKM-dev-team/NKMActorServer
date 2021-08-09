package com.tosware.NKM.models.game

case class GameStartDependencies(
                                  players: List[Player],
                                  hexMap: HexMap,
                                  pickType: PickType,
                                  numberOfBans: Int,
                                  numberOfCharactersPerPlayers: Int,
                                )