/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 *                                                                                               *
 *  Copyright  (C)  2015-2016  Christian Krause                                                  *
 *                                                                                               *
 *  Christian Krause  <kizkizzbangbang@gmail.com>                                                *
 *                                                                                               *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 *                                                                                               *
 *  This file is part of strace-analyzer.                                                        *
 *                                                                                               *
 *  strace-analyzer is free software: you can redistribute it and/or modify it under the terms   *
 *  of the GNU General Public License as published by the Free Software Foundation, either       *
 *  version 3 of the License, or any later version.                                              *
 *                                                                                               *
 *  strace-analyzer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; *
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.    *
 *  See the GNU General Public License for more details.                                         *
 *                                                                                               *
 *  You should have received a copy of the GNU General Public License along with                 *
 *  strace-analyzer. If not, see <http://www.gnu.org/licenses/>.                                 *
 *                                                                                               *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */


package strace
package analyze

import scalaz.Monoid
import scalaz.concurrent.Task
import scalaz.std.map._
import scalaz.std.list._
import scalaz.stream._

object util {
  implicit class RichProcess[O](underlying: Process[Task,O]) {

    def groupBy[K](f: O => K)(implicit M: Monoid[O]): Task[Map[K, List[O]]] =
      underlying runFoldMap { cur =>
        Map(f(cur) -> List(cur))
      }

    def groupByFoldMonoid[K](f: O => K)(implicit MO: Monoid[O]): Task[Map[K, O]] =
      groupByFoldMap(f)(identity)

    def groupByFoldMap[K,O2](f: O => K)(g: O => O2)(implicit MO: Monoid[O2]): Task[Map[K, O2]] =
      underlying runFoldMap { cur =>
        Map(f(cur) -> g(cur))
      }

  }
}
