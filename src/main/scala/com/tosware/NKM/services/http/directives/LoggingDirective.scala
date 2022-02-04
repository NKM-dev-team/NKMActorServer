package com.tosware.NKM.services.http.directives

import akka.event.Logging.LogLevel
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives.logRequestResult
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.server.directives.{LogEntry, LoggingMagnet}
import com.tosware.NKM.serializers.NKMJsonProtocol

trait LoggingDirective extends NKMJsonProtocol {
  def akkaResponseTimeLoggingFunction(
                                       loggingAdapter: LoggingAdapter,
                                       requestTimestamp: Long,
                                       level: LogLevel = Logging.InfoLevel)(req: HttpRequest)(res: RouteResult): Unit = {
    val entry = res match {
      case RouteResult.Complete(res) =>
        val responseTimestamp = System.nanoTime
        val elapsedTime = (responseTimestamp - requestTimestamp) / 1000000
        val loggingString =
          s"""
             |${req.uri}
             |${req.method.name} | ${res.status} | ${res.entity.contentType.toString}
             |Elapsed time: $elapsedTime ms""".stripMargin
        LogEntry(loggingString, level)

      case RouteResult.Rejected(reason) =>
        val loggingString =
          s"""
             |${req.uri}
             |${req.method.name}
             |Rejected. ${reason.mkString(",")}""".stripMargin
        LogEntry(loggingString, level)
    }
    entry.logTo(loggingAdapter)
  }

  def printResponseTime(log: LoggingAdapter) = {
    val requestTimestamp = System.nanoTime
    akkaResponseTimeLoggingFunction(log, requestTimestamp) _
  }

  val logRequestResponse = logRequestResult(LoggingMagnet(printResponseTime))

}
