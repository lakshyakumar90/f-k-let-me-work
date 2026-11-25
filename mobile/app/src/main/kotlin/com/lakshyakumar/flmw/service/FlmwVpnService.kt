package com.lakshyakumar.flmw.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.VpnService
import android.os.CancellationSignal
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lakshyakumar.flmw.FlmwApplication
import com.lakshyakumar.flmw.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * Local on-device DNS-filtering VpnService (no root, no remote VPN server).
 * Intercepts UDP/53 DNS queries on the TUN interface, responds with NXDOMAIN
 * for blocked domains/subdomains, and resolves permitted queries on the device network.
 */
class FlmwVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var loopJob: Job? = null
    private var expiryJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        startVpnLoop()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, FlmwApplication.CHANNEL_FOCUS_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("flmw · Focus active")
            .setContentText("Blocking distracting websites and apps locally.")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startVpnLoop() {
        if (tunInterface != null) return

        val builder = Builder()
            .setSession("flmw Focus Filter")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_DNS, 32)
            .setBlocking(true)

        runCatching { builder.addDisallowedApplication(packageName) }

        val pfd = builder.establish() ?: return
        tunInterface = pfd

        val repo = (application as FlmwApplication).repository
        expiryJob = serviceScope.launch(Dispatchers.Main) {
            while (repo.focusState.value.isEffectivelyActive()) delay(1_000)
            stopVpn()
        }
        loopJob = serviceScope.launch {
            val requests = Semaphore(16)
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteArray(32767)

            while (isActive) {
                val len = runCatching { input.read(buffer) }.getOrElse { -1 }
                if (len <= 0) break

                val state = repo.focusState.value
                val packet = buffer.copyOf(len)
                requests.acquire()
                launch {
                    try {
                        handleIpPacket(packet, state.blockedSites, output)
                    } finally {
                        requests.release()
                    }
                }
            }
        }
    }

    private suspend fun handleIpPacket(
        packet: ByteArray,
        blockedSites: List<String>,
        output: FileOutputStream
    ) {
        if (packet.size < 28) return
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return

        val ihl = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17 || ihl < 20 || packet.size < ihl + 8) return // UDP only

        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return

        val dnsOffset = ihl + 8
        val dnsPayload = packet.copyOfRange(dnsOffset, packet.size)
        val domain = extractDnsDomain(dnsPayload)

        if (dnsPayload.size < 12) return
        val responseDns = if (domain != null && isBlocked(domain, blockedSites)) {
            buildErrorResponse(dnsPayload, 3)
        } else {
            forwardDnsQuery(dnsPayload) ?: buildErrorResponse(dnsPayload, 2)
        }

        val replyIp = buildUdpIpv4Reply(packet, ihl, responseDns)
        synchronized(output) { runCatching { output.write(replyIp) } }
    }

    private fun isBlocked(queryDomain: String, blockedSites: List<String>): Boolean {
        val q = queryDomain.lowercase().trimEnd('.')
        return blockedSites.any { site ->
            q == site || q.endsWith(".$site")
        }
    }

    private fun extractDnsDomain(dns: ByteArray): String? {
        if (dns.size < 13) return null
        val labels = mutableListOf<String>()
        var idx = 12
        while (idx < dns.size) {
            val length = dns[idx].toInt() and 0xFF
            if (length == 0) break
            if (idx + 1 + length > dns.size) return null
            labels.add(String(dns, idx + 1, length, Charsets.US_ASCII))
            idx += 1 + length
        }
        return if (labels.isEmpty()) null else labels.joinToString(".")
    }

    private fun buildErrorResponse(query: ByteArray, code: Int): ByteArray {
        // Keep the question and any EDNS record, preserving the query ID and RD flag.
        return query.copyOf().apply {
            this[2] = ((this[2].toInt() and 0x79) or 0x80).toByte()
            this[3] = (0x80 or code).toByte()
        }
    }

    private suspend fun forwardDnsQuery(query: ByteArray): ByteArray? {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        // This app is excluded from its own VPN, so its active network is the underlying one.
        val network = connectivity.activeNetwork ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return withTimeoutOrNull(4_000) {
                suspendCancellableCoroutine { continuation ->
                    val cancellation = CancellationSignal()
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    val callback = object : DnsResolver.Callback<ByteArray> {
                        override fun onAnswer(answer: ByteArray, rcode: Int) {
                            // Android's resolver can assign its own transaction ID upstream.
                            if (answer.size >= 12) {
                                answer[0] = query[0]
                                answer[1] = query[1]
                                continuation.resume(answer)
                            } else {
                                continuation.resume(null)
                            }
                        }

                        override fun onError(error: DnsResolver.DnsException) {
                            continuation.resume(null)
                        }
                    }
                    try {
                        DnsResolver.getInstance().rawQuery(
                            network, query, DnsResolver.FLAG_EMPTY,
                            { task -> task.run() }, cancellation, callback
                        )
                    } catch (_: RuntimeException) {
                        continuation.resume(null)
                    }
                }
            }
        }

        val servers = connectivity.getLinkProperties(network)?.dnsServers.orEmpty()
        for (server in servers.take(2)) {
            val answer = runCatching {
                DatagramSocket().use { socket ->
                    if (!protect(socket)) return@use null
                    network.bindSocket(socket)
                    socket.connect(InetSocketAddress(server, 53))
                    socket.soTimeout = 1_500
                    socket.send(DatagramPacket(query, query.size))
                    val response = DatagramPacket(ByteArray(4096), 4096)
                    socket.receive(response)
                    response.data.copyOf(response.length).takeIf {
                        it.size >= 12 && it[0] == query[0] && it[1] == query[1]
                    }
                }
            }.getOrNull()
            if (answer != null) return answer
        }
        return null
    }

    private fun buildUdpIpv4Reply(request: ByteArray, ihl: Int, dnsPayload: ByteArray): ByteArray {
        val udpLen = 8 + dnsPayload.size
        val totalLen = 20 + udpLen
        val out = ByteBuffer.allocate(totalLen)

        // IPv4 Header (20 bytes)
        out.put(0x45.toByte()) // Version 4, IHL 5
        out.put(0x00.toByte())
        out.putShort(totalLen.toShort())
        out.putShort(0) // ID
        out.putShort(0x4000.toShort()) // Don't Fragment
        out.put(64.toByte()) // TTL
        out.put(17.toByte()) // UDP
        out.putShort(0) // Checksum placeholder
        out.put(request, 16, 4) // Src = original Dst
        out.put(request, 12, 4) // Dst = original Src

        // UDP Header (8 bytes)
        out.put(request, ihl + 2, 2) // Src Port = 53
        out.put(request, ihl, 2)     // Dst Port = original Src Port
        out.putShort(udpLen.toShort())
        out.putShort(0) // Optional UDP checksum for IPv4
        out.put(dnsPayload)

        val bytes = out.array()
        val checksum = ipv4Checksum(bytes, 20)
        bytes[10] = (checksum ushr 8).toByte()
        bytes[11] = (checksum and 0xFF).toByte()
        return bytes
    }

    private fun ipv4Checksum(buf: ByteArray, length: Int): Int {
        var sum = 0
        var i = 0
        while (i < length) {
            val word = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun stopVpn() {
        expiryJob?.cancel()
        expiryJob = null
        loopJob?.cancel()
        loopJob = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.lakshyakumar.flmw.VPN_START"
        const val ACTION_STOP = "com.lakshyakumar.flmw.VPN_STOP"
        private const val NOTIF_ID = 4101
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val VPN_DNS = "10.111.222.2"

        fun start(context: Context) {
            val intent = Intent(context, FlmwVpnService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FlmwVpnService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
