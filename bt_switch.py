from bluedot.btcomm import BluetoothServer
import RPi.GPIO as GPIO

SIGN = 3

GPIO.setmode(GPIO.BCM) #Configuracion del modo de los pines
GPIO.setwarnings(False) #Desactivar las advertencias del uso de los pines
GPIO.setup(SIGN, GPIO.OUT, initial=GPIO.LOW) #Configuracion del pin de salida en activo bajo

def leer(value):
    if value == "true":
        dato = True
        GPIO.output(SIGN, GPIO.HIGH) #Pin en activo bajo (0v)
    elif value == "false":
        dato = False
        GPIO.output(SIGN, GPIO.LOW) #Pin en activo alto (3.3v)
    else:
        print("Valor no reconocido")
    
    print(dato)

print("Iniciando servidor Bluetooth")
s = BluetoothServer(leer)