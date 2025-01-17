/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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
package anystream.frontend.components

import androidx.compose.runtime.*
import anystream.frontend.LocalAnyStreamClient
import anystream.frontend.libs.PopperElement
import anystream.frontend.libs.popperOptions
import anystream.frontend.util.ExternalClickMask
import anystream.frontend.util.rememberDomElement
import anystream.frontend.util.tooltip
import anystream.models.Permission
import anystream.models.api.SearchResponse
import app.softwork.routingcompose.BrowserRouter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.web.attributes.ATarget
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.attributes.target
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

val searchQuery = MutableStateFlow<String?>(null)

@Composable
fun Navbar() {
    val client = LocalAnyStreamClient.current
    val isAuthenticated = client.authenticated.collectAsState(client.isAuthenticated())
    Nav({
        classes(
            "navbar", "navbar-expand-lg", "navbar-dark",
            "rounded", "shadow", "m-2"
        )
        style {
            backgroundColor(rgba(0, 0, 0, 0.3))
        }
    }) {
        Div({ classes("container-fluid") }) {
            A(attrs = {
                classes("navbar-brand", "mx-2")
                style {
                    cursor("pointer")
                }
                onClick { BrowserRouter.navigate("/home") }
            }) {
                Img(src = "/images/as-logo.svg")
            }
            Div({ classes("collapse", "navbar-collapse") }) {
                val permissionsState = client.permissions.collectAsState(null)
                if (isAuthenticated.value) {
                    SearchBar()
                    SecondaryMenu(permissionsState.value ?: emptySet())
                }
            }
        }
    }
}

@Composable
private fun SecondaryMenu(permissions: Set<Permission>) {
    val client = LocalAnyStreamClient.current
    val scope = rememberCoroutineScope()
    val authMutex = remember { Mutex() }
    var isMenuVisible by remember { mutableStateOf(false) }
    Div({ classes("navbar-nav", "ms-auto") }) {
        if (Permission.check(Permission.ConfigureSystem, permissions)) {
            val sessionCount by client.observeStreams()
                .retry()
                .map { it.playbackStates.size }
                .collectAsState(0)
            A(attrs = {
                classes("nav-link", "nav-link-large", "d-flex", "align-items-center")
                tooltip("Activity", "bottom")
                if (sessionCount > 0) {
                    style {
                        color(rgb(255, 8, 28))
                    }
                }
            }) {
                if (sessionCount > 0) {
                    Div({
                        classes("fs-6", "pe-1")
                        style {
                            color(rgb(255, 8, 28))
                        }
                    }) {
                        Text(sessionCount.toString())
                    }
                }
                I({ classes("bi", "bi-activity") })
            }
            A(attrs = {
                classes("nav-link", "nav-link-large")
                tooltip("Users", "bottom")
                onClick { BrowserRouter.navigate("/usermanager") }
            }) {
                I({ classes("bi", "bi-people") })
            }
            A(attrs = {
                classes("nav-link", "nav-link-large")
                tooltip("Settings", "bottom")
                onClick { BrowserRouter.navigate("/settings/activity") }
            }) {
                I({ classes("bi", "bi-gear") })
            }
        }
        var overflowMenuButtonElement by remember { mutableStateOf<HTMLElement?>(null) }
        A(attrs = {
            onClick { isMenuVisible = !isMenuVisible }
            classes("nav-link", "nav-link-large")
        }) {
            DisposableEffect(Unit) {
                overflowMenuButtonElement = scopeElement
                onDispose { overflowMenuButtonElement = null }
            }
            I({ classes("bi", "bi-three-dots-vertical") })
        }
        if (isMenuVisible) {
            overflowMenuButtonElement?.let { element ->
                OverflowMenu(
                    element,
                    onLogout = {
                        scope.launch {
                            authMutex.withLock { client.logout() }
                        }
                    },
                    onClose = { isMenuVisible = false }
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    element: HTMLElement,
    onLogout: () -> Unit,
    onClose: () -> Unit,
) {
    PopperElement(
        element,
        popperOptions(placement = "bottom-end"),
        attrs = {
            style { property("z-index", 100) }
        }
    ) { popper ->
        Div({
            classes("d-flex", "flex-column", "px-2", "bg-dark", "rounded", "shadow")
        }) {
            var globalClickHandler by remember { mutableStateOf<ExternalClickMask?>(null) }
            DisposableEffect(Unit) {
                globalClickHandler = ExternalClickMask(scopeElement) { remove ->
                    onClose()
                    remove()
                }
                globalClickHandler?.attachListener()
                onDispose {
                    globalClickHandler?.dispose()
                    globalClickHandler = null
                }
            }
            A("https://docs.anystream.dev", {
                classes("nav-link", "nav-link-large", "fs-6")
                target(ATarget.Blank)
            }) {
                I({ classes("bi", "bi-book", "me-2") })
                Text("Documentation")
            }
            A(null, {
                classes("nav-link", "nav-link-large", "fs-6")
                onClick { onLogout() }
            }) {
                I({ classes("bi", "bi-box-arrow-right", "me-2") })
                Text("Logout")
            }
        }
        LaunchedEffect(Unit) { popper.update() }
    }
}

@Composable
private fun SearchBar() {
    val client = LocalAnyStreamClient.current
    val focused = remember { mutableStateOf(false) }
    var elementValue by remember { mutableStateOf<String?>(null) }
    val inputRef = remember { mutableStateOf<HTMLInputElement?>(null) }
    val queryState by searchQuery
        .debounce(500)
        .collectAsState(null)
    val isDisplayingSearch = searchQuery
        .map { it != null }
        .collectAsState(queryState != null)

    val searchResponse by produceState<SearchResponse?>(null, queryState) {
        value = queryState?.let { query ->
            try {
                client.search(query)
            } catch (e: Throwable) {
                null
            }
        }
    }

    Form(null, {
        onSubmit { it.preventDefault() }
        classes("d-flex", "flex-row", "mx-4", "p-1", "rounded-pill")
        style {
            width(320.px)
            maxWidth(320.px)
            backgroundColor(if (focused.value) Color.white else hsla(0, 0, 100, .08))
            property("transition", "background-color .2s")
        }
    }) {
        val formRef by rememberDomElement()
        I({
            classes("bi", "bi-search", "p-1")
            style {
                if (focused.value) {
                    color(rgba(0, 0, 0, .8))
                }
                property("transition", "color .2s")
            }
        })
        SearchInput {
            ref { newRef ->
                inputRef.value = newRef
                onDispose {
                    inputRef.value = null
                }
            }
            value(elementValue.orEmpty())
            onFocusIn {
                focused.value = true
                searchQuery.value = (it.target as? HTMLInputElement)
                    ?.value
                    ?.takeUnless(String::isNullOrBlank)
            }
            onFocusOut {
                focused.value = false
            }
            onInput { event ->
                searchQuery.value = event.value.takeUnless(String::isNullOrBlank)
                elementValue = event.value
            }
            classes("w-100")
            style {
                backgroundColor(Color.transparent)
                outline("0")
                property("border", 0)
                property("transition", "color .2s")
                if (focused.value) {
                    color(rgba(0, 0, 0, .8))
                } else {
                    color(Color.white)
                }
            }
        }
        I({
            classes("bi", "bi-x-circle-fill", "p-1")
            onClick {
                inputRef.value?.run {
                    value = ""
                    elementValue = null
                    focus()
                }
            }
            style {
                property("transition", "color .2s")
                if (focused.value) {
                    color(rgba(0, 0, 0, .8))
                }
                if (elementValue.isNullOrBlank()) {
                    opacity(0)
                } else {
                    cursor("pointer")
                }
            }
        })
        if (isDisplayingSearch.value) {
            searchResponse?.also { response ->
                formRef?.also { element ->
                    SearchResultPopper(
                        formRef = element,
                        focused = focused,
                        response = response,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultPopper(
    formRef: HTMLElement,
    focused: MutableState<Boolean>,
    response: SearchResponse,
) {
    var globalClickHandler by remember { mutableStateOf<ExternalClickMask?>(null) }
    PopperElement(
        formRef,
        popperOptions(placement = "bottom"),
        attrs = {
            style {
                property("z-index", 100)
            }
        }
    ) {
        DisposableEffect(Unit) {
            globalClickHandler = ExternalClickMask(scopeElement) { remove ->
                // Hide search only if we're also unfocusing input
                if (!focused.value) {
                    searchQuery.value = null
                    remove()
                }
            }
            globalClickHandler?.attachListener()
            onDispose {
                globalClickHandler?.dispose()
                globalClickHandler = null
            }
        }
        SearchResultsList(response)
    }
}
