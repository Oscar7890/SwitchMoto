package com.example.switchmoto;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import android.Manifest;


public class MainActivity extends AppCompatActivity {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION = 15 * 60 * 1000; // 15 minutos en milisegundos
    private int failedAttempts = 0;
    private Handler handler = new Handler();
    private Runnable enableButtonRunnable;
    private BluetoothAdapter adaptadorBluetooth;
    // UUID para el servicio SPP (Serial Port Profile)
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private BluetoothDevice selectedDevice;
    Button botonEnlazarMoto, botonAbrirSwitch; //Botones de la aplicacion
    private int selectedIndex = -1;
    private HashMap<String, String> deviceMap;
    private boolean isConnected = false;
    private String value = "true";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Verificar los permisos de bluetooth en tiempo de ejecucion
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            }
        }


        /*------------------------------------------------------------------------------------------------------*/
        // Bluetooth

        // Inicializa el adaptador Bluetooth
        adaptadorBluetooth = BluetoothAdapter.getDefaultAdapter();

        // Verifica si el dispositivo soporta Bluetooth
        if (adaptadorBluetooth == null) {
            // El dispositivo no soporta Bluetooth
            Toast.makeText(this, "Este dispositivo no soporta Bluetooth", Toast.LENGTH_SHORT).show();
        } else {
            // Si el Bluetooth no está encendido, mostrar un diálogo para sugerir al usuario que lo encienda
            if (!adaptadorBluetooth.isEnabled()) {
                mensajeActivacionBluetooth();
            } else {
                // El Bluetooth ya está encendido
                Toast.makeText(this, "Bluetooth ya está encendido", Toast.LENGTH_SHORT).show();
            }
        }


        botonEnlazarMoto = findViewById(R.id.botonEnlazarMoto); //Encontrar boton por su ID

        botonEnlazarMoto.setOnClickListener(v -> {
            if (isConnected) {
                desconectarDispositivo();
            } else {
                mostrarDispositivosVinculados();
            }
        }); //Asignar el evento OnClickListener al boton

        /*------------------------------------------------------------------------------------------------------*/
        // Huella dactilar

        botonAbrirSwitch = findViewById(R.id.botonAbrirSwitch); // Encontrar boton por su ID

        enableButtonRunnable = new Runnable() {
            @Override
            public void run() {
                enableButton();
            }
        };

        botonAbrirSwitch.setOnClickListener(v -> showBiometricPrompt()); // Asignar el evento OnClickListener al boton

    }

    private void mensajeActivacionBluetooth() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Para utilizar esta aplicación, es necesario activar el Bluetooth.")
                .setPositiveButton("Activar Bluetooth", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Intentar abrir la configuración de Bluetooth
                        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // El usuario ha cancelado, puedes manejarlo según tus necesidades
                        Toast.makeText(MainActivity.this, "La aplicación requiere Bluetooth",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setCancelable(false)
                .show();
    }

    @SuppressLint("MissingPermission")
    private void mostrarDispositivosVinculados() {
        Set<BluetoothDevice> dispositivosVinculados = adaptadorBluetooth.getBondedDevices();
        if (dispositivosVinculados.size() > 0) {
            ArrayList<String> listaNombresDispositivos = new ArrayList<>();
            deviceMap = new HashMap<>();
            for (BluetoothDevice device : dispositivosVinculados) {
                listaNombresDispositivos.add(device.getName());
                deviceMap.put(device.getName(), device.getAddress());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_singlechoice, listaNombresDispositivos);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Selecciona un dispositivo")
                    .setSingleChoiceItems(adapter, selectedIndex, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            selectedIndex = which;
                        }
                    })
                    .setPositiveButton("Conectar", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (selectedIndex != -1) {
                                String dispositivoSeleccionado = listaNombresDispositivos.get(selectedIndex);
                                String direccionMAC = deviceMap.get(dispositivoSeleccionado);
                                selectedDevice = adaptadorBluetooth.getRemoteDevice(direccionMAC);
                                conectarAlDispositivo(selectedDevice);
                            }
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        } else {
            Toast.makeText(this, "No hay dispositivos Bluetooth vinculados", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void conectarAlDispositivo(BluetoothDevice device) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    socket.connect();
                    outputStream = socket.getOutputStream();
                    isConnected = true;
                    showToast("Conexión exitosa");
                } catch (IOException e) {
                    e.printStackTrace();
                    showToast("Error al conectar");
                }
            }
        }).start();
    }

    private void desconectarDispositivo() {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
            isConnected = false;
            showToast("Dispositivo desconectado");
        } catch (IOException e) {
            e.printStackTrace();
            showToast("Error al desconectar");
        }
    }

    private void sendData(String data) {
        if (outputStream != null) {
            try {
                outputStream.write(data.getBytes());
                showToast("Datos enviados");
            } catch (IOException e) {
                e.printStackTrace();
                showToast("Error al enviar datos");
            }
        }
    }

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showBiometricPrompt() {
        BiometricManager biometricManager = BiometricManager.from(this);
        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                // El dispositivo puede realizar autenticación biométrica
                Toast.makeText(this, "Autenticacion biometrica disponible",
                        Toast.LENGTH_SHORT).show();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                // No hay hardware biométrico
                Toast.makeText(this, "No se cuenta con autenticacion biometrica",
                        Toast.LENGTH_SHORT).show();
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                // El hardware biométrico no está disponible
                Toast.makeText(this, "Autenticacion biomnetrica NO disponible",
                        Toast.LENGTH_SHORT).show();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                // No hay datos biométricos registrados
                Toast.makeText(this, "No se ha registrado ninguna autenticacion biometrica",
                        Toast.LENGTH_SHORT).show();
                break;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // Manejar error de autenticación
                Toast.makeText(getApplicationContext(), "Error de autenticación: " + errString,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                // Autenticación exitosa

                //Valor a enviar al dispositivo externo
                if(value.equals("true")){
                    //Enviar el valor true por Bluetooth
                    sendData(value);
                    value = "false";
                }else{
                    //Enviar el valor false por Bluetooth
                    sendData(value);
                    value = "true";
                }

                // Mostrar un mensaje al usuario indicando que la autenticación fue exitosa
                Toast.makeText(getApplicationContext(), "Autenticación exitosa ",
                        Toast.LENGTH_SHORT).show();

                // Restablecer el contador de intentos fallidos
                failedAttempts = 0;

            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                // Autenticación fallida

                // Mostrar un mensaje al usuario indicando que la autenticación falló
                Toast.makeText(getApplicationContext(),
                        "Autenticación fallida. Inténtalo de nuevo.", Toast.LENGTH_SHORT).show();

                failedAttempts++; //Aumentar el contador de intentos fallidos

                //Deshabilitar el boton en caso de que se acaben los intentos
                if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                    disableButton();
                    handler.postDelayed(enableButtonRunnable, LOCKOUT_DURATION);
                }
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Autenticación biométrica")
                .setSubtitle("Abre el switch con tu huella dactilar")
                .setNegativeButtonText("Cancelar")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void disableButton() {
        botonAbrirSwitch.setEnabled(false);
        Toast.makeText(getApplicationContext(), "Demasiados intentos fallidos. Intenta nuevamente en 15 minutos.", Toast.LENGTH_LONG).show();
    }

    private void enableButton() {
        botonAbrirSwitch.setEnabled(true);
        failedAttempts = 0; // Reiniciar el contador de intentos fallidos
        Toast.makeText(getApplicationContext(), "Ahora puedes intentar nuevamente.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        try {
            if(outputStream != null){
                outputStream.close();
            }
            if(socket != null){
                socket.close();
            }
        } catch(IOException e){
            e.printStackTrace();
        }
    }

}

