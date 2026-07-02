# Banco Mi Ahorrito - Simulaci\u00f3n simple en consola

class Cliente:
    def __init__(self, nombre, numero_cuenta, saldo_inicial=0):
        self.nombre = nombre
        self.numero_cuenta = numero_cuenta
        self.saldo = saldo_inicial
        self.tasa_interes = 0.01  # 1% mensual

def crear_cuenta(clientes):
    print("Crear cuenta de ahorros")
    nombre = input("Nombre del cliente: ")
    numero = input("N\u00famero de cuenta: ")
    saldo = float(input("Saldo inicial (0 si no hay): ") or 0)
    cliente = Cliente(nombre, numero, saldo)
    print(f"Cuenta creada con \xe9xito. Bienvenido, {cliente.nombre}.")
    clientes.append(cliente)
    return cliente

def depositar(clientes):
    cuenta = input("N\u00famero de cuenta: ")
    for cliente in clientes:
        if cliente.numero_cuenta == cuenta:
            monto = float(input("Monto a depositar: "))
            cliente.saldo += monto
            print(f"Dep\u00f3sito exitoso. Nuevo saldo: {cliente.saldo:.2f}")
            return
    print("Cuenta no encontrada.")

def retirar(clientes):
    cuenta = input("N\u00famero de cuenta: ")
    for cliente in clientes:
        if cliente.numero_cuenta == cuenta:
            monto = float(input("Monto a retirar: "))
            if monto > cliente.saldo:
                print("Fondos insuficientes.")
            else:
                cliente.saldo -= monto
                print(f"Retiro exitoso. Nuevo saldo: {cliente.saldo:.2f}")
            return
    print("Cuenta no encontrada.")

def generar_rendimientos(clientes):
    for cliente in clientes:
        interes = cliente.saldo * cliente.tasa_interes
        cliente.saldo += interes
        print(f"Rendimiento generado: +{interes:.2f} a la cuenta {cliente.numero_cuenta}. Nuevo saldo: {cliente.saldo:.2f}")

def menu():
    print("\n=== Banco Mi Ahorrito ===")
    print("1. Crear cuenta de ahorros")
    print("2. Depositar")
    print("3. Retirar")
    print("4. Generar rendimientos")
    print("5. Salir")

def main():
    clientes = []
    while True:
        menu()
        opcion = input("Seleccione una opci\u00f3n: ")
        if opcion == "1":
            crear_cuenta(clientes)
        elif opcion == "2":
            depositar(clientes)
        elif opcion == "3":
            retirar(clientes)
        elif opcion == "4":
            generar_rendimientos(clientes)
        elif opcion == "5":
            print("Gracias por usar Banco Mi Ahorrito. ¡Hasta luego!")
            break
        else:
            print("Opción no v\u00e1lida. Intente de nuevo.")

if __name__ == "__main__":
    main()