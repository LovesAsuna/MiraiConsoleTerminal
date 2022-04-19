/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress(
    "INVISIBLE_MEMBER",
    "INVISIBLE_REFERENCE",
    "CANNOT_OVERRIDE_INVISIBLE_MEMBER",
    "INVISIBLE_SETTER",
    "INVISIBLE_GETTER",
    "INVISIBLE_ABSTRACT_MEMBER_FROM_SUPER",
    "INVISIBLE_ABSTRACT_MEMBER_FROM_SUPER_WARNING",
    "EXPOSED_SUPER_CLASS"
)
@file:OptIn(ConsoleInternalApi::class, ConsoleFrontEndImplementation::class, ConsoleTerminalExperimentalApi::class)

package com.hyosakura.terminal


import kotlinx.coroutines.*
import net.mamoe.mirai.console.ConsoleFrontEndImplementation
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.MiraiConsoleFrontEndDescription
import net.mamoe.mirai.console.MiraiConsoleImplementation
import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.data.MultiFilePluginDataStorage
import net.mamoe.mirai.console.data.PluginDataStorage
import net.mamoe.mirai.console.plugin.jvm.JvmPluginLoader
import net.mamoe.mirai.console.plugin.loader.PluginLoader
import com.hyosakura.terminal.ConsoleInputImpl.requestInput
import com.hyosakura.terminal.noconsole.AllEmptyLineReader
import com.hyosakura.terminal.noconsole.NoConsole
import net.mamoe.mirai.console.internal.util.semver.RequirementParser.Token.Begin.line
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.console.util.ConsoleInput
import net.mamoe.mirai.console.util.ConsoleInternalApi
import net.mamoe.mirai.console.util.SemVersion
import net.mamoe.mirai.utils.*
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginAttribute
import org.apache.logging.log4j.core.config.plugins.PluginElement
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import org.apache.logging.log4j.core.layout.PatternLayout
import org.fusesource.jansi.Ansi
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.completer.NullCompleter
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.terminal.impl.AbstractWindowsTerminal
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths

/**
 * mirai-console-terminal 后端实现
 *
 * @see MiraiConsoleTerminalLoader CLI 入口点
 */
@ConsoleExperimentalApi
open class MiraiConsoleImplementationTerminal
@JvmOverloads constructor(
    final override val rootPath: Path = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath(),
    override val builtInPluginLoaders: List<Lazy<PluginLoader<*, *>>> = listOf(lazy { JvmPluginLoader }),
    override val frontEndDescription: MiraiConsoleFrontEndDescription = ConsoleFrontEndDescImpl,
    override val consoleCommandSender: MiraiConsoleImplementation.ConsoleCommandSenderImpl = ConsoleCommandSenderImplTerminal,
    override val dataStorageForJvmPluginLoader: PluginDataStorage = MultiFilePluginDataStorage(rootPath.resolve("data")),
    override val dataStorageForBuiltIns: PluginDataStorage = MultiFilePluginDataStorage(rootPath.resolve("data")),
    override val configStorageForJvmPluginLoader: PluginDataStorage = MultiFilePluginDataStorage(rootPath.resolve("config")),
    override val configStorageForBuiltIns: PluginDataStorage = MultiFilePluginDataStorage(rootPath.resolve("config")),
) : MiraiConsoleImplementation, CoroutineScope by CoroutineScope(
    SupervisorJob() + CoroutineName("MiraiConsoleImplementationTerminal") +
            CoroutineExceptionHandler { coroutineContext, throwable ->
                if (throwable is CancellationException) {
                    return@CoroutineExceptionHandler
                }
                val coroutineName = coroutineContext[CoroutineName]?.name ?: "<unnamed>"
                MiraiConsole.mainLogger.error("Exception in coroutine $coroutineName", throwable)
            }) {
    override val jvmPluginLoader: JvmPluginLoader by lazy { backendAccess.createDefaultJvmPluginLoader(coroutineContext) }
    override val commandManager: CommandManager by lazy { backendAccess.createDefaultCommandManager(coroutineContext) }
    override val consoleInput: ConsoleInput get() = ConsoleInputImpl
    override val isAnsiSupported: Boolean get() = true
    override val consoleDataScope: MiraiConsoleImplementation.ConsoleDataScope by lazy {
        MiraiConsoleImplementation.ConsoleDataScope.createDefault(
            coroutineContext,
            dataStorageForBuiltIns,
            configStorageForBuiltIns
        )
    }
    // used in test
    internal val logService: LoggingService

    override fun createLoginSolver(requesterBot: Long, configuration: BotConfiguration): LoginSolver {
        LoginSolver.Default?.takeIf { it !is StandardCharImageLoginSolver }?.let { return it }
        return StandardCharImageLoginSolver(input = { requestInput("LOGIN> ") })
    }

    @OptIn(MiraiInternalApi::class)
    override fun createLogger(identity: String?): MiraiLogger = LoggerCreator(identity, logService)

    init {
        with(rootPath.toFile()) {
            mkdir()
            require(isDirectory) { "rootDir $absolutePath is not a directory" }
            logService = if (ConsoleTerminalSettings.noLogging) {
                LoggingServiceNoop()
            } else {
                LoggingServiceI(childScope("Log Service")).also { service ->
                    service.startup(resolve("logs"))
                }
            }
        }
    }

    override val consoleLaunchOptions: MiraiConsoleImplementation.ConsoleLaunchOptions
        get() = ConsoleTerminalSettings.launchOptions

    override fun preStart() {
        overrideSTD(this)
    }
}

val lineReader: LineReader by lazy {
    val terminal = terminal
    if (terminal is NoConsole) return@lazy AllEmptyLineReader

    LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(NullCompleter())
        .build()
}

val terminal: Terminal = run {
    if (ConsoleTerminalSettings.noConsole) return@run NoConsole

    TerminalBuilder.builder()
        .name("Mirai Console")
        .system(true)
        .jansi(true)
        .dumb(true)
        .paused(true)
        .build()
        .let { terminal ->
            if (terminal is AbstractWindowsTerminal) {
                val pumpField = runCatching {
                    AbstractWindowsTerminal::class.java.getDeclaredField("pump").also {
                        it.isAccessible = true
                    }
                }.onFailure { err ->
                    err.printStackTrace()
                    return@let terminal.also { it.resume() }
                }.getOrThrow()
                var response = terminal
                terminal.setOnClose {
                    response = NoConsole
                }
                terminal.resume()
                val pumpThread = pumpField[terminal] as? Thread ?: return@let NoConsole
                @Suppress("ControlFlowWithEmptyBody")
                while (pumpThread.state == Thread.State.NEW);
                Thread.sleep(1000)
                terminal.setOnClose(null)
                return@let response
            }
            terminal.resume()
            terminal
        }
}

private object ConsoleFrontEndDescImpl : MiraiConsoleFrontEndDescription {
    override val name: String get() = "Terminal"
    override val vendor: String get() = "Mamoe Technologies"

    // net.mamoe.mirai.console.internal.MiraiConsoleBuildConstants.version
    // is console's version not frontend's version
    override val version: SemVersion =
        SemVersion(net.mamoe.mirai.console.internal.MiraiConsoleBuildConstants.versionConst)
}

internal val ANSI_RESET = Ansi().reset().toString()

@OptIn(MiraiInternalApi::class)
internal val LoggerCreator: (identity: String?, logService: LoggingService) -> MiraiLogger = { identity, logService ->
    object : MiraiLoggerPlatformBase() {
        override val identity: String? = identity
        private val logger = LoggerFactory.getLogger(identity)

        override fun debug0(message: String?, e: Throwable?) {
            logger.debug(message, e)
            logService.pushLine(message!!)
        }

        override fun error0(message: String?, e: Throwable?) {
            logger.error(message, e)
            logService.pushLine(message!!)
        }

        override fun info0(message: String?, e: Throwable?) {
            logger.info(message, e)
            logService.pushLine(message!!)
        }

        override fun verbose0(message: String?, e: Throwable?) {
            logger.trace(message, e)
            logService.pushLine(message!!)
        }

        override fun warning0(message: String?, e: Throwable?) {
            logger.warn(message, e)
            logService.pushLine(message!!)
        }

    }
}

@Plugin(name = "JLine", category = "Core", elementType = "appender", printObject = false)
class JLineAppender(
    name: String,
    private val exclusive: String?,
    filter: Filter?,
    layout: Layout<out Serializable>?,
    ignoreException: Boolean,
    properties: Array<Property>
) : AbstractAppender(name, filter, layout, ignoreException, properties) {
    override fun append(event: LogEvent) {
        exclusive?.let {
            if (Regex(it).matches(event.loggerName)) return
        }
        val text = layout.toSerializable(event).toString() + ANSI_RESET
        lineReader.printAbove(text)
    }

    companion object {
        @PluginFactory
        @JvmStatic
        fun createAppender(
            @PluginAttribute("name") name: String,
            @PluginAttribute("ignoreExceptions") ignoreExceptions: Boolean,
            @PluginAttribute("exclusive") exclusive: String?,
            @PluginElement("Layout") layout: Layout<out Serializable>? = PatternLayout.createDefaultLayout(),
            @PluginElement("Filters") filter: Filter?
        ): JLineAppender = JLineAppender(name, exclusive,filter, layout, ignoreExceptions, Property.EMPTY_ARRAY)
    }
}