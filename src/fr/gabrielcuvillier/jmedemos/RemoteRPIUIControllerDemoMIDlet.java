/**
 * RemoteRPIUIControllerDemoMIDlet.java 
 * This file is part of RemoteRPIUIControllerDemo
 * Copyright 2014 Gabriel Cuvillier
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.gabrielcuvillier.jmedemos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import javax.microedition.midlet.MIDlet;
import jdk.dio.DeviceManager;
import jdk.dio.gpio.GPIOPin;
import jdk.dio.gpio.GPIOPinConfig;
import jdk.dio.gpio.PinEvent;
import jdk.dio.gpio.PinListener;

/**
 * RemoteRPIUIControllerDemo MIDlet. <p>
 * 
 * This program allows to remotely control the RPIUIDemoMIDlet application running on 
 * Raspberry Pi from the JME Emulator. The program connects to the server socket 
 * of RPIUIDemoMIDlet, and send the button numbers activated on the Emulator over the socket 
 * output stream. The remote application will receive these button numbers, and handle 
 * them as local RPIUI board button pushes. <p>
 * 
 * Target platform is Emulator. The 3 default GPIOPin for input buttons are used: 
 * (0,0) (2,13) (6,15). Each button is accessible through the Emulator "External 
 * Events Generator" (in "Tools" sub-menu). <p>
 * 
 * Note: The RPIUIDemoMIDlet application must be started on the Raspberry Pi before 
 * running this program. Connection parameters are set using MIDlet attributes "Address" 
 * and "Port" (with default parameters "raspberrypi.local" and "19054"). <p>
 * 
 * See class source code for description of internal implementation. <p>
 * 
 * @author Gabriel Cuvillier
 */
public class RemoteRPIUIControllerDemoMIDlet extends MIDlet {

    /** Default connection parameters
      * Used if the MIDLet attribute "Address" or "Port" are not present */
    private static final int DEFAULT_PORT = 19054;
    private static final String DEFAULT_ADDRESS = "raspberrypi.local"; 
    // Note: this is default hostname of RPi with Zeroconf
    
    /** MIDlet attribute name to define RPIUIDemo host address */
    private static final String ADDRESS_ATTRIBUTE = "Address";
    /** MIDlet attribute name to define RPIUIDemo port */
    private static final String PORT_ATTRIBUTE = "Port";

    /** IP Address and port of running RPIUIDemo application */
    private String _RPIUIAddress;
    private int _RPIUIPort;

    /** Socket connection and input/output Streams */
    private SocketConnection _Connection;
    private OutputStream _OutputStream;
    private InputStream _InputStream;
    
    /** Thread to monitor connection status (read data on InputStream) */
    private Thread _ConnectionThread;

    /** The 3 GPIOPins corresponding to the 3 Emulator buttons
      * (use "External Events Generator" of the Emulator to change their values) */
    private GPIOPin _Button1;
    private GPIOPin _Button2;
    private GPIOPin _Button3;

    /** Start of application */
    @Override
    public void startApp() {
        System.out.println("Starting RemoteRPIUIcontrollerDemo application");

        // Get IP address from "Address" MIDlet attribute
        _RPIUIAddress = getAppProperty(ADDRESS_ATTRIBUTE);
        if (_RPIUIAddress == null || _RPIUIAddress.equals("")) {
            // In case of error or empty address, use the default address
            System.out.println("Note: using default address");
            _RPIUIAddress = DEFAULT_ADDRESS;
        }

        // Get Port from "Port" MIDlet attribute
        try {
            _RPIUIPort = Integer.parseInt(getAppProperty(PORT_ATTRIBUTE));
        } catch (NumberFormatException nfe) {
            // In case of parsing error, use the default port
            System.out.println("Note: using default Port");
            _RPIUIPort = DEFAULT_PORT;
        }

        try {
            // Connect to remote RPIUIDemo application
            _Connection = (SocketConnection) Connector.open("socket://" +
                                                            _RPIUIAddress + 
                                                            ":" + _RPIUIPort);
            System.out.println("Connected to RPIUIDemo on " + 
                                _RPIUIAddress + " port " + _RPIUIPort);
            // And open input and output streams
            _OutputStream = _Connection.openOutputStream();
            _InputStream = _Connection.openInputStream();
        } catch (IOException ex) {
            // In case of connnection error, stop application
            throw new RuntimeException("Unable to connect to RPIUIDemo instance: " + ex.getMessage());
        }

        try {
            System.out.println("Creating GPIOPin for Buttons 1, 2 and 3");
            
            // Open the GPIO pins for the 3 input buttons: (0,0),(2,13),(6,15)
            _Button1 = (GPIOPin) DeviceManager.open(
                    new GPIOPinConfig.Builder()
                            .setControllerNumber(0)
                            .setPinNumber(0)
                            .setDirection(GPIOPinConfig.DIR_INPUT_ONLY)
                            .setDriveMode(GPIOPinConfig.MODE_INPUT_PULL_DOWN)
                            .setTrigger(GPIOPinConfig.TRIGGER_BOTH_EDGES)
                            .setInitValue(false).build());
            
            _Button2 = (GPIOPin) DeviceManager.open(
                    new GPIOPinConfig.Builder()
                            .setControllerNumber(2)
                            .setPinNumber(13)
                            .setDirection(GPIOPinConfig.DIR_INPUT_ONLY)
                            .setDriveMode(GPIOPinConfig.MODE_INPUT_PULL_DOWN)
                            .setTrigger(GPIOPinConfig.TRIGGER_BOTH_EDGES)
                            .setInitValue(false).build());
            
            _Button3 = (GPIOPin) DeviceManager.open(
                    new GPIOPinConfig.Builder()
                            .setControllerNumber(6)
                            .setPinNumber(15)
                            .setDirection(GPIOPinConfig.DIR_INPUT_ONLY)
                            .setDriveMode(GPIOPinConfig.MODE_INPUT_PULL_DOWN)
                            .setTrigger(GPIOPinConfig.TRIGGER_BOTH_EDGES)
                            .setInitValue(false).build());

            // Register pin input listeners for each button
            _Button1.setInputListener(
                    new SendButtonNumberListenerImpl(1, _OutputStream));
            
            _Button2.setInputListener(
                    new SendButtonNumberListenerImpl(2, _OutputStream));
            
            _Button3.setInputListener(
                    new SendButtonNumberListenerImpl(3, _OutputStream));
            
        } catch (IOException ex) {
            throw new RuntimeException("Unable to open GPIO pins for buttons");
        }

        // Finally, create the thread that will monitor connection status
        _ConnectionThread = new Thread(
                new ConnectionMonitorRunnableImpl(this, _InputStream));
        _ConnectionThread.start();
    }

    /** End of application
     * @param unconditional */
    @Override
    public void destroyApp(boolean unconditional) {
        System.out.println("Stopping RemoteRPIUIControllerDemo application");
        
        // Close every opened resource: Input/Output Streams, Socket Connection, 
        // and Button GPIOPins

        try {
            if (_OutputStream != null) {
                _OutputStream.close();
                _OutputStream = null;
            }

            if (_InputStream != null) {
                _InputStream.close();
                _InputStream = null;
            }

            if (_Connection != null) {
                _Connection.close();
                _Connection = null;
            }

            if (_Button1 != null) {
                _Button1.close();
                _Button1 = null;
            }

            if (_Button2 != null) {
                _Button2.close();
                _Button2 = null;
            }

            if (_Button3 != null) {
                _Button3.close();
                _Button3 = null;
            }
            
            _ConnectionThread = null;
        } catch (IOException ex) {
            // Nothing to do in case of IO Exception
        }
    }

    /** Pin Listener implementation.
     The valueChanged method will send the associated button number to the output stream*/
    private static class SendButtonNumberListenerImpl implements PinListener {

        /** Button number */
        private final int _ButtonNumber;
        /** Output stream */
        private final OutputStream _Out;

        public SendButtonNumberListenerImpl(int button, OutputStream outputStream) {
            _ButtonNumber = button;
            _Out = outputStream;
        }

        @Override
        public void valueChanged(PinEvent evt) {
            System.out.format("Button %d pushed\n", _ButtonNumber);
            // When pin value is changed, send the button number to the stream
            try {
                _Out.write(_ButtonNumber);
            } catch (IOException ex) {
                System.out.println("IO Exception catched while sending data");
            }
        }
    }

    /** Connection Monitor implementation. 
     It will stop MIDLet if input stream is being closed.*/
    private static class ConnectionMonitorRunnableImpl implements Runnable {

        /** reference to Application (to be able to stop it) */
        private final RemoteRPIUIControllerDemoMIDlet _App;
        /** Input stream of the connection */
        private final InputStream _In; 
        
        public ConnectionMonitorRunnableImpl(RemoteRPIUIControllerDemoMIDlet app, 
                                             InputStream in) {
            _In = in;
            _App = app;
        }

        @Override
        public void run() {
            // We read from input stream: if "-1" is read, it means that the 
            // stream have been closed and so we stop application
            int c = 0;
            try {
                while (c != -1) {
                    c = _In.read(); // This is a blocking call
                }
            } catch (IOException ex) {
                System.out.println("IO Exception catched while monitoring the connection");
            } finally {
                System.out.println("Stream have been closed");
                // Stop application when end of stream is reached or an IO
                // Exception is thrown
                _App.destroyApp(true);
                _App.notifyDestroyed();
            }
        }
    }
}
