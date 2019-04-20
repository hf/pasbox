/*
 * Copyright (C) 2019
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package me.stojan.pasbox.storage

import io.reactivex.Observable
import io.reactivex.Single

interface SecretStore {
  class Query(val offset: Long = 0, val count: Long = 0)

  class Page(val total: Long, val offset: Long, val results: ArrayList<Pair<SecretPublic, Secret>>) {
    val count: Int = results.size
  }

  val modifications: Observable<Pair<SecretPublic, Secret>>

  fun save(data: Single<Pair<SecretPublic, SecretPrivate>>): Single<Pair<SecretPublic, SecretPrivate>>
  fun open(data: Single<Pair<SecretPublic, Secret>>): Single<Pair<SecretPublic, SecretPrivate>>

  fun page(query: Query): Single<Page>
}