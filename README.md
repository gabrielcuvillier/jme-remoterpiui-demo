jme-remoterpiui-demo
====================
RemoteRPIUIControllerDemo MIDlet

This program allows to remotely control the RPIUIDemoMIDlet application (jme-rpiui-demo repository) running on Raspberry Pi from the JME Emulator. The program connects to the server socket of RPIUIDemoMIDlet, and send the button numbers activated on the Emulator over the socket output stream. The remote application will receive these button numbers, and handle them as local RPIUI board button pushes.

Target platform is Emulator. The 3 default GPIOPin for input buttons are used: (0,0) (2,13) (6,15). Each button is accessible through the Emulator "External Events Generator" (in "Tools" sub-menu).

Note: The RPIUIDemoMIDlet application must be started on the Raspberry Pi before running this program. Connection parameters are set using MIDlet attributes "Address" and "Port" (with default parameters "raspberrypi.local" and "19054").
