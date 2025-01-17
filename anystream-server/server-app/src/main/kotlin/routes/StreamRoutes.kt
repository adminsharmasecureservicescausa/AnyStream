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
package anystream.routes

import anystream.data.UserSession
import anystream.json
import anystream.models.*
import anystream.models.api.PlaybackSessionsResponse
import anystream.service.stream.StreamService
import anystream.util.extractUserSession
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.drewcarlson.ktor.permissions.withPermission
import java.io.File
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private const val PLAYBACK_COMPLETE_PERCENT = 90

fun Route.addStreamRoutes(
    streamService: StreamService,
) {
    route("/stream") {
        authenticate {
            withPermission(Permission.ConfigureSystem) {
                get {
                    call.respond(streamService.getPlaybackSessions())
                }
            }
        }

        route("/{mediaRefId}") {
            authenticate {
                route("/state") {
                    get {
                        val session = call.principal<UserSession>()!!
                        val mediaRefId = call.parameters["mediaRefId"]!!
                        val playbackState =
                            streamService.getPlaybackState(mediaRefId, session.userId, false)
                        if (playbackState == null) {
                            call.respond(NotFound)
                        } else {
                            call.respond(playbackState)
                        }
                    }
                    put {
                        val session = call.principal<UserSession>()!!
                        val mediaRefId = call.parameters["mediaRefId"]!!
                        val state = call.receiveOrNull<PlaybackState>()
                            ?: return@put call.respond(UnprocessableEntity)

                        val actualState = streamService.getPlaybackState(mediaRefId, session.userId, false)

                        if (actualState == null) {
                            call.respond(NotFound)
                        } else {
                            val success = streamService.updateStatePosition(actualState.id, state.position)
                            call.respond(if (success) OK else InternalServerError)
                        }
                    }
                }
            }

            route("/hls") {
                get("/playlist.m3u8") {
                    val mediaRefId = call.parameters["mediaRefId"]!!
                    val token = call.parameters["token"]
                        ?: return@get call.respond(Unauthorized)
                    val playlist = streamService.getPlaylist(mediaRefId, token)
                    if (playlist == null) {
                        call.respond(NotFound)
                    } else {
                        call.respond(playlist)
                    }
                }

                get("/{segmentFile}") {
                    val segmentFile = call.parameters["segmentFile"]
                        ?: return@get call.respond(NotFound)
                    val token = call.request.queryParameters["token"]
                        ?: return@get call.respond(Unauthorized)

                    val filePath = streamService.getFilePathForSegment(token, segmentFile)
                    if (filePath == null) {
                        call.respond(NotFound)
                    } else {
                        call.respond(LocalFileContent(File(filePath), ContentType.Application.OctetStream))
                    }
                }
            }
        }

        get("/stop/{token}") {
            val token = call.parameters["token"]!!
            val delete = call.parameters["delete"]?.toBoolean() ?: false
            streamService.stopSession(token, delete)
            call.respond(OK)
        }
    }
}

fun Route.addStreamWsRoutes(
    streamService: StreamService,
) {
    val sessionsFlow = flow {
        var previousSessions: PlaybackSessionsResponse? = null
        while (true) {
            val nextSessions = streamService.getPlaybackSessions()
            if (previousSessions != nextSessions) {
                previousSessions = nextSessions
                emit(nextSessions)
            }
            delay(2.seconds)
        }
    }.shareIn(application, SharingStarted.WhileSubscribed(), 1)

    webSocket("/ws/stream") {
        val session = checkNotNull(extractUserSession())
        check(Permission.check(Permission.ConfigureSystem, session.permissions))
        sessionsFlow.collect { response -> sendSerialized(response) }
    }

    webSocket("/ws/stream/{mediaRefId}/state") {
        val session = checkNotNull(extractUserSession())
        check(Permission.check(Permission.ConfigureSystem, session.permissions))
        val userId = session.userId
        val mediaRefId = call.parameters["mediaRefId"]!!

        val state = streamService.getPlaybackState(mediaRefId, userId, create = true)
            ?: return@webSocket close()

        send(Frame.Text(json.encodeToString(state)))

        val finalPosition = incoming.receiveAsFlow()
            .takeWhile { it !is Frame.Close }
            .filterIsInstance<Frame.Text>()
            .map { frame ->
                val newState = json.decodeFromString<PlaybackState>(frame.readText())
                streamService.updateStatePosition(state.id, newState.position)
                newState.position
            }
            .lastOrNull() ?: state.position

        val completePercent = ((finalPosition / state.runtime) * 100).roundToInt()
        val isComplete = completePercent >= PLAYBACK_COMPLETE_PERCENT
        if (isComplete) {
            streamService.deletePlaybackState(state.id)
        }
        streamService.stopSession(state.id, isComplete)
    }
}
