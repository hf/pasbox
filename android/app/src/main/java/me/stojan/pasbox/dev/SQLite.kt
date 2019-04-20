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

package me.stojan.pasbox.dev

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class SQLiteQuery {
  var _distinct: Boolean = false
  var _table: String? = null
  var _columns: Array<out String>? = null
  var _selection: String? = null
  var _selectionArgs: Array<out String>? = null
  var _order: String? = null
  var _limit: String? = null
  var _groupBy: String? = null
  var _having: String? = null

  inline fun select(vararg columns: String) {
    this._columns = columns
  }

  inline fun selectDistinct(vararg columns: String) {
    this._distinct = true
    this._columns = columns
  }

  inline fun args(vararg args: Any?) {
    this._selectionArgs = Array(args.size) { i ->
      when (args[i]) {
        null -> "NULL"
        is String -> "'${args[i]}'"
        else -> args[i].toString()
      }
    }
  }

  inline fun from(table: String) {
    this._table = table
  }

  inline fun where(where: String) {
    this._selection = where
  }

  inline fun orderBy(order: String) {
    this._order = order
  }

  inline fun limit(rows: Long, offset: Long = 0) {
    this._limit = "$offset, $rows"
  }

  inline fun groupBy(groupBy: String) {
    this._groupBy = groupBy
  }

  inline fun having(having: String) {
    this._having = having
  }
}

inline fun SQLiteDatabase.query(queryFn: SQLiteQuery.() -> Unit): Cursor =
  SQLiteQuery()
    .apply(queryFn)
    .let { query ->
      this.query(
        query._distinct,
        query._table,
        query._columns,
        query._selection,
        query._selectionArgs,
        query._groupBy,
        query._having,
        query._order,
        query._limit
      )
    }


inline fun <R> SQLiteDatabase.transaction(txFn: (SQLiteDatabase) -> R): R =
  try {
    beginTransaction()
    val returnValue = txFn(this)
    setTransactionSuccessful()
    returnValue
  } finally {
    endTransaction()
  }
