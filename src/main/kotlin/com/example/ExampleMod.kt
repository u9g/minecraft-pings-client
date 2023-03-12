package com.example

import com.eclipsesource.json.Json
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketFactory
import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.client.ClientCommandHandler
import net.minecraftforge.client.event.RenderWorldLastEvent
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
import kotlin.math.floor

lateinit var webSocket: WebSocket
var isOnline = false

val pingKey = KeyBinding("Ping!", Keyboard.KEY_F, "Pings")
val replayKey = KeyBinding("Replay Last Ping", Keyboard.KEY_Y, "Pings")

data class Ping(val username: String, val pos: Triple<Double, Double, Double>) {
    val madeTime = System.currentTimeMillis()
}

val pings = mutableListOf<Ping>()
val toAdd = mutableListOf<Ping>()
val places = mutableMapOf("vicious" to { Ping("Vicious", Triple(333.0,100.0,370.0))},
                          "trunk" to { Ping("Trunk", Triple(777.0,100.0,267.0))},
                          "sniper" to { Ping("Sniper", Triple(173.0,100.0,801.0))},)

var lastRemoved: Ping? = null

@Mod(modid = "examplemod", version = "1.0.0")
class ExampleMod {
    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        webSocket = WebSocketFactory()
                        .createSocket("ws://u9g.jprq.live")
                        .addListener(object : WebSocketAdapter() {

                            override fun onTextMessage(ws: WebSocket, message: String) {
                                try {
                                    println("received message: $message")
                                    if (isOnline) {
                                        val parsed = Json.parse(message).asObject()
                                        val username = parsed["username"].asString()
                                        val x = parsed["x"].asDouble()
                                        val y = parsed["y"].asDouble()
                                        val z = parsed["z"].asDouble()
                                        Minecraft.getMinecraft().ingameGUI.chatGUI.printChatMessage(TextComponentString(
                                                "$username pinged at (${floor(x)},${floor(y)},${floor(z)})"))
                                        toAdd.add(Ping(username, Triple(x, y, z)))
                                    }
                                } catch (_: Exception) {}
                            }
                        })
                        .connect()
        MinecraftForge.EVENT_BUS.register(this)
        ClientRegistry.registerKeyBinding(pingKey)
        ClientCommandHandler.instance.registerCommand(object : CommandBase() {
            override fun getName() = "waypoint"

            override fun getUsage(sender: ICommandSender) = ""

            override fun execute(server: MinecraftServer?, sender: ICommandSender, args: Array<out String>) {
                if (args.size == 1) {
                    places[args[0]]?.invoke()?.let { toAdd.add(it) }
                } else if (args.size == 3) {
                    toAdd.add(Ping("waypointed", Triple(args[0].toDouble(), args[1].toDouble(), args[2].toDouble())))
                }
            }

            override fun checkPermission(server: MinecraftServer?, sender: ICommandSender) = true

            override fun getTabCompletions(
                server: MinecraftServer?,
                sender: ICommandSender,
                args: Array<out String>,
                targetPos: BlockPos?
            ): MutableList<String> {
                return places.keys.toMutableList().filter { it.startsWith(args[0] ?? "") }.toMutableList()
            }
        })
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
    fun renderWorldLast(event: RenderWorldLastEvent) {
        pings.addAll(toAdd)
        toAdd.clear()
        val iter = pings.iterator()

        while (iter.hasNext()) {
            val ping = iter.next()
            if (System.currentTimeMillis() - ping.madeTime > 1 * 60 * 1000) {
                lastRemoved = ping;
                iter.remove()
            }
            BeaconManager.drawBeam(ping.pos, 0xFF0000, event.partialTicks)
            val (x, y, z) = ping.pos
            NametagManager.renderWaypointName(ping.username, x, y, z)
        }
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
            val block = Minecraft.getMinecraft().player
            webSocket.sendText(jsonOf(
                    "type" to "ping",
                    "x" to block.posX,
                    "y" to block.posY,
                    "z" to block.posZ))
        } else if (replayKey.isPressed && isOnline && lastRemoved != null) {
            toAdd.add(Ping(lastRemoved!!.username, lastRemoved!!.pos))
        }
    }
}

fun jsonOf(vararg pairs: Pair<String, *>) =
    (mapOf(*pairs) to Json.`object`())
            .also { (pairs, json) -> pairs.forEach { k ->
                when (val value = k.value) {
                    is String -> json.add(k.key, value)
                    is Int -> json.add(k.key, value)
                    is Float -> json.add(k.key, value)
                    is Double -> json.add(k.key, value)
                    else -> throw RuntimeException("Unable to serialize $value")
                }
            }}.second.toString()
