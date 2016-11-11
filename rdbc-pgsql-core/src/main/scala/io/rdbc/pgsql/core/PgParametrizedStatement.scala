/*
 * Copyright 2016 Krzysztof Pado
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rdbc.pgsql.core

import com.typesafe.scalalogging.StrictLogging
import io.rdbc.ImmutSeq
import io.rdbc.implbase.ParametrizedStatementPartialImpl
import io.rdbc.pgsql.core.fsm.extendedquery.{BeginningTx, WaitingForDescribe}
import io.rdbc.pgsql.core.messages.backend.TxStatus
import io.rdbc.pgsql.core.messages.frontend._
import io.rdbc.pgsql.core.scheduler.TimeoutScheduler
import io.rdbc.sapi.{ParametrizedStatement, ResultStream}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

class PgParametrizedStatement(conn: PgConnection,
                              nativeSql: String,
                              params: ImmutSeq[DbValue])
  extends ParametrizedStatement
    with ParametrizedStatementPartialImpl
    with StrictLogging {

  implicit val ec = conn.ec

  private def cachedPreparedStatement: Option[String] = {
    conn.stmtCache.get(nativeSql)
  }

  protected def parseAndBind: (Option[Parse], Bind) = {
    val (stmtName, parse) = if (cachedPreparedStatement.isDefined) {
      (cachedPreparedStatement, Option.empty[Parse])
    } else {
      val stmtName = if (shouldCache()) Some(conn.nextStmtName()) else None
      val parse = Some(Parse(stmtName, nativeSql, params.map(_.dataTypeOid).toVector))
      (stmtName, parse)
    }

    //TODO AllTextual TODO toList
    (parse, Bind(stmtName.map(_ + "P"), stmtName, params.toList, AllBinary))
  }

  protected def shouldCache(): Boolean = {
    //TODO introduce a cache threshold
    true
  }

  def executeForStream()(implicit timeout: FiniteDuration): Future[ResultStream] = conn.ifReady { (reqId, txStatus) =>
    logger.debug(s"Executing statement '$nativeSql'")
    val (parse, bind) = parseAndBind

    val streamPromise = Promise[PgResultStream]
    val parsePromise = Promise[Unit]

    val timeoutScheduler = TimeoutScheduler {
      conn.scheduler.schedule(timeout) {
        conn.onTimeout(reqId)
      }
    }

    txStatus match {
      case TxStatus.Active =>
        conn.triggerTransition(WaitingForDescribe.withoutTxMgmt(bind.portal, streamPromise, parsePromise, conn.sessionParams,
          timeoutScheduler, conn.rdbcTypeConvRegistry, conn.pgTypeConvRegistry)(conn.out, ec))
        parse.foreach(conn.out.write(_))
        conn.out.writeAndFlush(bind, Describe(PortalType, bind.portal), Sync)

      case TxStatus.Idle =>
        conn.triggerTransition(BeginningTx(parse, bind, streamPromise, parsePromise, conn.sessionParams, timeoutScheduler, conn.rdbcTypeConvRegistry, conn.pgTypeConvRegistry)(conn.out, ec))
        conn.out.writeAndFlush(Query("BEGIN"))

      case TxStatus.Failed => ??? //TODO
    }

    parse.flatMap(_.optionalName).foreach { stmtName =>
      parsePromise.future.onSuccess {
        case _ => conn.stmtCache = conn.stmtCache.put(nativeSql, stmtName)
      }
    }

    streamPromise.future
  }

  def deallocate(): Future[Unit] = conn.deallocateStatement(nativeSql)

  def connWatchForIdle: Future[PgConnection] = conn.watchForIdle
}
