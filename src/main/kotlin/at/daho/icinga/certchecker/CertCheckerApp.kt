/*
 * Cert-Checker - An Icinga plugin to check for certificates about to expire.
 * Copyright (c) 2024.  Daniel Hofer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package at.daho.icinga.certchecker

import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain
import jakarta.inject.Inject
import picocli.CommandLine
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Callable

@QuarkusMain
@CommandLine.Command(footer = ["cert-checker  Copyright (C) 2024  Daniel Hofer\n" +
        "This program comes with ABSOLUTELY NO WARRANTY.\n" +
        "This is free software, and you are welcome to redistribute it\n" +
        "under the conditions of GPLv3, see <https://github.com/dahoat/cert-checker> for details."]
)
class CertCheckerApp: Callable<Int>, QuarkusApplication {

    companion object {
        private val inputTimestampFormat = DateTimeFormatter.ofPattern("MMM ppd HH:mm:ss yyyy z").withLocale(Locale.ROOT)
        private val outputTimestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withLocale(Locale.ROOT)
        private const val OK = 0
        private const val WARNING = 1
        private const val ERROR = 2
        private const val UNKNOWN = 3
    }


    @Inject
    lateinit var factory: CommandLine.IFactory

    @CommandLine.Option(names = ["--host"], description = ["Host to contact"], required = true)
    var host: String = ""

    @CommandLine.Option(names = ["--port"], description = ["Port to use"])
    var port: Int = 443

    @CommandLine.Option(names = ["--subject"], description = ["Subject to expect, host is used if not specified"])
    var expectedSubject: String? = null

    @CommandLine.Option(names = ["--starttls"], description = ["Specify the StartTLS mode to use."])
    var startTlsMode: String? = null

    @CommandLine.Option(names = ["--expire-days-warning"], description = ["Number of days left for warning state."], defaultValue = "30")
    var daysWarning: Long = 30

    @CommandLine.Option(names = ["--expire-days-error"], description = ["Number of days left for error state."], defaultValue = "15")
    var daysError: Long = 15

    override fun call(): Int? {
        var checkStatus = OK

        try {
            val openSSLOutput = getOpenSSLOutput()
            if (openSSLOutput.isBlank()) {
                println("Got no output from openSSL, maybe could not connect.")
                return 3
            }

            val expirationDate = getNotAfter(openSSLOutput)
            val daysLeft = daysToExpiry(expirationDate)
            if (daysLeft < daysWarning) {
                checkStatus = WARNING
            } else if (daysLeft < daysError) {
                checkStatus = ERROR
            }
            if (checkStatus != OK) {
                println("Certificate expires on ${expirationDate.format(outputTimestampFormat)} which is in $daysLeft days.")
            }


            val subject = getSubject(openSSLOutput)
            expectedSubject = expectedSubject ?: host
            if (subject != expectedSubject) {
                println("Expected subject $host but certificate for $subject.")
                checkStatus = ERROR
            }

            if (checkStatus == OK) {
                val startTlsComment = if (startTlsMode != null) {
                    "(StartTLS $startTlsMode)"
                } else {
                    ""
                }
                println("Certificate for $host:$port $startTlsComment OK: $daysLeft until expiry (${expirationDate.format(outputTimestampFormat)}) and subject is $subject.")
            }
            return checkStatus
        } catch (e: Exception) {
            println("Could not validate certificate: " + e.message)
            return UNKNOWN
        }
    }

    private fun daysToExpiry(expirationDate: ZonedDateTime): Long {
        return ChronoUnit.DAYS.between(ZonedDateTime.now(expirationDate.zone), expirationDate)
    }

    private fun getOpenSSLOutput(): String {
        val commands = mutableListOf<String>("openssl", "s_client",  "-connect", "$host:$port")
        startTlsMode?.isNotBlank()?.let {
            commands.add("-starttls")
            commands.add(startTlsMode!!)
        }

        val processBuilder = ProcessBuilder()
        processBuilder.command(commands)
        val process = processBuilder.start()
        process.outputStream.close()
        process.waitFor()
        return process.inputStream.bufferedReader().readText()
    }

    private fun getNotAfter(connectionOutput: String): ZonedDateTime {
        val commands = "openssl x509 -noout -dates".split(" ")
        val response = callOpenSSL(commands, connectionOutput)
        val rawDate = "(?<=notAfter=)(.*)".toRegex().find(response)?.value ?: throw IllegalArgumentException("Could not parse expiration date: $response")
        return ZonedDateTime.parse(rawDate, inputTimestampFormat)
    }

    private fun callOpenSSL(commands: List<String>, input: String): String {
        val processBuilder = ProcessBuilder()
        processBuilder.command(commands)
        val process = processBuilder.start()
        process.outputStream.write(input.encodeToByteArray())
        process.outputStream.close()
        return process.inputStream.reader().readText()
    }

    private fun getSubject(connectionOutput: String): String {
        val commands = "openssl x509 -subject -noout".split(" ")
        val response = callOpenSSL(commands, connectionOutput).trim()
        return "subject=\\h*CN\\h*=\\h*(.*)".toRegex().find(response)?.groupValues[1] ?: throw IllegalArgumentException("Could not parse subject: $response")
    }

    override fun run(vararg args: String?): Int {
        return CommandLine(this, factory).execute(*args)
    }


}

