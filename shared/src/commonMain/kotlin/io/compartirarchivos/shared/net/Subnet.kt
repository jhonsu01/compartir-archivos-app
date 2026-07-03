package io.compartirarchivos.shared.net

/**
 * Representa una subred IPv4 local (una interfaz de red activa).
 *
 * [hostAddress] es la IP de esta máquina en esa subred; [prefixLength] es la
 * máscara en notación CIDR (p.ej. 24 para 255.255.255.0). Con ambos se puede
 * recorrer toda la subred y descubrir otros dispositivos CompartirArchivos.
 */
data class Subnet(
    val hostAddress: String,
    val prefixLength: Short,
)

/**
 * Devuelve las subredes IPv4 activas de todas las interfaces de red UP
 * (WiFi, Ethernet, etc.). Implementación expect/actual (JVM en ambos targets).
 *
 * Excluye loopback, interfaces "down" y direcciones no IPv4.
 */
expect fun localSubnets(): List<Subnet>
