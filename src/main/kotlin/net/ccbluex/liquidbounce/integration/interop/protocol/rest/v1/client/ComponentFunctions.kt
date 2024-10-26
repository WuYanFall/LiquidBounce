/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2024 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.blaze3d.systems.RenderSystem
import io.netty.handler.codec.http.FullHttpResponse
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.integration.interop.protocol.genericProtocolGson
import net.ccbluex.liquidbounce.integration.interop.protocol.protocolGson
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.integration.theme.ThemeManager.activeComponents
import net.ccbluex.liquidbounce.integration.theme.component.ComponentOverlay
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.render.Alignment
import net.ccbluex.netty.http.model.RequestObject
import net.ccbluex.netty.http.util.httpBadRequest
import net.ccbluex.netty.http.util.httpOk
import java.io.StringReader
import java.util.*

// GET /api/v1/client/componentFactories
@Suppress("UNUSED_PARAMETER")
fun getComponentFactories(requestObject: RequestObject): FullHttpResponse {
    return httpOk(JsonArray().apply {
        ThemeManager.availableThemes.forEach { theme ->
            add(JsonObject().apply {
                addProperty("name", theme.name)

                add("components", JsonArray().apply {
                    for (component in theme.components) {
                        add(JsonPrimitive(component.name))
                    }
                })
            })
        }
    })
}

// GET /api/v1/client/components
@Suppress("UNUSED_PARAMETER")
fun getAllComponents(requestObject: RequestObject) = httpOk(JsonArray().apply {
    for (component in activeComponents) {
        add(protocolGson.toJsonTree(component))
    }
})

// GET /api/v1/client/components/:name
fun getComponents(requestObject: RequestObject): FullHttpResponse {
    val name = requestObject.params["name"] ?: return httpBadRequest("No name provided")
    val components = activeComponents.filter { theme -> theme.theme.name.equals(name, true) }

    return httpOk(JsonArray().apply {
        for (component in components) {
            add(protocolGson.toJsonTree(component))
        }
    })
}

// GET /api/v1/client/component/:id
fun getComponentSettings(requestObject: RequestObject): FullHttpResponse {
    val id = requestObject.params["id"]?.let { UUID.fromString(it) }
        ?: return httpBadRequest("No ID provided")

    val component = activeComponents
        .find { it.id == id } ?: return httpBadRequest("No component found")
    val json = ConfigSystem.serializeConfigurable(component, gson = genericProtocolGson)

    ComponentOverlay.fireComponentsUpdate()

    return httpOk(json)
}

// PUT /api/v1/client/component/:id
fun updateComponentSettings(requestObject: RequestObject): FullHttpResponse {
    val id = requestObject.params["id"]?.let { UUID.fromString(it) }
        ?: return httpBadRequest("No ID provided")

    val component = activeComponents
        .find { it.id == id } ?: return httpBadRequest("No component found")
    ConfigSystem.deserializeConfigurable(component, StringReader(requestObject.body), gson = protocolGson)

    ComponentOverlay.fireComponentsUpdate()

    return httpOk(JsonObject())
}

// PATCH /api/v1/client/component/:id
fun moveComponent(requestObject: RequestObject): FullHttpResponse {
    val id = requestObject.params["id"]?.let { UUID.fromString(it) }
        ?: return httpBadRequest("No ID provided")

    val component = activeComponents
        .find { it.id == id } ?: return httpBadRequest("No component found")

    // We copy the alignment to the existing because we do not want to replace the instance
    val newAlignment = protocolGson.fromJson(requestObject.body, Alignment::class.java)
    component.alignment = newAlignment

    ComponentOverlay.fireComponentsUpdate()

    return httpOk(JsonObject())
}

// POST /api/v1/client/components/:name/:componentName
fun createComponent(requestObject: RequestObject): FullHttpResponse {
    val name = requestObject.params["name"]
    val componentName = requestObject.params["componentName"]

    val theme = ThemeManager.availableThemes.find { it.name == name }
        ?: return httpBadRequest("No theme found")
    val componentFactory = theme.components.find { it.name == componentName }
        ?: return httpBadRequest("No component found")

    RenderSystem.recordRenderCall {
        runCatching {
            val component = componentFactory.new(theme)
            activeComponents += component
            ComponentOverlay.update()
            ComponentOverlay.fireComponentsUpdate()
        }.onFailure {
            logger.error("Failed to create component", it)
        }
    }
    return httpOk(JsonObject())
}

// DELETE /api/v1/client/components/:id
fun deleteComponent(requestObject: RequestObject): FullHttpResponse {
    val id = requestObject.params["id"]?.let { UUID.fromString(it) }
        ?: return httpBadRequest("No ID provided")

    RenderSystem.recordRenderCall {
        runCatching {
            activeComponents.removeIf { it.id == id }
            ComponentOverlay.update()
            ComponentOverlay.fireComponentsUpdate()
        }.onFailure {
            logger.error("Failed to delete component", it)
        }
    }
    return httpOk(JsonObject())
}
