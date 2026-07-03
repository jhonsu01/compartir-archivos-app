package io.compartirarchivos.shared.net

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Subredes IPv4 activas (WiFi + Ethernet + ...) en JVM.
 * Usa java.net.NetworkInterface.getInterfaceAddresses() para obtener IP + prefijo.
 */
actual fun localSubnets(): List<Subnet> {
    return try {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface ->
                iface.interfaceAddresses
                    .filter { it.address is Inet4Address }
                    .map { Subnet(it.address.hostAddress, it.networkPrefixLength) }
            }
            .distinctBy { it.hostAddress }
    } catch (_: Throwable) {
        emptyList()
    }
}
