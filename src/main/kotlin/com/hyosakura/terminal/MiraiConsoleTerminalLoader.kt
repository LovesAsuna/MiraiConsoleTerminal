package com.hyosakura.terminal/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.ConsoleFrontEndImplementation
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.MiraiConsoleImplementation
import net.mamoe.mirai.console.MiraiConsoleImplementation.Companion.start
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.utils.MiraiLogger
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream

/**
 * mirai-console-com.hyosakura.terminal.getTerminal CLI 入口点
 */
object MiraiConsoleTerminalLoader {
    @OptIn(ConsoleExperimentalApi::class)
    @JvmStatic
    fun main(args: Array<String>) {
        startAsDaemon()
        try {
            runBlocking {
                MiraiConsole.job.join()
            }
        } catch (e: CancellationException) {
            // ignored
        }
    }

    @OptIn(ConsoleFrontEndImplementation::class)
    @Suppress("MemberVisibilityCanBePrivate")
    @ConsoleExperimentalApi
    fun startAsDaemon(instance: MiraiConsoleImplementationTerminal = MiraiConsoleImplementationTerminal()) {
        instance.start()
        overrideSTD()
        startupConsoleThread()
    }
}

internal fun overrideSTD() {
    System.setOut(
        PrintStream(
            BufferedOutputStream(
                logger = MiraiLogger.Factory.create(MiraiLogger::class, "stdout")
                    .run { ({ line: String? -> info(line) }) }
            ),
            false,
            "UTF-8"
        )
    )
    System.setErr(
        PrintStream(
            BufferedOutputStream(
                logger = MiraiLogger.Factory.create(MiraiLogger::class, "stderr")
                    .run { ({ line: String? -> warning(line) }) }
            ),
            false,
            "UTF-8"
        )
    )
}


@OptIn(ConsoleFrontEndImplementation::class)
internal object ConsoleCommandSenderImplTerminal : MiraiConsoleImplementation.ConsoleCommandSenderImpl {
    override suspend fun sendMessage(message: String) {
        kotlin.runCatching {
            lineReader.printAbove(message + ANSI_RESET)
        }.onFailure { exception ->
            // If failed. It means JLine Terminal not working...
            PrintStream(FileOutputStream(FileDescriptor.err)).use {
                it.println("Exception while com.hyosakura.terminal.ConsoleCommandSenderImplTerminal.sendMessage")
                exception.printStackTrace(it)
            }
        }
    }

    override suspend fun sendMessage(message: Message) {
        return sendMessage(message.toString())
    }
}