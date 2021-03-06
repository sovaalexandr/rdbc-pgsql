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

package io.rdbc.pgsql.core.internal.protocol.codec.sco

import java.nio.charset.Charset

import io.rdbc.pgsql.core.internal.protocol.Argument
import scodec.bits.BitVector
import scodec.codecs.{bytes, int16, int32, string, variableSizeBytes, vectorOfN}
import scodec.{Attempt, Codec, DecodeResult, Err, SizeBound}

private[sco] object ParamValuesCodec {

  private val formatCodec = new Codec[Argument] {
    val sizeBound = SizeBound.exact(16)

    def encode(value: Argument): Attempt[BitVector] = {
      value match {
        case _: Argument.Textual | _: Argument.Null => int16.encode(0)
        case _: Argument.Binary => int16.encode(1)
      }
    }

    def decode(bits: BitVector): Attempt[DecodeResult[Argument]] = {
      Attempt.failure(Err("decoding not supported"))
    }
  }

  private def valueCodec(implicit charset: Charset) = new Codec[Argument] {
    def sizeBound: SizeBound = SizeBound.atLeast(32)

    def encode(value: Argument): Attempt[BitVector] = value match {
      case _: Argument.Null => int32.encode(-1)
      case Argument.Textual(value, _) => variableSizeBytes(int32, string).encode(value)
      case Argument.Binary(value, _) => variableSizeBytes(int32, bytes).encode(value)
    }

    def decode(bits: BitVector): Attempt[Nothing] = {
      Attempt.failure(Err("decoding not supported"))
    }
  }

  def paramValues(implicit charset: Charset): Codec[Vector[Argument]] = new Codec[Vector[Argument]] {

    val sizeBound: SizeBound = SizeBound.unknown

    def encode(params: Vector[Argument]): Attempt[BitVector] = {
      {
        vectorOfN(int16, formatCodec) ~ vectorOfN(int16, valueCodec)
      }.encode((params, params))
    }

    def decode(bits: BitVector): Attempt[Nothing] = {
      Attempt.failure(Err("decoding not supported"))
    }
  }
}
