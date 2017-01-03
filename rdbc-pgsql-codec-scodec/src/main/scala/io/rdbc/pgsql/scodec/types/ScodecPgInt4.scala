/*
 * Copyright 2016-2017 Krzysztof Pado
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

package io.rdbc.pgsql.scodec.types

import _root_.scodec.codecs.int32
import io.rdbc.pgsql.core.SessionParams
import io.rdbc.pgsql.core.types.PgInt4
import scodec.Codec

object ScodecPgInt4 extends ScodecPgType[Int] with PgInt4 with CommonCodec[Int] {
  def codec(implicit sessionParams: SessionParams): Codec[Int] = int32
}