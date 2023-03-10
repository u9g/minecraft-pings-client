package com.example

import com.eclipsesource.json.Json
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketFactory
import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraft.util.ChatComponentText
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent
import org.lwjgl.input.Keyboard
import java.net.InetSocketAddress

lateinit var webSocket: WebSocket
var isOnline = false

val pingKey = KeyBinding("Ping!", Keyboard.KEY_F, "Pings")

@Mod(modid = "examplemod", version = "1.0.0")
class ExampleMod {
    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        webSocket = WebSocketFactory()
                        .createSocket("ws://localhost:3000")
                        .addListener(object : WebSocketAdapter() {

                            override fun onTextMessage(ws: WebSocket, message: String) {
                                try {
                                    println("received message: $message")
                                    if (isOnline) {
                                        val parsed = Json.parse(message).asObject()
                                        val username = parsed["username"].asString()
                                        val x = parsed["x"].asInt()
                                        val y = parsed["y"].asInt()
                                        val z = parsed["z"].asInt()
                                        Minecraft.getMinecraft().ingameGUI.chatGUI.printChatMessage(ChatComponentText(
                                                "$username pinged at ($x,$y,$z)"))
                                    }
                                } catch (e: Exception) {}
                            }
                        })
                        .connect()
        MinecraftForge.EVENT_BUS.register(this)
        ClientRegistry.registerKeyBinding(pingKey)
    }

    @SubscribeEvent
    fun joinServer(event: ClientConnectedToServerEvent) {
        println("connected to server")
        if (event.isLocal) return
        webSocket.sendText(jsonOf(
                "type" to "connected",
                "username" to Minecraft.getMinecraft().session.username,
                "ip" to (event.manager.remoteAddress as InetSocketAddress).hostName
        ))
        isOnline = true
    }

    @SubscribeEvent
    fun leaveServer(event: ClientDisconnectionFromServerEvent) {
        println("disconnected from server")
        if (event.manager.isLocalChannel) return
        webSocket.sendText(jsonOf("type" to "disconnected"))
        isOnline = false
    }

    @SubscribeEvent
    fun tick(event: TickEvent) {
        if (pingKey.isPressed && isOnline) {
            val block = Minecraft.getMinecraft().thePlayer.position
            webSocket.sendText(jsonOf(
                    "type" to "ping",
                    "x" to block.x,
                    "y" to block.y,
                    "z" to block.z))
        }
    }
}

fun jsonOf(vararg pairs: Pair<String, *>) =
    (mapOf(*pairs) to Json.`object`())
            .also { (pairs, json) -> pairs.forEach { k ->
                when (val value = k.value) {
                    is String -> json.add(k.key, value)
                    is Int -> json.add(k.key, value)
                    else -> throw RuntimeException("Unable to serialize $value")
                }
            }}.second.toString()