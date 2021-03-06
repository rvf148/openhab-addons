/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.deconz.internal.netutils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.deconz.internal.dto.DeconzBaseMessage;
import org.openhab.binding.deconz.internal.dto.LightMessage;
import org.openhab.binding.deconz.internal.dto.SensorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Establishes and keeps a websocket connection to the deCONZ software.
 *
 * The connection is closed by deCONZ now and then and needs to be re-established.
 *
 * @author David Graeff - Initial contribution
 */
@WebSocket
@NonNullByDefault
public class WebSocketConnection {
    private final Logger logger = LoggerFactory.getLogger(WebSocketConnection.class);

    private final WebSocketClient client;
    private final WebSocketConnectionListener connectionListener;
    private final Map<String, WebSocketMessageListener> sensorListener = new HashMap<>();
    private final Map<String, WebSocketMessageListener> lightListener = new HashMap<>();
    private final Gson gson;
    private boolean connected = false;

    public WebSocketConnection(WebSocketConnectionListener listener, WebSocketClient client, Gson gson) {
        this.connectionListener = listener;
        this.client = client;
        this.client.setMaxIdleTimeout(0);
        this.gson = gson;
    }

    public void start(String ip) {
        if (connected) {
            return;
        }
        try {
            URI destUri = URI.create("ws://" + ip);

            client.start();

            logger.debug("Connecting to: {}", destUri);
            client.connect(this, destUri).get();
        } catch (Exception e) {
            connectionListener.connectionError(e);
        }
    }

    public void close() {
        try {
            connected = false;
            client.stop();
        } catch (Exception e) {
            logger.debug("Error while closing connection", e);
        }
        client.destroy();
    }

    public void registerSensorListener(String sensorID, WebSocketMessageListener listener) {
        sensorListener.put(sensorID, listener);
    }

    public void unregisterSensorListener(String sensorID) {
        sensorListener.remove(sensorID);
    }

    public void registerLightListener(String lightID, WebSocketMessageListener listener) {
        lightListener.put(lightID, listener);
    }

    public void unregisterLightListener(String lightID) {
        sensorListener.remove(lightID);
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        connected = true;
        logger.debug("Connect: {}", session.getRemoteAddress().getAddress());
        connectionListener.connectionEstablished();
    }

    @SuppressWarnings("null")
    @OnWebSocketMessage
    public void onMessage(String message) {
        DeconzBaseMessage changedMessage = gson.fromJson(message, DeconzBaseMessage.class);
        switch (changedMessage.r) {
            case "sensors":
                WebSocketMessageListener listener = sensorListener.get(changedMessage.id);
                if (listener != null) {
                    listener.messageReceived(changedMessage.id, gson.fromJson(message, SensorMessage.class));
                }
                break;
            case "lights":
                listener = lightListener.get(changedMessage.id);
                if (listener != null) {
                    listener.messageReceived(changedMessage.id, gson.fromJson(message, LightMessage.class));
                }
                break;
            default:
        }
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        connected = false;
        connectionListener.connectionError(cause);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        connected = false;
        connectionListener.connectionLost(reason);
    }

    public boolean isConnected() {
        return connected;
    }
}
