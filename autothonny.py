import time
import subprocess

#Ejecutar Thonny
subprocess.Popen(['thonny', '/home/switch_moto/bt_switch.py'])

#Esperar a que thonny inicie
time.sleep(3)

#Presionar F5 para que se ejecute el script
subprocess.run(['xdotool', 'search', '--name', 'Thonny', 'key', 'F5'])