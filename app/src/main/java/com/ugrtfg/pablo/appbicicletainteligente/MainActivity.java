package com.ugrtfg.pablo.appbicicletainteligente;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends Activity implements View.OnClickListener{

    // Variables para la interacción con la interfaz gráfica
    private TextView textoEstado, textoVelocidad, textoPendiente, textoTemperatura;
    private Button botonInicio, botonModo1, botonModo2, botonSalir;
    private Chronometer contador;
    // Variables para el tratamiento de la conexión Bluetooth
    private BluetoothDevice dispositivo;
    private BluetoothSocket socketBT;
    private BluetoothAdapter bAdapter;
    private ArrayList<BluetoothDevice> listaDispositivos;
    private final String DEVICE_ADDRESS = "20:15:02:03:05:26";
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); // ID del servicio de puerto Serial
    // Variables de control de mensajes
    private OutputStream out;
    private InputStream in;
    byte buffer[];
    boolean stopThread;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listaDispositivos = new ArrayList<BluetoothDevice>();
        registrarElementos();
        configurarAdaptadorBluetooth();
        registrarEventosBluetooth();
    }


    /*
    *
    * Serie de funciones que inicializan el programa
    *
     */

    private void registrarElementos(){

        botonInicio = (Button)findViewById(R.id.botonInicio);
        botonInicio.setOnClickListener(this);
        botonInicio.setEnabled(false);

        botonSalir = (Button)findViewById(R.id.botonSalir);
        botonSalir.setOnClickListener(this);
        botonSalir.setEnabled(false);

        botonModo1 = (Button)findViewById(R.id.botonModo1);
        botonModo1.setOnClickListener(this);
        botonModo1.setEnabled(false);

        botonModo2 = (Button)findViewById(R.id.botonModo2);
        botonModo2.setOnClickListener(this);
        botonModo2.setEnabled(false);

        textoEstado = (TextView)findViewById(R.id.textoEstado);
        textoPendiente = (TextView)findViewById(R.id.textoPendiente);
        textoTemperatura = (TextView)findViewById(R.id.textoTemperatura);
        textoVelocidad = (TextView)findViewById(R.id.textoVelocidad);

        contador = (Chronometer)findViewById(R.id.tiempoAplicacion);
    }

    private void configurarAdaptadorBluetooth(){
        bAdapter = BluetoothAdapter.getDefaultAdapter();

        if(bAdapter == null) {
            textoEstado.setText("El dispositivo no soporta Bluetooth. No se puede utilizar esta aplicación");
            return;
        }
        if(!bAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 0);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            textoEstado.setText("Bluetooth Conectado");
            botonInicio.setEnabled(true);
        }
        else {
            botonInicio.setEnabled(true);
            textoEstado.setText("Bluetooth Conectado");
        }
        // Si había alguna búsqueda Bluetooth la cerramos
        if (bAdapter.isDiscovering()){
            bAdapter.cancelDiscovery();
        }
        // Volvemos con la búsqueda
        bAdapter.startDiscovery();
    }

    private void registrarEventosBluetooth() {
        // Registramos el BroadcastReceiver que instanciamos previamente para detectar los distintos eventos que queremos recibir
        IntentFilter filtro = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filtro.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(bReceiver, filtro);
    }

    /**
     * Handler que capta un nuevo dispositivo Bluetooth y lo añade a la lista
     */
    private final BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            // Cada vez que se descubra un nuevo dispositivo por Bluetooth
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                if(listaDispositivos == null) {
                    listaDispositivos = new ArrayList<>();
                }
                BluetoothDevice dispositivo = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                listaDispositivos.add(dispositivo);
            }
        }
    };

    /**
     * Handler para manejar los eventos onClick de los botones.
     */
    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.botonInicio) {
            if (buscarDispositivos()) {
                if (conectarDispositivo()) {
                    textoEstado.setText("Conexión realizada con éxito");
                    botonSalir.setEnabled(true);
                    botonModo1.setEnabled(true);
                    botonModo2.setEnabled(true);
                    escucharMensaje();
                } else {
                    textoEstado.setText("Problema al conectarse con el dispositivo");
                }
            } else {
                textoEstado.setText("No se encuentra el dispositivo de la bicicleta");
            }
        }
        else if(v.getId() == R.id.botonSalir) {
            enviarMensaje("s%");
            contador.stop();
            finish();
        }
        else if(v.getId() == R.id.botonModo1){
            enviarMensaje("1%");
            escucharMensaje();
        }
        else if(v.getId() == R.id.botonModo2){
            enviarMensaje("2%");
            escucharMensaje();
        }
    }

    /*
    *
    * Funciones que realizan la búsqueda del dispositivo bluetooth deseado y la conexión con el mismo
    *
     */

    private boolean buscarDispositivos(){
        boolean encontrado = false;
        Set<BluetoothDevice> dispositivosAsociados = bAdapter.getBondedDevices();
        if (dispositivosAsociados.size() > 0) {
            for (BluetoothDevice device : dispositivosAsociados) {
                listaDispositivos.add(device);
            }
        }
        for (BluetoothDevice iterator : listaDispositivos) {
            if(iterator.getAddress().equals(DEVICE_ADDRESS)) {
                dispositivo = iterator;
                encontrado = true;
                bAdapter.cancelDiscovery();
                break;
            }
        }
        return encontrado;
    }

    private boolean conectarDispositivo(){
        boolean conectado = true;
        try {
            socketBT = dispositivo.createRfcommSocketToServiceRecord(PORT_UUID);
            socketBT.connect();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                // Intento de fallback (se realizará con versiones de Android >= 4.2)
                socketBT =(BluetoothSocket) dispositivo.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(dispositivo,1);
                socketBT.connect();
            } catch (Exception e2) {
                e.printStackTrace();
                conectado = false;
            }
        }
        if(conectado) {
            try {
                out = socketBT.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                in = socketBT.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return conectado;
    }

    /*
    *
    * Funciones para la captación y el tratamiento de los mensajes en la comunicación bluetooth
    *
     */

    private void enviarMensaje(String m){
        try{
            out.write(m.getBytes());
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    private void tratamientoRespuesta(String respuesta){
        String respuestas[] = respuesta.split(",");
        switch (respuestas[0]) {
            case "D": {
                textoPendiente.setText(respuestas[1]);
                textoTemperatura.setText(respuestas[2]);
                textoVelocidad.setText(respuestas[3]);
            }
            default: {
                textoEstado.setText("Error: " + respuesta);
            }
        }
    }

    void escucharMensaje() {
        final Handler handler = new Handler();
        stopThread = false;
        buffer = new byte[1024];
        Thread thread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopThread) {
                    try {
                        int byteCount = in.available();
                        if (byteCount > 0) {
                            byte[] rawBytes = new byte[byteCount];
                            in.read(rawBytes);
                            final String respuesta = new String(rawBytes, "UTF-8");
                            handler.post(new Runnable() {
                                public void run() {
                                    tratamientoRespuesta(respuesta);
                                }
                            });
                        }
                    } catch (IOException ex) {
                        stopThread = true;
                    }
                }
            }
        });
        thread.start();
    }
}
