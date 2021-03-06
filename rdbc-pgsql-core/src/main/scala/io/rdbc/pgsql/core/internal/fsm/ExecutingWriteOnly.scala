/*
 * Copyright 2016 rdbc contributors
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

package io.rdbc.pgsql.core.internal.fsm

import io.rdbc.pgsql.core.internal.PgMsgHandler
import io.rdbc.pgsql.core.internal.protocol.messages.backend._

import scala.concurrent.Promise

private[core]
class ExecutingWriteOnly private[fsm](parsePromise: Promise[Unit],
                                      resultPromise: Promise[Long])
  extends State {

  protected val msgHandler: PgMsgHandler = {
    case BindComplete => stay
    case ParseComplete =>
      parsePromise.success(())
      stay

    case _: DataRow => stay
    case EmptyQueryResponse => finished(0L)
    case CommandComplete(_, rowsAffected) => finished(rowsAffected.map(_.toLong).getOrElse(0L))
  }

  private def finished(rowsAffected: Long): StateAction = traced {
    goto(State.waitingAfterSuccess(resultPromise, rowsAffected))
  }

  private def sendFailureToClient(ex: Throwable): Unit = traced {
    if (!parsePromise.isCompleted) parsePromise.failure(ex)
    resultPromise.failure(ex)
    ()
  }

  protected def onFatalError(ex: Throwable): Unit = traced {
    sendFailureToClient(ex)
  }

  protected def onNonFatalError(ex: Throwable): StateAction = traced {
    goto(State.waitingAfterFailure(sendFailureToClient(_), ex))
  }
}
