/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package anystream.frontend.util

import org.jetbrains.compose.web.attributes.AttrsScope
import org.w3c.dom.Element

fun <TElement : Element> AttrsScope<TElement>.tooltip(
    title: String,
    placement: String,
) {
    attr("data-bs-toggle", "tooltip")
    attr("data-bs-placement", placement)
    attr("title", title)
    ref {
        val tooltip = Bootstrap.Tooltip(it)
        onDispose {
            tooltip.dispose()
        }
    }
}
