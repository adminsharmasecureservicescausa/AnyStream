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
package anystream.android.util

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kt.mobius.Connection
import kt.mobius.MobiusLoop
import kt.mobius.extras.QueuedConsumer
import kt.mobius.functions.Consumer

@Composable
fun <M, E> createLoopController(
    loopBuilder: () -> MobiusLoop.Controller<M, E>
): Pair<MutableState<M>, MutableState<Consumer<E>>> {
    val controller = remember { loopBuilder() }
    val modelState = remember { mutableStateOf(controller.model) }
    val eventConsumer = remember { mutableStateOf<Consumer<E>>(QueuedConsumer()) }

    val currentContext = LocalContext.current
    val activity = remember(currentContext) {
        val activity = (currentContext as? ComponentActivity)
            ?: ((currentContext as? ContextWrapper)?.baseContext as? ComponentActivity)
        checkNotNull(activity)
    }
    DisposableEffect(controller) {
        controller.connect { output ->
            (eventConsumer.value as? QueuedConsumer<E>)?.dequeueAll(output)
            eventConsumer.value = output
            object : Connection<M> {
                override fun accept(value: M) {
                    modelState.value = value
                }

                override fun dispose() {
                    eventConsumer.value = QueuedConsumer()
                }
            }
        }
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (!controller.isRunning) controller.start()
                modelState.value = controller.model
            }

            override fun onPause(owner: LifecycleOwner) {
                if (controller.isRunning) controller.stop()
            }
        }
        activity.lifecycle.addObserver(observer)

        onDispose {
            activity.lifecycle.removeObserver(observer)
            if (controller.isRunning) {
                controller.stop()
            }
            controller.disconnect()
        }
    }
    return Pair(modelState, eventConsumer)
}